[CmdletBinding()]
param(
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot ('.m15-results/run-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$mavenOutputRoot = [IO.Path]::GetFullPath((Join-Path $backendRoot 'target'))
if ($OutputDirectory.Equals($mavenOutputRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $OutputDirectory.StartsWith($mavenOutputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose an output directory outside backend/target; Maven clean removes that directory.'
}
if (Test-Path -LiteralPath $OutputDirectory) {
    throw "Choose a NEW output directory; existing evidence will not be overwritten: $OutputDirectory"
}

function Write-JsonFile($Value, [string]$Path) {
    $Value | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-EvidenceArchive([string]$Path, [object[]]$Files) {
    $archive = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($file in $Files) {
            $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $archive, $file.full_path, $file.path, [IO.Compression.CompressionLevel]::Optimal)
        }
    } finally {
        $archive.Dispose()
    }
}

function Get-GitState {
    $commit = & git -C $repoRoot rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read source commit.' }
    $branch = & git -C $repoRoot branch --show-current
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read branch.' }
    $status = @(& git -C $repoRoot status --porcelain=v1)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read working tree status.' }
    [ordered]@{ commit = "$commit"; branch = "$branch"; status = $status }
}

function Get-SourceHashes {
    $files = @(
        Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src') -Recurse -File
        Get-Item -LiteralPath (Join-Path $backendRoot 'pom.xml')
        Get-Item -LiteralPath $PSCommandPath
    )
    @($files | Sort-Object FullName | ForEach-Object {
        [pscustomobject]@{
            path = $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })
}

$rawDirectory = Join-Path $OutputDirectory 'raw'
$null = New-Item -ItemType Directory -Path $rawDirectory -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$gitBefore = Get-GitState
$hashesBefore = Get-SourceHashes
$hashesBefore | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'source-sha256.csv') -NoTypeInformation -Encoding UTF8
$sourceFiles = @($hashesBefore | ForEach-Object {
    [pscustomobject]@{ path = $_.path; full_path = Join-Path $repoRoot $_.path }
})
Write-EvidenceArchive (Join-Path $OutputDirectory 'source.zip') $sourceFiles

$conditions = [ordered]@{
    status = 'started'
    started_at = (Get-Date).ToUniversalTime().ToString('o')
    git_before = $gitBefore
    source_identity = 'HEAD plus source-sha256.csv and snapshots in source.zip; working changes are NOT part of HEAD.'
    application_cache = 'courseDetails'
    application_key_format = 'courseDetails::<courseId>'
    cached_dto = 'CourseResponse: id, categoryId, categoryName, title, description, price, level, status'
    key_serializer = 'StringRedisSerializer (RedisCacheConfiguration default)'
    value_serializer = 'JacksonJsonRedisSerializer<CourseResponse>'
    default_ttl_seconds = 600
    expiry_test_override_seconds = 5
    older_mutation_tests = 'CourseCacheIntegrationTests extends selected keys to 30s; its expiry test does not.'
    database = 'H2 in MySQL compatibility mode; isolated test database, not Compose MySQL'
    redis = 'Separate real redis:7.4-alpine Testcontainers for each cache test class; not Compose Redis'
    http = 'Real controllers/services via MockMvc in five M15 tests; expiry test calls the real service'
    query_method = 'Hibernate StatementInspector; SELECTs touching courses/categories, excluding DB snapshot reads; JOIN counts can overlap'
    performance = 'No HTTP latency benchmark or integrated performance percentage is collected.'
    command = 'mvnw.cmd -B clean verify -Dm15.evidence.directory=<raw directory>'
}
$conditionsPath = Join-Path $OutputDirectory 'conditions.json'
Write-JsonFile $conditions $conditionsPath

Write-Host "M15 source commit: $($gitBefore.commit)"
Write-Host "Running clean verify with isolated H2 + real Redis. Output: $OutputDirectory"
Push-Location $backendRoot
try {
    # Native JVM stderr can appear as a PowerShell ErrorRecord; the exit code is authoritative.
    $ErrorActionPreference = 'Continue'
    @(
        'JAVA'; & java -version 2>&1 | ForEach-Object { $_.ToString() }
        'MAVEN'; & '.\mvnw.cmd' -version 2>&1 | ForEach-Object { $_.ToString() }
        'DOCKER'; & docker version --format '{{.Server.Version}}' 2>&1 | ForEach-Object { $_.ToString() }
    ) | Out-File -LiteralPath (Join-Path $OutputDirectory 'versions.txt') -Encoding UTF8
    & '.\mvnw.cmd' -B clean verify "-Dm15.evidence.directory=$rawDirectory" *> (Join-Path $OutputDirectory 'test-output.txt')
    $buildExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
    Pop-Location
}

$conditions['finished_at'] = (Get-Date).ToUniversalTime().ToString('o')
$conditions['maven_exit_code'] = $buildExitCode
$conditions['git_after'] = Get-GitState
$hashesAfter = Get-SourceHashes
$changed = @(Compare-Object ($hashesBefore | ConvertTo-Json -Compress) ($hashesAfter | ConvertTo-Json -Compress))
$conditions['source_changed_during_run'] = ($changed.Count -gt 0)
$conditions['status'] = 'failed'
Write-JsonFile $conditions $conditionsPath
if ($buildExitCode -ne 0) {
    Get-Content -LiteralPath (Join-Path $OutputDirectory 'test-output.txt') -Tail 50
    throw "Maven failed ($buildExitCode). Evidence preserved at $OutputDirectory"
}
if ($changed.Count -gt 0 -or $conditions.git_after.commit -ne $gitBefore.commit) {
    throw 'Source changed during the run. Run again on a stable source version.'
}

$reports = @(Get-ChildItem -LiteralPath (Join-Path $backendRoot 'target/surefire-reports') -Filter 'TEST-*.xml')
$testSummary = @($reports |
    ForEach-Object {
        [xml]$report = Get-Content -LiteralPath $_.FullName -Raw
        [pscustomobject]@{
            suite = [string]$report.testsuite.name
            tests = [int]$report.testsuite.tests
            failures = [int]$report.testsuite.failures
            errors = [int]$report.testsuite.errors
            skipped = [int]$report.testsuite.skipped
        }
    })
$testSummary | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'test-summary.csv') -NoTypeInformation -Encoding UTF8
if ($testSummary.Count -eq 0 -or @($testSummary | Where-Object { $_.failures -gt 0 -or $_.errors -gt 0 }).Count -gt 0) {
    throw 'Missing or failed Surefire reports.'
}
foreach ($name in @('CourseCacheIntegrationTests', 'M15CategoryCacheEvidenceTests')) {
    $suite = @($testSummary | Where-Object { $_.suite.EndsWith(".$name") })
    if ($suite.Count -ne 1 -or $suite[0].tests -eq 0 -or $suite[0].skipped -gt 0) {
        throw "Required cache suite missing or skipped: $name"
    }
}
$reportFiles = @($reports | ForEach-Object {
    [pscustomobject]@{ path = $_.Name; full_path = $_.FullName }
})
Write-EvidenceArchive (Join-Path $OutputDirectory 'surefire.zip') $reportFiles

$scenarioNames = @('miss-hit', 'course-update-delete', 'category-rename-two-courses',
    'category-rollback', 'category-delete-conflict', 'ttl-5s')
$documents = @($scenarioNames | ForEach-Object {
    Get-Content -LiteralPath (Join-Path $rawDirectory ($_ + '.json')) -Raw | ConvertFrom-Json
})
$queryRows = @()
$redisRows = @()
foreach ($document in $documents) {
    if ($document.steps.Count -eq 0) { throw "No observations in $($document.scenario)" }
    foreach ($step in $document.steps) {
        $hasSql = $null -ne $step.PSObject.Properties['sql']
        $hasStatus = $null -ne $step.PSObject.Properties['http_status']
        $queryRows += [pscustomobject]@{
            scenario = $document.scenario; stage = $step.stage; at = $step.at
            http_status = $(if ($hasStatus) { $step.http_status } else { $null })
            select_statements = $(if ($hasSql) { $step.sql.selectStatements } else { $null })
            course_selects = $(if ($hasSql) { $step.sql.courseSelects } else { $null })
            category_selects = $(if ($hasSql) { $step.sql.categorySelects } else { $null })
        }
        foreach ($state in $step.redis) {
            $redisRows += [pscustomobject]@{
                scenario = $document.scenario; stage = $step.stage; at = $step.at
                key = $state.key; exists = $state.exists; ttl_ms = $state.ttl_ms
                category_id = $(if ($null -ne $state.json) { $state.json.categoryId } else { $null })
                category_name = $(if ($null -ne $state.json) { $state.json.categoryName } else { $null })
                title = $(if ($null -ne $state.json) { $state.json.title } else { $null })
            }
        }
    }
}
$queryRows | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'query-counts.csv') -NoTypeInformation -Encoding UTF8
$redisRows | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'redis-states.csv') -NoTypeInformation -Encoding UTF8
$conditions['status'] = 'passed'
$conditions['test_count'] = ($testSummary | Measure-Object tests -Sum).Sum
$conditions['skipped_test_count'] = ($testSummary | Measure-Object skipped -Sum).Sum
$conditions['scenario_files'] = $scenarioNames
Write-JsonFile $conditions $conditionsPath

$display = @(
    "M15 PASSED: $($conditions.test_count) tests; $($conditions.skipped_test_count) skipped."
    'FUNCTIONAL EVIDENCE: H2 MySQL mode + real Redis + MockMvc; not a client latency benchmark.'
    'TTL: default 600s; separate actual expiry test configured to 5s.'
    ($queryRows | Format-Table scenario, stage, http_status, course_selects, category_selects, select_statements -AutoSize | Out-String -Width 220)
    ($redisRows | Where-Object { $_.scenario -eq 'category-rename-two-courses' -or $_.scenario -eq 'ttl-5s' } |
        Format-Table scenario, stage, key, exists, ttl_ms, category_name -AutoSize | Out-String -Width 240)
    "Source commit: $($gitBefore.commit)"
    "Source SHA256 and working tree state: $OutputDirectory"
    'HEAD identifies the base; source snapshots identify any uncommitted implementation.'
)
$display | Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary.txt') -Encoding UTF8
$display | ForEach-Object { Write-Host $_ }
