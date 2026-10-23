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
    [int]$Runs = 30,

    [ValidateRange(1, 1000)]
    [int]$Rounds = 3,

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
    Join-Path (Split-Path -Parent $repoRoot) "cache-benchmark-evidence"
} else {
    $ResultsRoot
}
$resultsRootPath = [IO.Path]::GetFullPath($resultsRootPath).TrimEnd('\', '/')
if ($resultsRootPath -ieq $repoRoot -or $resultsRootPath.StartsWith($repoRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "ResultsRoot must be outside the repository, as required by STT01."
}

$baseUri = $BaseUrl.TrimEnd("/")
$endpoint = "$baseUri/api/courses/$CourseId"
$healthEndpoint = "$baseUri/api/health"
$redisKey = "${CacheName}::$CourseId"
$runStartedAt = Get-Date
$resultDirectory = Join-Path $resultsRootPath ("{0}_{1}_{2}" -f $runStartedAt.ToString("yyyy-MM-dd_HHmmss_fff"), $Mode, [guid]::NewGuid().ToString('N').Substring(0, 6))
$requestRows = [System.Collections.Generic.List[object]]::new()
$roundRows = [System.Collections.Generic.List[object]]::new()
$httpClient = $null
$gitMetadata = $null

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory = $true)][string]$FailureContext
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $nativeOutput = @()
    $exitCode = $null
    try {
        $ErrorActionPreference = "Continue"
        $nativeOutput = @(& $FilePath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $outputLines = @($nativeOutput | ForEach-Object { $_.ToString() })
    if ($null -eq $exitCode) {
        throw "$FailureContext did not provide a native exit code."
    }
    if ($exitCode -ne 0) {
        $message = ($outputLines -join [Environment]::NewLine).Trim()
        if ([string]::IsNullOrWhiteSpace($message)) {
            $message = "No native output was captured."
        }
        throw "$FailureContext failed with exit code ${exitCode}: $message"
    }

    return $outputLines
}

function Invoke-Compose {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    return @(Invoke-NativeCommand `
        -FilePath "docker" `
        -Arguments (@("compose", "-f", $composeFilePath) + $Arguments) `
        -FailureContext "docker compose")
}

function Get-GitValue {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = Invoke-NativeCommand `
        -FilePath "git" `
        -Arguments (@("-C", $repoRoot) + $Arguments) `
        -FailureContext "git command"

    return ((@($output) -join [Environment]::NewLine).Trim())
}

function Get-GitMetadata {
    $rawStatus = Get-GitValue -Arguments @("status", "--porcelain=v1", "--untracked-files=all")

    return [ordered]@{
        Branch = Get-GitValue -Arguments @("branch", "--show-current")
        Commit = Get-GitValue -Arguments @("rev-parse", "HEAD")
        RawWorkingTree = if ([string]::IsNullOrWhiteSpace($rawStatus)) { "clean" } else { "dirty" }
        SourceWorkingTree = if ([string]::IsNullOrWhiteSpace($rawStatus)) { "clean" } else { "dirty" }
        Status = $rawStatus
        TrackedDiffSha256 = Get-Sha256Hex -Text (Get-GitValue -Arguments @("diff", "--binary", "HEAD"))
    }
}

function Get-FirstOutputLine {
    param([Parameter(Mandatory = $true)][object[]]$Output)

    $firstLine = @($Output | ForEach-Object { $_.ToString().Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -First 1)
    if ($firstLine.Count -eq 0) {
        throw "Native command returned no output."
    }

    return $firstLine[0]
}

function Get-DotNetDescription {
    # Windows PowerShell can resolve an older RuntimeInformation facade.
    # Probe by reflection and keep the CLR version when that API is unavailable.
    try {
        $type = [System.Runtime.InteropServices.RuntimeInformation]
        $property = $type.GetProperty("FrameworkDescription", [Reflection.BindingFlags]"Public,Static")
        if ($null -ne $property) {
            $description = [string]$property.GetValue($null, $null)
            if (-not [string]::IsNullOrWhiteSpace($description)) { return $description }
        }
    } catch {
        # Missing optional metadata must not discard already recorded HTTP samples.
    }
    return "CLR $([Environment]::Version); runtime $([Runtime.InteropServices.RuntimeEnvironment]::GetSystemVersion()); FrameworkDescription unavailable"
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

    $dockerVersion = Invoke-NativeCommand `
        -FilePath "docker" `
        -Arguments @("version", "--format", "{{.Client.Version}}|{{.Server.Version}}") `
        -FailureContext "Docker daemon check"
    $javaVersion = Invoke-NativeCommand `
        -FilePath "java" `
        -Arguments @("-version") `
        -FailureContext "Java version check"

    [xml]$pom = Get-Content -Raw -LiteralPath (Join-Path $repoRoot "backend\pom.xml")
    return [ordered]@{
        Java = Get-FirstOutputLine -Output $javaVersion
        JavaObservation = "Host java -version only; not proof of the backend JVM or a forked test JVM. Use the STT01 collector for actual runtime evidence."
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
    # The hex literal avoids nested quote loss when Windows PowerShell builds native arguments.
    $dbCommand = 'export MYSQL_PWD=$MYSQL_ROOT_PASSWORD; echo SHOW GLOBAL STATUS WHERE Variable_name=0x436f6d5f73656c656374\; | mysql -N -B -uroot'
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
    return Invoke-RedisIntegerCommand -Arguments @("PTTL", $redisKey)
}

function Get-RedisObservation {
    return [pscustomobject]@{ Exists = Get-RedisKeyExists; TtlMs = Get-RedisKeyTtl }
}

function Assert-RedisObservation {
    param($Observation, [string]$Stage)
    if ($Mode -eq "off") {
        if ($Observation.Exists -ne 0 -or $Observation.TtlMs -ne -2) {
            throw "OFF $Stage failed: '$redisKey' must remain absent (EXISTS=0, PTTL=-2). Start the backend with spring.cache.type=none."
        }
    } elseif ($Observation.Exists -ne 1 -or $Observation.TtlMs -le 0) {
        throw "$Mode $Stage failed: '$redisKey' must exist with a positive PTTL."
    }
}

function Invoke-HttpGet {
    param(
        [Parameter(Mandatory = $true)][string]$Url
    )

    $timestamp = [DateTimeOffset]::Now.ToString("o")
    $response = $null
    $status = 0
    $body = ""
    $errorText = $null
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        # The SAME boundary is used in OFF, MISS and HIT, including the complete body.
        # ContentRead keeps HttpClient.Timeout in effect while buffering the body.
        $response = $httpClient.GetAsync($Url, [System.Net.Http.HttpCompletionOption]::ResponseContentRead).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    } catch {
        $errorText = $_.Exception.GetType().FullName
    } finally {
        $stopwatch.Stop()
        if ($null -ne $response) { $response.Dispose() }
    }
    return [pscustomobject]@{
        HttpStatus = $status; Body = $body; Error = $errorText
        LatencyMs = [double]$stopwatch.Elapsed.TotalMilliseconds; Timestamp = $timestamp
    }
}

function Assert-CourseResponse {
    param($Response, [string]$ExpectedHash = "")
    if ($Response.Error -or $Response.HttpStatus -ne 200) {
        throw "Expected HTTP 200 from $endpoint; got $($Response.HttpStatus), transport error: $($Response.Error)."
    }
    $payload = $Response.Body | ConvertFrom-Json
    if ([long]$payload.id -ne $CourseId) { throw "Response Course ID does not match $CourseId." }
    $hash = Get-Sha256Hex -Text $Response.Body
    if ($ExpectedHash -and $hash -ne $ExpectedHash) { throw "Course payload changed during the benchmark; discard this comparison." }
}

function Invoke-CourseRequest {
    return Invoke-HttpGet -Url $endpoint
}

function Invoke-ModePreparation {
    if ($Mode -eq "miss") {
        Remove-RedisKey
        if ((Get-RedisKeyExists) -ne 0) { throw "MISS preparation failed: selected key still exists." }
    }
}

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)

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
    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    if ($requestRows.Count) { $requestRows |
        Select-Object scenario, round, request, course_id, http_status,
            @{Name = "latency_ms"; Expression = { $_.latency_ms.ToString("R", $culture) }},
            timestamp, redis_key, exists_before, pttl_before_ms, exists_after, pttl_after_ms, transport_error |
        Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "requests.csv")
    }
    if ($roundRows.Count) { $roundRows | Export-Csv -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "rounds.csv") }
}

