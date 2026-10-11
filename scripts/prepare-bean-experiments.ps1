#requires -Version 5.1
param([string]$RepoRoot = 'D:\CCNLTHD\spring-boot-course-management')
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

function Test-GitRef([string]$Ref) {
    & git -C $repo show-ref --verify --quiet $Ref
    return ($LASTEXITCODE -eq 0)
}

$null = Get-Command git -ErrorAction Stop
$top = (Invoke-Git @('rev-parse', '--show-toplevel') | Select-Object -Last 1).Trim()
if ([System.IO.Path]::GetFullPath($top) -ne [System.IO.Path]::GetFullPath($repo)) {
    throw 'RepoRoot must point to the Git repository root.'
}
foreach ($relative in @('backend\pom.xml', 'backend\mvnw.cmd')) {
    if (!(Test-Path -LiteralPath (Join-Path $repo $relative))) {
        throw "Missing project file: $relative"
    }
}
$status = @(Invoke-Git @('status', '--porcelain'))
if ($status.Count -gt 0) {
    throw 'Working tree is not clean. Commit or move your current changes before running this preparation script.'
}

Invoke-Git @('fetch', 'origin', '--prune') | ForEach-Object { Write-Host $_ }
if (!(Test-GitRef "refs/remotes/origin/$baseBranch")) {
    throw "Cannot find origin/$baseBranch. Check the remote and branch name."
}

if (Test-GitRef "refs/heads/$workBranch") {
    Invoke-Git @('switch', $workBranch) | ForEach-Object { Write-Host $_ }
    Write-Host 'Existing local lab branch selected; its commits were retained.'
} elseif (Test-GitRef "refs/remotes/origin/$workBranch") {
    Invoke-Git @('switch', '--track', '-c', $workBranch, "origin/$workBranch") |
        ForEach-Object { Write-Host $_ }
} else {
    Invoke-Git @('switch', '--no-track', '-c', $workBranch, "origin/$baseBranch") |
        ForEach-Object { Write-Host $_ }
}

$branch = (Invoke-Git @('branch', '--show-current') | Select-Object -Last 1).Trim()
$commit = (Invoke-Git @('rev-parse', 'HEAD') | Select-Object -Last 1).Trim()
Write-Host "Ready: $branch" -ForegroundColor Green
Write-Host "Current commit: $commit"
Write-Host "PR target: $baseBranch"
Write-Host 'Now copy the M03 Java files and scripts into this branch.'
