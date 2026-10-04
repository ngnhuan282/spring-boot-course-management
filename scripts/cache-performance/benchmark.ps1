[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("off", "miss", "hit")]
    [string]$Mode,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$CourseId,

    [string]$BaseUrl = "http://localhost:8080",

    [ValidateRange(0, 100000)]
    [int]$Warmup = 20,

    [ValidateRange(1, 100000)]
    [int]$Runs = 100,

    [string]$RedisService = "redis",
    [string]$CacheName = "courseDetails",
    [string]$DbService = "mysql",

    [ValidateRange(1, 1440)]
    [int]$TtlMinutes = 10,

    [string]$ComposeFile = "",
    [string]$ResultsRoot = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$Mode = $Mode.ToLowerInvariant()
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$composeFilePath = if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    Join-Path $repoRoot "docker-compose.yml"
} else {
    (Resolve-Path $ComposeFile).Path
}
$resultsRootPath = if ([string]::IsNullOrWhiteSpace($ResultsRoot)) {
    Join-Path $PSScriptRoot "results"
} else {
    $ResultsRoot
}

$baseUri = $BaseUrl.TrimEnd("/")
$endpoint = "$baseUri/api/courses/$CourseId"
$healthEndpoint = "$baseUri/api/health"
$redisKey = "${CacheName}::$CourseId"
$runStartedAt = Get-Date
$resultDirectory = Join-Path $resultsRootPath ("{0}_{1}" -f $runStartedAt.ToString("yyyy-MM-dd_HHmmss"), $Mode)
$requestRows = [System.Collections.Generic.List[object]]::new()
$httpClient = $null

function Invoke-Compose {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & docker compose -f $composeFilePath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $message = (@($output) | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
        throw "docker compose failed: $message"
    }

    return @($output)
}

function Get-GitValue {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & git -C $repoRoot @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git command failed: $((@($output) -join ' '))"
    }

    return ((@($output) -join [Environment]::NewLine).Trim())
}

function Get-GitMetadata {
    $rawStatus = Get-GitValue -Arguments @("status", "--porcelain")
    $sourceStatus = Get-GitValue -Arguments @(
        "status",
        "--porcelain",
        "--",
        ".",
        ":(exclude)scripts/cache-performance/results/**"
    )

    return [ordered]@{
        Branch = Get-GitValue -Arguments @("branch", "--show-current")
        Commit = Get-GitValue -Arguments @("rev-parse", "HEAD")
        RawWorkingTree = if ([string]::IsNullOrWhiteSpace($rawStatus)) { "clean" } else { "dirty" }
        SourceWorkingTree = if ([string]::IsNullOrWhiteSpace($sourceStatus)) { "clean" } else { "dirty" }
    }
}

function Get-FirstOutputLine {
    param([Parameter(Mandatory = $true)][scriptblock]$Command)

    $output = & $Command 2>&1
    return (@($output) | Select-Object -First 1).ToString().Trim()
}

function Get-SystemMetadata {
    $cpu = $env:PROCESSOR_IDENTIFIER
    $ramBytes = $null
    try {
        $computerSystem = Get-CimInstance Win32_ComputerSystem -ErrorAction Stop
        $ramBytes = [long]$computerSystem.TotalPhysicalMemory
    } catch {
        $ramBytes = $null
    }

    $dockerVersion = (& docker version --format "{{.Client.Version}}|{{.Server.Version}}" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Docker daemon is unavailable: $((@($dockerVersion) -join ' '))"
    }

    [xml]$pom = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "backend\pom.xml")
    return [ordered]@{
        Java = Get-FirstOutputLine -Command { java -version }
        SpringBoot = $pom.project.parent.version
        OS = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
        CPU = if ([string]::IsNullOrWhiteSpace($cpu)) { "UNAVAILABLE" } else { $cpu }
        RamBytes = $ramBytes
        Docker = ((@($dockerVersion) -join " ").Trim())
    }
}

function Assert-ComposeServiceRunning {
    param([Parameter(Mandatory = $true)][string]$Service)

    $services = Invoke-Compose -Arguments @("config", "--services")
    $serviceNames = @($services | ForEach-Object { $_.ToString().Trim() })
    if ($Service -notin $serviceNames) {
        throw "Compose service '$Service' is not defined in $composeFilePath."
    }

    $running = Invoke-Compose -Arguments @("ps", "--status", "running", "--services")
    $runningNames = @($running | ForEach-Object { $_.ToString().Trim() })
    if ($Service -notin $runningNames) {
        throw "Compose service '$Service' is not running."
    }
}

