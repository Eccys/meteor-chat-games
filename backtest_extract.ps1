# Backtest script: Extract all chat game events from all compressed logs across all instances,
# then simulate running each prompt through the solver logic and compare against the known answer.

$ErrorActionPreference = "SilentlyContinue"
$instancesDir = "C:\Users\user\AppData\Roaming\PrismLauncher\instances"

# ==================== PHASE 1: Extract all game events ====================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PHASE 1: Extracting chat game events" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$allGames = @()
$totalLogs = 0

$instances = Get-ChildItem $instancesDir -Directory
foreach ($inst in $instances) {
    $logsDir = "$($inst.FullName)\minecraft\logs"
    if (!(Test-Path $logsDir)) { continue }
    
    # Process .log.gz files
    $gzFiles = Get-ChildItem $logsDir -Filter "*.log.gz" -ErrorAction SilentlyContinue
    # Also process latest.log
    $latestLog = Join-Path $logsDir "latest.log"
    
    $logFiles = @()
    foreach ($gz in $gzFiles) { $logFiles += @{ Path = $gz.FullName; IsGz = $true } }
    if (Test-Path $latestLog) { $logFiles += @{ Path = $latestLog; IsGz = $false } }
    
    foreach ($logFile in $logFiles) {
        $totalLogs++
        try {
            if ($logFile.IsGz) {
                $fs = [System.IO.File]::OpenRead($logFile.Path)
                $gzs = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Decompress)
                $sr = New-Object System.IO.StreamReader($gzs)
                $content = $sr.ReadToEnd()
                $sr.Close(); $gzs.Close(); $fs.Close()
            } else {
                $content = [System.IO.File]::ReadAllText($logFile.Path, [System.Text.Encoding]::UTF8)
            }
        } catch { continue }
        
        if (!$content) { continue }
        $lines = $content -split "`n"
        
        # State machine to parse game blocks
        $inGame = $false
        $gameType = ""
        $promptLines = @()
        $gameStartIdx = -1
        
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i].Trim()
            
            # Detect game start header
            if ($line -match "CHATGAMES" -and $line -notmatch "WINNER" -and $line -notmatch "TIME EXPIRED") {
                $inGame = $true
                $promptLines = @()
                $gameStartIdx = $i
                
                if ($line -match "MATH") { $gameType = "MATH" }
                elseif ($line -match "FILL") { $gameType = "FILL" }
                elseif ($line -match "WRITE") { $gameType = "WRITE" }
                elseif ($line -match "SORT") { $gameType = "SORT" }
                elseif ($line -match "REVERSE") { $gameType = "REVERSE" }
                elseif ($line -match "TRIVIA") { $gameType = "TRIVIA" }
                elseif ($line -match "VARIABLE") { $gameType = "VARIABLE" }
                else { $gameType = "UNKNOWN" }
                continue
            }
            
            # Collect prompt lines (up to 10 lines after header, before WINNER/EXPIRED)
            if ($inGame -and ($i - $gameStartIdx) -le 10) {
                # Check for winner/expiry
                if ($line -match "CHATGAMES" -and ($line -match "WINNER" -or $line -match "TIME EXPIRED")) {
                    $inGame = $false
                    
                    # Extract answer from next few lines
                    $answer = $null
                    $timeStr = $null
                    for ($j = $i; $j -le [Math]::Min($i + 5, $lines.Count - 1); $j++) {
                        $wline = $lines[$j].Trim()
                        # Match backtick-wrapped answer: `Answer` (X.Xs)
                        if ($wline -match '`([^`]+)`\s*\((\d+\.?\d*)s\)') {
                            $answer = $Matches[1]
                            $timeStr = $Matches[2]
                            break
                        }
                    }
                    
                    $isExpired = $line -match "TIME EXPIRED"
                    
                    $allGames += [PSCustomObject]@{
                        Instance    = $inst.Name
                        LogFile     = [System.IO.Path]::GetFileName($logFile.Path)
                        GameType    = $gameType
                        PromptLines = ($promptLines -join " | ")
                        Answer      = $answer
                        TimeSec     = $timeStr
                        Expired     = $isExpired
                    }
                    continue
                }
                
                # Skip empty lines and non-chat lines
                if ($line -match "\[.*CHAT\]") {
                    # Extract just the chat content
                    $chatContent = $line -replace '^\[.*\[CHAT\]\s*', ''
                    if ($chatContent.Trim().Length -gt 0) {
                        $promptLines += $chatContent.Trim()
                    }
                }
            }
            
            # Timeout: if we've gone too far past game start without a winner, reset
            if ($inGame -and ($i - $gameStartIdx) -gt 30) {
                $inGame = $false
            }
        }
    }
}

Write-Host "`nProcessed $totalLogs log files across $($instances.Count) instances." -ForegroundColor Green
Write-Host "Found $($allGames.Count) total chat game events.`n" -ForegroundColor Green

# ==================== PHASE 2: Summary by type ====================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PHASE 2: Game type breakdown" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$byType = $allGames | Group-Object GameType | Sort-Object Count -Descending
foreach ($g in $byType) {
    $won = ($g.Group | Where-Object { !$_.Expired -and $_.Answer }).Count
    $expired = ($g.Group | Where-Object { $_.Expired }).Count
    $noAnswer = ($g.Group | Where-Object { !$_.Expired -and !$_.Answer }).Count
    Write-Host ("  {0,-12} {1,5} total | {2,4} won | {3,4} expired | {4,4} no-answer" -f $g.Name, $g.Count, $won, $expired, $noAnswer)
}

# Save raw data for the Java backtest
$csvPath = "C:\Users\user\.gemini\antigravity\scratch\meteor-chat-games\backtest_data.csv"
$allGames | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "`nSaved $($allGames.Count) game events to: $csvPath" -ForegroundColor Yellow

# ==================== PHASE 3: Show sample prompts per type ====================
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  PHASE 3: Sample prompts per type" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

foreach ($g in $byType) {
    Write-Host "`n--- $($g.Name) (sample 5) ---" -ForegroundColor Yellow
    $samples = $g.Group | Where-Object { $_.Answer -and !$_.Expired } | Select-Object -First 5
    foreach ($s in $samples) {
        Write-Host "  Prompt: $($s.PromptLines)" -ForegroundColor Gray
        Write-Host "  Answer: $($s.Answer) ($($s.TimeSec)s)" -ForegroundColor Green
    }
}
