# Backtest Phase 2: Run all extracted game prompts through our solver logic
# and compare against the known correct answers.

$ErrorActionPreference = "SilentlyContinue"

$csvPath = "C:\Users\user\.gemini\antigravity\scratch\meteor-chat-games\backtest_data.csv"
$wordsPath = "C:\Users\user\.gemini\antigravity\scratch\meteor-chat-games\src\main\resources\words.txt"

if (!(Test-Path $csvPath)) {
    Write-Host "ERROR: backtest_data.csv not found. Run backtest_extract.ps1 first." -ForegroundColor Red
    exit 1
}

$games = Import-Csv $csvPath
$words = Get-Content $wordsPath | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -ge 1 }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BACKTEST: Solver Accuracy Report" -ForegroundColor Cyan
Write-Host "  $($games.Count) game events loaded" -ForegroundColor Cyan
Write-Host "  $($words.Count) dictionary words loaded" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# ==================== Solver Functions (mirroring Java logic) ====================

function Solve-Math {
    param([string]$expr)
    if (!$expr) { return $null }
    $clean = $expr -replace '[^0-9+\-*/\s]', '' | ForEach-Object { $_.Trim() }
    if (!$clean) { return $null }
    try {
        # Use PowerShell's expression evaluator
        $result = Invoke-Expression $clean
        return [string][math]::Floor($result)
    } catch { return $null }
}

function Solve-Reverse {
    param([string]$word)
    if (!$word) { return $null }
    $chars = $word.ToCharArray()
    [array]::Reverse($chars)
    return -join $chars
}

function Solve-Write {
    param([string]$word)
    return $word
}

function Solve-Sort {
    param([string]$scrambled)
    if (!$scrambled) { return $null }
    $targetSorted = ($scrambled.ToLower().ToCharArray() | Sort-Object) -join ''
    foreach ($w in $words) {
        if ($w.Length -eq $scrambled.Length) {
            $wSorted = ($w.ToLower().ToCharArray() | Sort-Object) -join ''
            if ($wSorted -eq $targetSorted) {
                return $w
            }
        }
    }
    return $null
}

function Solve-Fill {
    param([string]$pattern)
    if (!$pattern -or $pattern -notmatch '_') { return $null }
    $patLower = $pattern.ToLower()
    foreach ($w in $words) {
        if ($w.Length -eq $patLower.Length) {
            $match = $true
            for ($i = 0; $i -lt $patLower.Length; $i++) {
                $pc = $patLower[$i]
                if ($pc -ne '_' -and $pc -ne $w.ToLower()[$i]) {
                    $match = $false
                    break
                }
            }
            if ($match) { return $w }
        }
    }
    return $null
}

# ==================== Regex patterns (mirroring Java) ====================
$SORT_REGEX    = [regex]'(?i)(?:sort|unscramble)\s+(?:the\s+)?(?:word)?\s*"([^"]+)"'
$REVERSE_REGEX = [regex]'(?i)(?:type|write|reverse)\s+(?:the\s+)?(?:word)?\s*"([^"]+)"\s+backwards|(?:reverse)\s+(?:the\s+)?(?:word)?\s*"([^"]+)"'
$MATH_REGEX    = [regex]'"(\d+\s*[+\-*/xX]\s*\d+)"'
$WRITE_REGEX   = [regex]'(?i)(?:to\s+)?(?:type|write)\s+"([^"]+)"'
$FILL_REGEX    = [regex]'(?i)[_a-zA-Z]{3,}'

# ==================== Run Backtest ====================

$results = @{}
$missingWords = @{}
$failures = @()

foreach ($type in @("MATH","WRITE","SORT","FILL","REVERSE","TRIVIA","VARIABLE","UNKNOWN")) {
    $results[$type] = @{ Total = 0; Won = 0; Expired = 0; Solved = 0; Correct = 0; Wrong = 0; Missed = 0 }
}