function Assert-OffCache {
    # These three requests verify OFF before warm-up; none are latency samples.
    $verification = [ordered]@{
        Status = "RUNNING"
        RedisKey = $redisKey
        Checks = @()
        SelectBefore = $null
        SelectAfter = $null
        SelectDelta = $null
        CounterScope = "GLOBAL MySQL counter; not an exact count of Course queries."
        Error = $null
    }
    $checks = [System.Collections.Generic.List[object]]::new()
    try {
        Remove-RedisKey
        $verification.SelectBefore = Get-DbSelectCounter
        for ($probe = 1; $probe -le 3; $probe++) {
            $response = Invoke-CourseRequest
            $redis = Get-RedisObservation
            $checks.Add([pscustomobject]@{
                Request = $probe
                HttpStatus = $response.HttpStatus
                RedisKeyExists = $redis.Exists
                RedisPttlMs = $redis.TtlMs
                Timestamp = $response.Timestamp
            })
            $verification.Checks = $checks.ToArray()
            Assert-CourseResponse $response $coursePayloadSha256
            if ($redis.Exists -ne 0 -or $redis.TtlMs -ne -2) {
                throw "OFF verification failed: a Redis cache key was created. Check BENCH_CACHE and restart the backend with spring.cache.type=none."
            }
        }
        $verification.SelectAfter = Get-DbSelectCounter
        $verification.SelectDelta = $verification.SelectAfter - $verification.SelectBefore
        if ($verification.SelectDelta -le 0) {
            throw "OFF verification failed: no increase in MySQL Com_select."
        }
        $verification.Status = "SUCCESS"
        return $verification
    } catch {
        $verification.Status = "FAILED"
        $verification.Error = $_.Exception.Message
        throw
    } finally {
        # Keep partial checks even when an intentionally wrong OFF configuration fails.
        $verification | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $resultDirectory "off-verification.json")
    }
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
    Assert-ComposeServiceRunning -Service $RedisService

    $gitMetadata = Get-GitMetadata
    New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
    Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $resultDirectory "benchmark.ps1")

    $systemMetadata = Get-SystemMetadata
    $httpHandler = [System.Net.Http.HttpClientHandler]::new()
    $httpHandler.AllowAutoRedirect = $false
    $httpClient = [System.Net.Http.HttpClient]::new($httpHandler)
    $httpClient.Timeout = [TimeSpan]::FromSeconds(30)

    $health = Invoke-HttpGet -Url $healthEndpoint
    if ($health.Error -or $health.HttpStatus -ne 200) { throw "Health check did not return HTTP 200." }
    # Only the selected test key is deleted, outside the HTTP timing boundary.
    Remove-RedisKey
    $coursePreflight = Invoke-CourseRequest
    Assert-CourseResponse $coursePreflight
    $coursePayloadSha256 = Get-Sha256Hex -Text $coursePreflight.Body
    [IO.File]::WriteAllText((Join-Path $resultDirectory "course-preflight.json"), $coursePreflight.Body, [Text.UTF8Encoding]::new($false))
    $preflightRedis = Get-RedisObservation
    if ($Mode -ne "off") { Assert-RedisObservation $preflightRedis "after preflight GET" }

    $ttlBefore = $null
    $ttlAfter = $null
    $missContractTtl = $null
    $offVerification = $null
    if ($Mode -eq "off") {
        $offVerification = Assert-OffCache
    } elseif ($Mode -eq "miss") {
        $missContractTtl = $preflightRedis.TtlMs
    } elseif ($Mode -eq "hit") {
        $ttlBefore = $preflightRedis.TtlMs
    }

    $lastHitTtl = $ttlBefore
    for ($index = 1; $index -le $Warmup; $index++) {
        Invoke-ModePreparation
        $warmResponse = Invoke-CourseRequest
        Assert-CourseResponse $warmResponse $coursePayloadSha256
        $warmRedis = Get-RedisObservation
        Assert-RedisObservation $warmRedis "after warm-up $index"
        if ($Mode -eq "hit") {
            if ($warmRedis.TtlMs -gt $lastHitTtl) { throw "HIT key TTL increased during warm-up; the key may have been recreated." }
            $lastHitTtl = $warmRedis.TtlMs
        }
    }

    $measurementStartedAt = Get-Date
    for ($round = 1; $round -le $Rounds; $round++) {
        $roundBefore = Get-DbSelectCounter
        for ($index = 1; $index -le $Runs; $index++) {
            Invoke-ModePreparation
            $redisBefore = Get-RedisObservation
            if ($Mode -eq "miss") {
                if ($redisBefore.Exists -ne 0 -or $redisBefore.TtlMs -ne -2) { throw "MISS key was not absent before request." }
            } else { Assert-RedisObservation $redisBefore "before request $round/$index" }
            if ($Mode -eq "hit" -and $redisBefore.TtlMs -gt $lastHitTtl) { throw "HIT key TTL increased between requests." }
            $measurement = Invoke-CourseRequest
            $row = [pscustomobject]@{
                scenario = $Mode.ToUpperInvariant(); round = $round; request = $index
                course_id = $CourseId; http_status = $measurement.HttpStatus
                latency_ms = $measurement.LatencyMs; timestamp = $measurement.Timestamp
                redis_key = $redisKey; exists_before = $redisBefore.Exists; pttl_before_ms = $redisBefore.TtlMs
                exists_after = $null; pttl_after_ms = $null; transport_error = $measurement.Error
            }
            $requestRows.Add($row)
            Assert-CourseResponse $measurement $coursePayloadSha256
            $redisAfter = Get-RedisObservation
            $row.exists_after = $redisAfter.Exists; $row.pttl_after_ms = $redisAfter.TtlMs
            Assert-RedisObservation $redisAfter "after request $round/$index"
            if ($Mode -eq "hit") {
                if ($redisAfter.TtlMs -gt $redisBefore.TtlMs) { throw "HIT key TTL increased during request; it may have expired and been recreated." }
                $lastHitTtl = $redisAfter.TtlMs
            }
        }
        $roundAfter = Get-DbSelectCounter
        $roundRows.Add([pscustomobject]@{
            scenario = $Mode.ToUpperInvariant(); round = $round; n = $Runs
            queries_before = $roundBefore; queries_after = $roundAfter; global_select_delta = ($roundAfter - $roundBefore)
        })
        Save-PartialRequests
        Write-Host "$($Mode.ToUpperInvariant()) round=$round requests=$Runs Com_select=$roundBefore->$roundAfter key=$redisKey PTTL=$($redisAfter.TtlMs)ms"
        if ($roundAfter -lt $roundBefore) { throw "MySQL counter decreased; server may have restarted or counters were reset." }
        if ($Mode -in @("off", "miss") -and $roundAfter -eq $roundBefore) { throw "$Mode has no observed DB SELECT increase in round $round; check backend/database identity." }
    }

    $measurementEndedAt = Get-Date
    $dbSelectBefore = $roundRows[0].queries_before
    $dbSelectAfter = $roundRows[$roundRows.Count - 1].queries_after
    if ($Mode -eq "hit") { $ttlAfter = $lastHitTtl }

    $sortedLatencies = [double[]]@($requestRows | ForEach-Object { $_.latency_ms } | Sort-Object)
    $average = ($sortedLatencies | Measure-Object -Average).Average
    $median = Get-Median -SortedValues $sortedLatencies
    $p95 = Get-P95NearestRank -SortedValues $sortedLatencies
    $minimum = $sortedLatencies[0]
    $maximum = $sortedLatencies[-1]
    $dbSelectDelta = ($roundRows | Measure-Object -Property global_select_delta -Sum).Sum
    $gitAfter = Get-GitMetadata
    $sourceStable = ($gitMetadata.SourceWorkingTree -eq "clean" -and $gitAfter.SourceWorkingTree -eq "clean" -and $gitMetadata.Commit -eq $gitAfter.Commit -and $gitMetadata.Branch -eq $gitAfter.Branch -and $gitMetadata.TrackedDiffSha256 -eq $gitAfter.TrackedDiffSha256)
    if (-not $sourceStable) { Write-Warning "A clean, stable checkout was not confirmed. HEAD alone does not identify all source; this is not sealed submission evidence." }
    if ($Mode -eq "hit" -and $dbSelectDelta -ne 0) { Write-Warning "HIT global Com_select increased. It cannot distinguish Course/Category queries from unrelated traffic; investigate before claiming zero DB queries." }
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
        MeasuredRequests = $requestRows.Count
        Concurrency = 1
        Rounds = $Rounds
        RequestsPerRound = $Runs
        Measurement = [ordered]@{
            Tool = "System.Net.Http.HttpClient + System.Diagnostics.Stopwatch"
            Unit = "milliseconds"
            Boundary = "Before GetAsync until ReadAsStringAsync completes"
            ConnectionPolicy = "One HttpClient reused within each scenario"
            MedianMethod = "Middle sample; average of two middle samples for even N; pooled raw samples"
            PowerShell = $PSVersionTable.PSVersion.ToString()
            DotNet = Get-DotNetDescription
            ScriptSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $resultDirectory "benchmark.ps1")).Hash.ToLowerInvariant()
            Start = "Immediately before GetAsync(ResponseContentRead); the same buffering policy in all scenarios"
            Stop = "After ReadAsStringAsync completes (or transport/body-read failure), before response disposal/validation"
            Metric = "Client-observed HTTP GET latency including full response-body reading/decoding; milliseconds"
            Excluded = "Redis DEL/EXISTS/PTTL, Docker process time, DB counter queries, response validation and warm-up"
            NotEquivalentTo = "curl time_total, curl process wall time, or Service execution time"
            HttpClientLifetime = "One reused HttpClient per scenario process; redirects disabled; sequential requests"
            P95Method = "Nearest-rank ceil(0.95*n), calculated from all raw samples of this scenario"
        }
        OffVerification = $offVerification
        MetricsMs = [ordered]@{
            N = $requestRows.Count
            Average = [double]$average
            Median = [double]$median
            P95 = [double]$p95
            Min = [double]$minimum
            Max = [double]$maximum
        }
        Database = [ordered]@{
            Service = $DbService
            Counter = "SHOW GLOBAL STATUS WHERE Variable_name = 'Com_select'"
            Scope = "GLOBAL MySQL SELECT counter during each measured round"
            SelectBefore = $dbSelectBefore
            SelectAfter = $dbSelectAfter
            SelectDelta = $dbSelectDelta
            Limitation = "May include unrelated SELECT statements and SQL health checks; not an exact count of Course queries."
        }
        Cache = [ordered]@{
            RedisService = $RedisService
            CacheName = $CacheName
            RedisKey = $redisKey
            ExpectedTtlMinutes = $TtlMinutes
            MissContractPttlMs = $missContractTtl
            PttlMsAfterPrime = $ttlBefore
            PttlMsAfterRun = $ttlAfter
            Checks = "EXISTS/PTTL before and after each measured request; HIT additionally rejects increased TTL. Only the selected key is deleted."
            ModeDeclaration = "Mode declares the intended backend configuration. Retain actual startup/configuration evidence via STT01."
        }
        Repository = $gitMetadata
        RepositoryAfter = $gitAfter
        CleanStableSourceDuringRun = $sourceStable
        SourceLimitation = "Ignored files and runtime/source linkage are not captured; dirty/untracked bytes are not snapshotted. Keep STT01 before/after and actual startup/source evidence."
        Environment = $systemMetadata
        Files = [ordered]@{
            Requests = "requests.csv"
            RoundCounters = "rounds.csv"
            Script = "benchmark.ps1"
            OffVerification = if ($Mode -eq "off") { "off-verification.json" } else { $null }
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
        "| Method | HttpClient + Stopwatch, full response body |",
        "| Rounds x requests | $Rounds x $Runs |",
        "| Measured requests | $($requestRows.Count) |",
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
        "| MISS contract PTTL (ms) | $(ConvertTo-MarkdownValue $missContractTtl) |",
        "| PTTL after prime (ms) | $(ConvertTo-MarkdownValue $ttlBefore) |",
        "| PTTL after run (ms) | $(ConvertTo-MarkdownValue $ttlAfter) |",
        "| Clean, stable source | $sourceStable |",
        "",
        "> Timing: HttpClient + Stopwatch from send through full response-body reading. This is client HTTP latency, not curl time_total or Service-only time. Redis/Docker/counter operations are outside the timer.",
        "> Statistics pool all $($requestRows.Count) raw samples; p95 nearest-rank uses rank $([Math]::Ceiling(0.95 * $requestRows.Count)). Dispersion is min/max.",
        "> DB query note: Com_select is global, not a Course/Category-specific query count. Other traffic can affect the delta; Redis checks introduce gaps between sequential HTTP requests.",
        "> Environment: Java is host java -version only. Keep STT01 before/after snapshots and actual backend startup/configuration evidence. A dirty checkout is not identified by HEAD alone."
    )
    $summaryLines += @("", "## DB counters by round", "", "| Round | n | Before | After | Global SELECT delta |", "|---|---|---|---|---|")
    foreach ($r in $roundRows) {
        $summaryLines += "| $($r.round) | $($r.n) | $($r.queries_before) | $($r.queries_after) | $($r.global_select_delta) |"
    }
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
        RepositoryBefore = $gitMetadata
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
