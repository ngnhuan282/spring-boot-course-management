#requires -Version 5.1
param(
    [string]$RepoRoot = 'D:\CCNLTHD\spring-boot-course-management',
    [switch]$SkipBrowser
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath $RepoRoot).Path
$baseBranch = 'refactor/course-standalone'
$workBranch = 'lab/bean-experiments'

function Invoke-Git([string[]]$GitArgs) {
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = & git -C $repo @GitArgs 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    $output = @($lines | ForEach-Object { $_.ToString() })
    if ($exitCode -ne 0) { throw ($output -join "`n") }
    return $output
}

$null = Get-Command git -ErrorAction Stop
$branch = (Invoke-Git @('branch', '--show-current') | Select-Object -Last 1).Trim()
if ($branch -ne $workBranch) { throw "Switch to $workBranch first." }
$status = @(Invoke-Git @('status', '--porcelain'))
if ($status.Count -gt 0) { throw 'Commit your changes before pushing the lab branch.' }

Invoke-Git @('fetch', 'origin', '--prune') | ForEach-Object { Write-Host $_ }
$comparison = "origin/${baseBranch}...HEAD"
$diff = @(Invoke-Git @('diff', '--stat', $comparison))
if ($diff.Count -eq 0) { throw 'No file changes to propose against standalone.' }
$diff | ForEach-Object { Write-Host $_ }

Invoke-Git @('push', '-u', 'origin', $workBranch) | ForEach-Object { Write-Host $_ }
$base = [System.Uri]::EscapeDataString($baseBranch)
$head = [System.Uri]::EscapeDataString($workBranch)
$url = "https://github.com/ngnhuan282/spring-boot-course-management/compare/${base}...${head}?expand=1"
Write-Host "Base: $baseBranch"
Write-Host "Compare: $workBranch"
Write-Host "Create or view the PR here: $url" -ForegroundColor Green
Write-Host 'This script opens the comparison page. Submit Create pull request there and ask another member to review.'
if (!$SkipBrowser) { Start-Process $url }
