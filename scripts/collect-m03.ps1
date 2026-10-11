#requires -Version 5.1
param([string]$RepoRoot, [string]$OutputDir)
$ErrorActionPreference = 'Stop'
if (!$RepoRoot) { $RepoRoot = Join-Path $PSScriptRoot '..' }
$repo = (Resolve-Path -LiteralPath $RepoRoot).Path
$baseBranch = 'refactor/course-standalone'
$backend = Join-Path $repo 'backend'
$wrapper = Join-Path $backend 'mvnw.cmd'
$testDir = Join-Path $backend 'src\test\java\com\ccnlthd\course_management\lab\m03'
$probe = Join-Path $testDir 'BeanLifecycleProbe.java'
$test = Join-Path $testDir 'M03BeanLifecycleTest.java'
$report = Join-Path $backend 'target\surefire-reports\TEST-com.ccnlthd.course_management.lab.m03.M03BeanLifecycleTest.xml'

function Invoke-Logged($File, [string[]]$Arguments, $Log) {
    $null = Get-Command $File -ErrorAction Stop
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue' # Native stderr is captured on PS 5.1.
        $lines = & $File @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    $text = @($lines | ForEach-Object { $_.ToString() })
    $text | Set-Content -LiteralPath $Log -Encoding UTF8
    if ($exitCode -ne 0) { throw "Command failed. Read: $Log" }
    return $text
}

