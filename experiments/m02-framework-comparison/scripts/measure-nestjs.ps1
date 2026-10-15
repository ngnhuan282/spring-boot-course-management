$times = @()
$results = @()

$node = (Get-Command node).Source
$main = (Resolve-Path ".\dist\main.js").Path

if (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) {
    throw "Port 8082 dang duoc su dung. Hay tat ung dung truoc."
}

1..10 | ForEach-Object {
    $run = $_
    Write-Host "Starting run $run..."

    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    $process = Start-Process `
        -FilePath $node `
        -ArgumentList ""$main"" `
        -WorkingDirectory (Get-Location).Path `
        -PassThru `
        -WindowStyle Hidden

    $ready = $false

    for ($i = 0; $i -lt 300; $i++) {
        Start-Sleep -Milliseconds 50
        $process.Refresh()

        if ($process.HasExited) { break }

        try {
            $response = Invoke-WebRequest `
                -Uri "http://localhost:8082/api/health" `
                -UseBasicParsing `
                -TimeoutSec 1

            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        }
        catch {}
    }

    $sw.Stop()

    if ($ready) {
        $seconds = [math]::Round($sw.Elapsed.TotalSeconds, 3)
        $times += $seconds
        $status = "OK"
        Write-Host "Run ${run}: $seconds s"
    }
    else {
        $seconds = $null
        $status = "FAILED"
        Write-Host "Run $run FAILED"
    }

    $results += [PSCustomObject]@{
        Run = $run
        Seconds = $seconds
        Status = $status
    }

    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        $process.WaitForExit()
    }

    Start-Sleep -Seconds 1
}

$results | Format-Table -AutoSize

$file = Join-Path $env:USERPROFILE "Desktop\M02_nestjs_10lan.csv"
$results | Export-Csv -Path $file -NoTypeInformation -Encoding UTF8

if ($times.Count -eq 10) {
    $sorted = @($times | Sort-Object)
    $median = ($sorted[4] + $sorted[5]) / 2
    Write-Host "Median: $median s"
}

Write-Host "Da luu CSV: $file"