function Get-DbSelectCounter {
    $dbCommand = 'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW GLOBAL STATUS LIKE ''Com_select'';"'
    $output = Invoke-Compose -Arguments @("exec", "-T", $DbService, "sh", "-c", $dbCommand)
    $counterLine = @($output | Where-Object { $_.ToString() -match "Com_select" } | Select-Object -Last 1)
    if ($counterLine.Count -eq 0) {
        throw "MySQL did not return the Com_select counter."
    }

    $parts = $counterLine[0].ToString().Trim() -split "\s+"
    $counter = 0L
    if (-not [long]::TryParse($parts[-1], [ref]$counter)) {
        throw "Cannot parse Com_select value from: $($counterLine[0])"
    }

    return $counter
}

function Invoke-RedisIntegerCommand {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = Invoke-Compose -Arguments (@("exec", "-T", $RedisService, "redis-cli") + $Arguments)
    $lastLine = (@($output) | Select-Object -Last 1).ToString().Trim()
    $value = 0
    if (-not [int]::TryParse($lastLine, [ref]$value)) {
        throw "Unexpected Redis response: $lastLine"
    }

    return $value
}

function Remove-RedisKey {
    [void](Invoke-RedisIntegerCommand -Arguments @("DEL", $redisKey))
}

function Get-RedisKeyExists {
    return Invoke-RedisIntegerCommand -Arguments @("EXISTS", $redisKey)
}

function Get-RedisKeyTtl {
    return Invoke-RedisIntegerCommand -Arguments @("TTL", $redisKey)
}

function Invoke-HttpGet {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [switch]$Measure
    )

    $timestamp = Get-Date
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = $httpClient.GetAsync($Url).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $stopwatch.Stop()

    if (-not $response.IsSuccessStatusCode) {
        throw "HTTP $([int]$response.StatusCode) from $Url. Body: $body"
    }

    if ($Measure) {
        return [pscustomobject]@{
            HttpStatus = [int]$response.StatusCode
            LatencyMs = [double]$stopwatch.Elapsed.TotalMilliseconds
            Timestamp = $timestamp.ToString("o")
        }
    }

    return [pscustomobject]@{
        HttpStatus = [int]$response.StatusCode
        Body = $body
    }
}

function Invoke-CourseRequest {
    param([switch]$Measure)

    return Invoke-HttpGet -Url $endpoint -Measure:$Measure
}

function Invoke-ModePreparation {
    if ($Mode -eq "miss") {
        Remove-RedisKey
    }
}

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][string]$Text)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hash = $sha256.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hash)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Get-Median {
    param([Parameter(Mandatory = $true)][double[]]$SortedValues)

    $count = $SortedValues.Count
    if (($count % 2) -eq 1) {
        return $SortedValues[[int][Math]::Floor($count / 2)]
    }

    $upper = [int]($count / 2)
    return ($SortedValues[$upper - 1] + $SortedValues[$upper]) / 2.0
}

function Get-P95NearestRank {
    param([Parameter(Mandatory = $true)][double[]]$SortedValues)

    $index = [Math]::Max(0, [Math]::Ceiling(0.95 * $SortedValues.Count) - 1)
    return $SortedValues[[int]$index]
}

function ConvertTo-MarkdownValue {
    param($Value)

    if ($null -eq $Value) {
        return "UNAVAILABLE"
    }

    return $Value.ToString().Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Save-PartialRequests {
    if ($requestRows.Count -eq 0) {
        return
    }

    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $requestRows |
        Select-Object Run, Mode, CourseId, HttpStatus,
            @{Name = "LatencyMs"; Expression = { $_.LatencyMs.ToString("F6", $culture) }},
            Timestamp |
        Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "requests.csv")
}