foreach ($file in @($wrapper, $probe, $test)) {
    if (!(Test-Path -LiteralPath $file)) { throw "Missing file: $file" }
}
$branch = (& git -C $repo branch --show-current).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git branch.' }
if ($branch -ne 'lab/bean-experiments') { throw 'Switch to lab/bean-experiments first.' }
$status = @(& git -C $repo status --porcelain)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status.' }
if ($status.Count -gt 0) { throw 'Commit your changes before collecting evidence.' }
$commit = (& git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git commit.' }
$baseRef = "refs/remotes/origin/$baseBranch"
& git -C $repo show-ref --verify --quiet $baseRef
if ($LASTEXITCODE -ne 0) { $baseRef = "refs/heads/$baseBranch" }
$baseCommit = (& git -C $repo merge-base HEAD $baseRef).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve standalone base. Run prepare-bean-experiments.ps1 first.' }
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss_fff'
if (!$OutputDir) { $OutputDir = Join-Path (Split-Path $repo -Parent) "M03-evidence\$stamp" }
$outputPath = [System.IO.Path]::GetFullPath($OutputDir)
$repoPrefix = $repo.TrimEnd([char[]]@('\', '/')) + [System.IO.Path]::DirectorySeparatorChar
if ($outputPath.Equals($repo, [System.StringComparison]::OrdinalIgnoreCase) -or
    $outputPath.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose an evidence directory outside the Git repository.'
}
$out = (New-Item -ItemType Directory -Path $OutputDir -Force).FullName
$started = (Get-Date).ToString('o')
$branch | Set-Content "$out\branch.txt" -Encoding UTF8
$commit | Set-Content "$out\commit.txt" -Encoding UTF8
$baseBranch | Set-Content "$out\base-branch.txt" -Encoding UTF8
$baseCommit | Set-Content "$out\base-commit.txt" -Encoding UTF8
$baseRef | Set-Content "$out\base-ref.txt" -Encoding UTF8
& git -C $repo status | Set-Content "$out\git-status.txt" -Encoding UTF8
Copy-Item -LiteralPath $probe, $test -Destination $out
Copy-Item -LiteralPath $PSCommandPath -Destination $out

Push-Location $backend
try {
    $null = Invoke-Logged 'java' @('-version') "$out\java-version.txt"
    $null = Invoke-Logged $wrapper @('-v') "$out\maven-version.txt"
    if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report }
    Write-Host 'Running only the three M03 cases...'
    $mavenArgs = @('-Dtest=M03BeanLifecycleTest', '-DskipTests=false',
        '-Dmaven.test.skip=false', '-DenableOutErrElements=true', '-Dstyle.color=never', 'test')
    $null = Invoke-Logged $wrapper $mavenArgs "$out\maven.log"
} finally { Pop-Location }

if (!(Test-Path -LiteralPath $report)) { throw 'No fresh M03 Surefire report.' }
[xml]$xml = Get-Content -LiteralPath $report -Raw
$suite = $xml.testsuite
if ([int]$suite.tests -ne 3 -or [int]$suite.failures -ne 0 -or
    [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
    throw "M03 did not pass all three cases. Read: $out\maven.log"
}
Copy-Item -LiteralPath $report -Destination $out
$meta = "Branch: $branch`nCommit: $commit`nRun: $started"

Add-Type -AssemblyName System.Drawing
function Save-Png($Name, $Title, [string]$Text) {
    $lines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($line in ($Text -replace "`t", '    ' -split '\r?\n')) {
        if (!$line.Length) { $lines.Add(''); continue }
        for ($i = 0; $i -lt $line.Length; $i += 82) {
            $lines.Add($line.Substring($i, [Math]::Min(82, $line.Length - $i)))
        }
    }
    $height = 226 + 30 * $lines.Count
    $bitmap = [System.Drawing.Bitmap]::new(1200, $height)
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $font = [System.Drawing.Font]::new('Consolas', 22, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $heading = [System.Drawing.Font]::new('Consolas', 23, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    try {
        $g.Clear([System.Drawing.Color]::White)
        $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
        $g.DrawString($Title, $heading, [System.Drawing.Brushes]::DarkBlue, 30, 20)
        $g.DrawString($meta, $font, [System.Drawing.Brushes]::DimGray, 30, 60)
        $g.FillRectangle([System.Drawing.Brushes]::WhiteSmoke, 24, 180, 1152, $height - 204)
        $g.DrawRectangle([System.Drawing.Pens]::Silver, 24, 180, 1152, $height - 204)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $g.DrawString($lines[$i], $font, [System.Drawing.Brushes]::Black, 36, 188 + 30 * $i)
        }
        $bitmap.Save((Join-Path $out $Name), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $heading.Dispose(); $g.Dispose(); $bitmap.Dispose() }
}

$source = Get-Content -LiteralPath $test -Raw
function Get-Code([string]$Mode) {
    $pattern = '(?ms)^[ \t]*// M03 CODE BEGIN ' + $Mode + '\r?\n(.*?)^[ \t]*// M03 CODE END ' + $Mode + '\r?$'
    $sections = [regex]::Matches($source, $pattern)
    if (!$sections.Count) { throw "Missing source section: $Mode" }
    return (($sections | ForEach-Object { $_.Groups[1].Value.TrimEnd() }) -join "`n`n")
}
Save-Png 'M03_0_probe_code.png' 'M03 / BeanLifecycleProbe / SOURCE' (Get-Content -LiteralPath $probe -Raw)
$index = 0
foreach ($mode in @('singleton', 'prototype', 'new')) {
    $index++
    $logMode = $mode
    if ($mode -eq 'new') { $method = 'plainNew' } else { $method = $mode }
    $case = @($suite.testcase | Where-Object { $_.name -eq $method })
    if ($case.Count -ne 1) { throw "Missing test case: $method" }
    $stdout = $case[0].SelectSingleNode('system-out')
    if (!$stdout) { throw "Missing runtime stdout: $method" }
    $log = $stdout.InnerText
    if (!$log.Contains("[M03] BEGIN $logMode") -or !$log.Contains("[M03] END $logMode")) {
        throw "Incomplete runtime log: $mode"
    }
    $log | Set-Content "$out\$mode.log" -Encoding UTF8
    $code = Get-Code $mode
    if ($mode -ne 'new') { $code += "`n`n" + (Get-Code 'shared') }
    Save-Png "M03_${index}_${mode}_code.png" "M03 / $mode / SOURCE EXCERPTS" $code
    Save-Png "M03_${index}_${mode}_log.png" "M03 / $mode / RUNTIME LOG" $log
}
@"
# M03 - Bean scope and lifecycle
Repository: https://github.com/ngnhuan282/spring-boot-course-management
$meta
PR target: $baseBranch
Merge base: $baseCommit
Base ref used locally: $baseRef
Finished: $((Get-Date).ToString('o'))
Command: backend\mvnw.cmd $($mavenArgs -join ' ')
Surefire: tests=$($suite.tests), failures=$($suite.failures), errors=$($suite.errors), skipped=$($suite.skipped)
PNG files render source excerpts and actual per-test stdout; original sources/logs/XML are retained.
This run covers M03 only; it does not establish CRUD/cache/integration test results.
"@ | Set-Content "$out\evidence.md" -Encoding UTF8
Get-FileHash -Algorithm SHA256 -LiteralPath $probe, $test, $PSCommandPath |
    Format-Table -AutoSize | Out-String -Width 240 |
    Set-Content "$out\source-hashes.txt" -Encoding UTF8
Write-Host "Done: 3 tests passed; 7 PNG files. Evidence: $out" -ForegroundColor Green
Invoke-Item -LiteralPath $out