foreach ($game in $games) {
    $type = $game.GameType
    if (!$results.ContainsKey($type)) { $results[$type] = @{ Total = 0; Won = 0; Expired = 0; Solved = 0; Correct = 0; Wrong = 0; Missed = 0 } }
    
    $r = $results[$type]
    $r.Total++
    
    $isExpired = $game.Expired -eq "True"
    $knownAnswer = $game.Answer
    $prompt = $game.PromptLines
    
    if ($isExpired) { $r.Expired++; continue }
    if (!$knownAnswer) { continue }
    $r.Won++
    
    # Split prompt back into individual lines
    $promptParts = $prompt -split '\s*\|\s*'
    $ourAnswer = $null
    
    switch ($type) {
        "MATH" {
            foreach ($part in $promptParts) {
                $m = $MATH_REGEX.Match($part)
                if ($m.Success) {
                    $expr = $m.Groups[1].Value
                    $ourAnswer = Solve-Math $expr
                    break
                }
            }
        }
        "WRITE" {
            foreach ($part in $promptParts) {
                $m = $WRITE_REGEX.Match($part)
                if ($m.Success) {
                    $ourAnswer = $m.Groups[1].Value
                    break
                }
            }
        }
        "UNKNOWN" {
            # These are often WRITE games that didn't get classified
            foreach ($part in $promptParts) {
                $m = $WRITE_REGEX.Match($part)
                if ($m.Success) {
                    $ourAnswer = $m.Groups[1].Value
                    break
                }
            }
        }
        "SORT" {
            foreach ($part in $promptParts) {
                $m = $SORT_REGEX.Match($part)
                if ($m.Success) {
                    $scrambled = $m.Groups[1].Value
                    $ourAnswer = Solve-Sort $scrambled
                    break
                }
            }
        }
        "REVERSE" {
            foreach ($part in $promptParts) {
                $m = $REVERSE_REGEX.Match($part)
                if ($m.Success) {
                    $word = if ($m.Groups[1].Value) { $m.Groups[1].Value } else { $m.Groups[2].Value }
                    $ourAnswer = Solve-Reverse $word
                    break
                }
            }
        }
        "FILL" {
            foreach ($part in $promptParts) {
                # Look for the fill pattern line (starts with special char then underscore pattern)
                if ($part -match '([a-zA-Z_]{3,})' -and $part -match '_') {
                    $tokens = $part -split '\s+'
                    foreach ($tok in $tokens) {
                        $cleanTok = $tok -replace '[^a-zA-Z_]', ''
                        if ($cleanTok -match '_' -and $cleanTok.Length -ge 3) {
                            $ourAnswer = Solve-Fill $cleanTok
                            if ($ourAnswer) { break }
                        }
                    }
                    if ($ourAnswer) { break }
                }
            }
        }
        "TRIVIA" {
            # We'd need the trivia DB loaded - skip for now, just mark as missed
            $ourAnswer = $null
        }
        "VARIABLE" {
            # Variable needs multi-line equations - skip for now
            $ourAnswer = $null
        }
    }
    
    if ($ourAnswer) {
        $r.Solved++
        if ($ourAnswer.Trim().ToLower() -eq $knownAnswer.Trim().ToLower()) {
            $r.Correct++
        } else {
            $r.Wrong++
            $failures += [PSCustomObject]@{
                Type = $type
                Prompt = ($promptParts | Select-Object -First 2) -join " | "
                Expected = $knownAnswer
                Got = $ourAnswer
            }
        }
    } else {
        $r.Missed++
        # Track missing words for SORT and FILL
        if ($type -eq "SORT" -or $type -eq "FILL") {
            if ($knownAnswer -and $knownAnswer -match '^[a-zA-Z\s]+$') {
                $missingWords[$knownAnswer] = $type
            }
        }
    }
}

# ==================== Report ====================

Write-Host "===========================================" -ForegroundColor Cyan
Write-Host "  RESULTS BY GAME TYPE" -ForegroundColor Cyan
Write-Host "===========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host ("{0,-12} {1,6} {2,6} {3,6} {4,8} {5,8} {6,8} {7,8}" -f "Type", "Total", "Won", "Exprd", "Solved", "Correct", "Wrong", "Missed") -ForegroundColor White
Write-Host ("{0,-12} {1,6} {2,6} {3,6} {4,8} {5,8} {6,8} {7,8}" -f "----", "-----", "---", "-----", "------", "-------", "-----", "------")

$totalSolved = 0; $totalCorrect = 0; $totalWrong = 0; $totalMissed = 0; $totalWon = 0

foreach ($type in @("MATH","WRITE","UNKNOWN","SORT","FILL","REVERSE","TRIVIA","VARIABLE")) {
    $r = $results[$type]
    if ($r.Total -eq 0) { continue }
    
    $pct = if ($r.Won -gt 0) { [math]::Round(($r.Correct / $r.Won) * 100, 1) } else { 0 }
    $color = if ($pct -ge 90) { "Green" } elseif ($pct -ge 50) { "Yellow" } else { "Red" }
    
    Write-Host ("{0,-12} {1,6} {2,6} {3,6} {4,8} {5,8} {6,8} {7,8}  ({8}%)" -f $type, $r.Total, $r.Won, $r.Expired, $r.Solved, $r.Correct, $r.Wrong, $r.Missed, $pct) -ForegroundColor $color
    
    $totalSolved += $r.Solved; $totalCorrect += $r.Correct; $totalWrong += $r.Wrong; $totalMissed += $r.Missed; $totalWon += $r.Won
}

Write-Host ""
$overallPct = if ($totalWon -gt 0) { [math]::Round(($totalCorrect / $totalWon) * 100, 1) } else { 0 }
Write-Host ("OVERALL: {0} won games, {1} solved, {2} correct, {3} wrong, {4} missed => {5}% accuracy" -f $totalWon, $totalSolved, $totalCorrect, $totalWrong, $totalMissed, $overallPct) -ForegroundColor Cyan

# ==================== Failures Detail ====================
if ($failures.Count -gt 0) {
    Write-Host "`n===========================================" -ForegroundColor Red
    Write-Host "  WRONG ANSWERS ($($failures.Count) total)" -ForegroundColor Red
    Write-Host "===========================================" -ForegroundColor Red
    foreach ($f in $failures | Select-Object -First 20) {
        Write-Host "  [$($f.Type)] Expected: '$($f.Expected)' | Got: '$($f.Got)'" -ForegroundColor Red
        Write-Host "    Prompt: $($f.Prompt)" -ForegroundColor DarkGray
    }
    if ($failures.Count -gt 20) { Write-Host "  ... and $($failures.Count - 20) more" -ForegroundColor DarkGray }
}

# ==================== Missing Words ====================
if ($missingWords.Count -gt 0) {
    Write-Host "`n===========================================" -ForegroundColor Yellow
    Write-Host "  MISSING DICTIONARY WORDS ($($missingWords.Count) total)" -ForegroundColor Yellow
    Write-Host "  These words need to be added to words.txt" -ForegroundColor Yellow
    Write-Host "===========================================" -ForegroundColor Yellow
    $sorted = $missingWords.GetEnumerator() | Sort-Object Name
    foreach ($w in $sorted) {
        $inDict = if ($words -contains $w.Name) { "IN DICT" } else { "MISSING" }
        Write-Host "  $($w.Name) [$($w.Value)] - $inDict" -ForegroundColor $(if ($inDict -eq "MISSING") { "Red" } else { "Green" })
    }
}