try {
    if (-not (Test-Path -LiteralPath $composeFilePath -PathType Leaf)) {
        throw "Compose file not found: $composeFilePath"
    }
    if ($Warmup -lt 0 -or $Runs -lt 1) {
        throw "Warmup and Runs values are invalid."
    }

    foreach ($commandName in @("docker", "git", "java")) {
        if ($null -eq (Get-Command $commandName -ErrorAction SilentlyContinue)) {
            throw "Required command '$commandName' was not found."
        }
    }

    Assert-ComposeServiceRunning -Service $DbService
    if ($Mode -ne "off") {
        Assert-ComposeServiceRunning -Service $RedisService
    }

    $gitMetadata = Get-GitMetadata
    New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null

    $systemMetadata = Get-SystemMetadata
    $httpClient = [System.Net.Http.HttpClient]::new()
    $httpClient.Timeout = [TimeSpan]::FromSeconds(30)

    [void](Invoke-HttpGet -Url $healthEndpoint)
    $coursePreflight = Invoke-CourseRequest
    $coursePayload = $coursePreflight.Body | ConvertFrom-Json
    if ([long]$coursePayload.id -ne $CourseId) {
        throw "Course preflight returned id '$($coursePayload.id)' instead of '$CourseId'."
    }
    $coursePayloadSha256 = Get-Sha256Hex -Text $coursePreflight.Body

    $ttlBefore = $null
    $ttlAfter = $null
    $missContractTtl = $null
    if ($Mode -eq "miss") {
        Remove-RedisKey
        [void](Invoke-CourseRequest)
        if ((Get-RedisKeyExists) -ne 1) {
            throw "MISS preflight failed: Redis key '$redisKey' was not created. The cache contract/key may not match or cache may not be active."
        }
        $missContractTtl = Get-RedisKeyTtl
        if ($missContractTtl -le 0) {
            throw "MISS preflight failed: Redis key '$redisKey' does not have a positive TTL."
        }
        Remove-RedisKey
    } elseif ($Mode -eq "hit") {
        Remove-RedisKey
        [void](Invoke-CourseRequest)
        if ((Get-RedisKeyExists) -ne 1) {
            throw "Redis key '$redisKey' was not created by the prime request."
        }
        $ttlBefore = Get-RedisKeyTtl
        if ($ttlBefore -le 0) {
            throw "Redis key '$redisKey' does not have a positive TTL after priming."
        }
    }

    for ($index = 1; $index -le $Warmup; $index++) {
        Invoke-ModePreparation
        [void](Invoke-CourseRequest)
    }

    $dbSelectBefore = Get-DbSelectCounter
    $measurementStartedAt = Get-Date

    for ($index = 1; $index -le $Runs; $index++) {
        Invoke-ModePreparation
        $measurement = Invoke-CourseRequest -Measure
        $requestRows.Add([pscustomobject]@{
            Run = $index
            Mode = $Mode.ToUpperInvariant()
            CourseId = $CourseId
            HttpStatus = $measurement.HttpStatus
            LatencyMs = $measurement.LatencyMs
            Timestamp = $measurement.Timestamp
        })
    }

    $measurementEndedAt = Get-Date
    $dbSelectAfter = Get-DbSelectCounter

    if ($Mode -eq "hit") {
        if ((Get-RedisKeyExists) -ne 1) {
            throw "Redis key '$redisKey' expired during the measured HIT workload."
        }
        $ttlAfter = Get-RedisKeyTtl
        if ($ttlAfter -le 0) {
            throw "Redis key '$redisKey' does not have a positive TTL after the measured HIT workload."
        }
    }

    $sortedLatencies = [double[]]@($requestRows | ForEach-Object { $_.LatencyMs } | Sort-Object)
    $average = ($sortedLatencies | Measure-Object -Average).Average
    $median = Get-Median -SortedValues $sortedLatencies
    $p95 = Get-P95NearestRank -SortedValues $sortedLatencies
    $minimum = $sortedLatencies[0]
    $maximum = $sortedLatencies[-1]
    $dbSelectDelta = $dbSelectAfter - $dbSelectBefore
    $runEndedAt = Get-Date

    Save-PartialRequests

    $summary = [ordered]@{
        Status = "SUCCESS"
        Timestamp = $runStartedAt.ToString("o")
        StartTime = $runStartedAt.ToString("o")
        EndTime = $runEndedAt.ToString("o")
        MeasurementStartTime = $measurementStartedAt.ToString("o")
        MeasurementEndTime = $measurementEndedAt.ToString("o")
        Mode = $Mode.ToUpperInvariant()
        Endpoint = $endpoint
        CourseId = $CourseId
        CoursePayloadSha256 = $coursePayloadSha256
        Warmup = $Warmup
        MeasuredRequests = $Runs
        Concurrency = 1
        MetricsMs = [ordered]@{
            Average = [Math]::Round([double]$average, 3)
            Median = [Math]::Round([double]$median, 3)
            P95 = [Math]::Round([double]$p95, 3)
            Min = [Math]::Round([double]$minimum, 3)
            Max = [Math]::Round([double]$maximum, 3)
        }
        Database = [ordered]@{
            Service = $DbService
            Counter = "SHOW GLOBAL STATUS LIKE 'Com_select'"
            SelectBefore = $dbSelectBefore
            SelectAfter = $dbSelectAfter
            SelectDelta = $dbSelectDelta
            Limitation = "Com_select is global; unrelated database traffic can affect the delta."
        }
        Cache = [ordered]@{
            RedisService = if ($Mode -eq "off") { "NOT_ACCESSED" } else { $RedisService }
            CacheName = $CacheName
            RedisKey = $redisKey
            ExpectedTtlMinutes = $TtlMinutes
            MissContractTtlSeconds = $missContractTtl
            TtlSecondsAfterPrime = $ttlBefore
            TtlSecondsAfterRun = $ttlAfter
        }
        Repository = $gitMetadata
        Environment = $systemMetadata
        Files = [ordered]@{
            Requests = "requests.csv"
            JsonSummary = "summary.json"
            MarkdownSummary = "summary.md"
        }
    }

    $summary | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "summary.json")

    $summaryLines = @(
        "# Cache Benchmark Summary",
        "",
        "| Metric | Value |",
        "|---|---|",
        "| Status | SUCCESS |",
        "| Mode | $(ConvertTo-MarkdownValue $summary.Mode) |",
        "| Endpoint | $(ConvertTo-MarkdownValue $summary.Endpoint) |",
        "| Course ID | $CourseId |",
        "| Course payload SHA-256 | $coursePayloadSha256 |",
        "| Warm-up | $Warmup |",
        "| Measured requests | $Runs |",
        "| Concurrency | 1 |",
        "| Average (ms) | $($summary.MetricsMs.Average) |",
        "| Median (ms) | $($summary.MetricsMs.Median) |",
        "| P95 nearest-rank (ms) | $($summary.MetricsMs.P95) |",
        "| Min (ms) | $($summary.MetricsMs.Min) |",
        "| Max (ms) | $($summary.MetricsMs.Max) |",
        "| DB SELECT before | $dbSelectBefore |",
        "| DB SELECT after | $dbSelectAfter |",
        "| DB SELECT delta | $dbSelectDelta |",
        "| Git branch | $(ConvertTo-MarkdownValue $gitMetadata.Branch) |",
        "| Git commit | $(ConvertTo-MarkdownValue $gitMetadata.Commit) |",
        "| Raw working tree | $(ConvertTo-MarkdownValue $gitMetadata.RawWorkingTree) |",
        "| Source working tree | $(ConvertTo-MarkdownValue $gitMetadata.SourceWorkingTree) |",
        "| Java | $(ConvertTo-MarkdownValue $systemMetadata.Java) |",
        "| Spring Boot | $(ConvertTo-MarkdownValue $systemMetadata.SpringBoot) |",
        "| OS | $(ConvertTo-MarkdownValue $systemMetadata.OS) |",
        "| CPU | $(ConvertTo-MarkdownValue $systemMetadata.CPU) |",
        "| RAM bytes | $(ConvertTo-MarkdownValue $systemMetadata.RamBytes) |",
        "| Docker | $(ConvertTo-MarkdownValue $systemMetadata.Docker) |",
        "| Redis key | $(ConvertTo-MarkdownValue $redisKey) |",
        "| MISS contract TTL (seconds) | $(ConvertTo-MarkdownValue $missContractTtl) |",
        "| TTL after prime (seconds) | $(ConvertTo-MarkdownValue $ttlBefore) |",
        "| TTL after run (seconds) | $(ConvertTo-MarkdownValue $ttlAfter) |",
        "",
        "> DB query note: Com_select is a global MySQL counter. Other database traffic during the measured workload can affect the delta."
    )
    $summaryLines | Set-Content -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "summary.md")

    Write-Host "Benchmark completed: $resultDirectory"
    Write-Host "Median: $($summary.MetricsMs.Median) ms; P95: $($summary.MetricsMs.P95) ms; DB SELECT delta: $dbSelectDelta"
} catch {
    if (-not (Test-Path -LiteralPath $resultDirectory)) {
        New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
    }

    Save-PartialRequests
    $failure = [ordered]@{
        Status = "FAILED"
        Timestamp = (Get-Date).ToString("o")
        Mode = $Mode.ToUpperInvariant()
        Endpoint = $endpoint
        CourseId = $CourseId
        CompletedMeasuredRequests = $requestRows.Count
        Error = $_.Exception.Message
        Note = "No successful summary was generated."
    }
    $failure | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "failure.json")
    [Console]::Error.WriteLine(
        "Benchmark failed. Evidence: $(Join-Path $resultDirectory 'failure.json'). $($_.Exception.Message)"
    )
    exit 1
} finally {
    if ($null -ne $httpClient) {
        $httpClient.Dispose()
    }
}
