# Extract ALL trivia questions and their answers from ALL logs across ALL instances
$ErrorActionPreference = "SilentlyContinue"
$instancesDir = "C:\Users\user\AppData\Roaming\PrismLauncher\instances"

$triviaMap = @{} # question -> answer
$instances = Get-ChildItem $instancesDir -Directory
$totalLogs = 0

foreach ($inst in $instances) {
    $logsDir = "$($inst.FullName)\minecraft\logs"
    if (!(Test-Path $logsDir)) { continue }
    
    $logFiles = @()
    $gzFiles = Get-ChildItem $logsDir -Filter "*.log.gz" -ErrorAction SilentlyContinue
    foreach ($gz in $gzFiles) { $logFiles += @{ Path = $gz.FullName; IsGz = $true } }
    $latestLog = Join-Path $logsDir "latest.log"
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
        
        if (!$content -or $content -notmatch "TRIVIA") { continue }
        $lines = $content -split "`n"
        
        $inTrivia = $false
        $triviaQuestion = $null
        $triviaStartIdx = -1
        
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i].Trim()
            
            # Detect TRIVIA header
            if ($line -match "CHATGAMES.*TRIVIA" -and $line -notmatch "WINNER" -and $line -notmatch "TIME EXPIRED") {
                $inTrivia = $true
                $triviaQuestion = $null
                $triviaStartIdx = $i
                continue
            }
            
            # Look for the question in quotes
            if ($inTrivia -and !$triviaQuestion -and ($i - $triviaStartIdx) -le 8) {
                if ($line -match '"([^"]{15,})"') {
                    $triviaQuestion = $Matches[1]
                }
            }
            
            # Look for WINNER or TIME EXPIRED with answer
            if ($inTrivia -and $triviaQuestion -and ($i - $triviaStartIdx) -le 30) {
                # Winner answer in backticks
                if ($line -match '`([^`]+)`\s*\([\d.]+s\)') {
                    $answer = $Matches[1]
                    $triviaMap[$triviaQuestion] = $answer
                    $inTrivia = $false
                    continue
                }
                # Expired answer (format: "the correct answer was XXXX!")
                if ($line -match "EXPIRED" -or $line -match "TIME EXPIRED") {
                    # Check next few lines for the answer
                    for ($j = $i; $j -le [Math]::Min($i + 5, $lines.Count - 1); $j++) {
                        $wline = $lines[$j].Trim()
                        if ($wline -match '`([^`]+)`\s*\([\d.]+s\)') {
                            $triviaMap[$triviaQuestion] = $Matches[1]
                            break
                        }
                        # Answer after "was" or at end with !
                        if ($wline -match '\b([A-Z][a-zA-Z\s]+)!?\s*$' -and $wline -match '(answer|correct)') {
                            $triviaMap[$triviaQuestion] = $Matches[1].Trim().TrimEnd('!')
                            break
                        }
                        # Just grab the last capitalized word before !
                        if ($wline -match '(\w[\w\s]+)!\s*$' -and $wline -notmatch "CHATGAMES") {
                            $possibleAnswer = $Matches[1].Trim()
                            if ($possibleAnswer.Length -ge 2 -and $possibleAnswer.Length -le 30) {
                                $triviaMap[$triviaQuestion] = $possibleAnswer
                                break
                            }
                        }
                    }
                    $inTrivia = $false
                    continue
                }
            }
            
            # Timeout
            if ($inTrivia -and ($i - $triviaStartIdx) -gt 30) {
                $inTrivia = $false
            }
        }
    }
}

Write-Host "Processed $totalLogs log files." -ForegroundColor Green
Write-Host "Found $($triviaMap.Count) unique trivia Q&A pairs:`n" -ForegroundColor Green

$sorted = $triviaMap.GetEnumerator() | Sort-Object Name
foreach ($entry in $sorted) {
    Write-Host "  Q: $($entry.Name)" -ForegroundColor Cyan
    Write-Host "  A: $($entry.Value)" -ForegroundColor Yellow
    Write-Host ""
}

# Output as JSON for trivia_db.json
$triviaArray = @()
foreach ($entry in $sorted) {
    $triviaArray += @{ question = $entry.Name; answer = $entry.Value }
}
$jsonObj = @{ trivia = $triviaArray; sort = @() }
$jsonObj | ConvertTo-Json -Depth 5 | Set-Content "trivia_extracted.json" -Encoding UTF8
Write-Host "`nSaved to trivia_extracted.json" -ForegroundColor Green
