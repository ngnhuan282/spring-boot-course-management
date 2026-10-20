param(
    [ValidateSet("Baseline", "Final")][string]$Mode = "Final",
    [int]$MySqlPort = 13310,
    [int]$RedisPort = 16380,
    [string]$EvidenceRoot = "",
    [switch]$KeepServices
)
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$t10Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$t10Compose = Join-Path $PSScriptRoot "compose.task10.yml"

function Write-T10Json {
    param([string]$Path, [object]$Value)
    $Value | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $Path -Encoding UTF8
}
function Invoke-T10Logged {
    param([string]$Program, [string[]]$ArgumentList, [string]$LogFile)
    $null = Get-Command $Program -ErrorAction Stop
    Set-Content -LiteralPath $LogFile -Value "" -Encoding UTF8
    $t10PreviousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 wraps native stderr; preserve it and check exit code.
        $ErrorActionPreference = "Continue"
        & $Program @ArgumentList 2>&1 | ForEach-Object {
            $t10Line = $_.ToString()
            Write-Host $t10Line
            Add-Content -LiteralPath $LogFile -Value $t10Line -Encoding UTF8
        }
        $t10Code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $t10PreviousPreference
    }
    return [int]$t10Code
}
function Assert-T10PortFree {
    param([int]$Port)
    $t10Listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, $Port)
    try { $t10Listener.Start() }
    catch { throw "Port $Port is busy; select another port in the script arguments." }
    finally { $t10Listener.Stop() }
}

Push-Location $t10Root
try {
    $t10Sha = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Not a Git working tree." }
    $t10Status = @(& git status --porcelain=v1 --untracked-files=all)
    if ($LASTEXITCODE -ne 0 -or $t10Status.Count -ne 0) {
        throw "Commit the Task 10 files first; this worktree must be clean."
    }
    if (-not (Test-Path "backend/src/test/java/com/ccnlthd/course_management/Task09MySqlCacheTests.java")) {
        throw "Start this branch from your Task 9 source, with the Task 9 tests retained."
    }
    Assert-T10PortFree $MySqlPort
    Assert-T10PortFree $RedisPort
    if ($MySqlPort -eq $RedisPort) { throw "MySQL and Redis need distinct ports." }
    if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
        $EvidenceRoot = Join-Path (Split-Path -Parent $t10Root) "task10-evidence"
    }
    $EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
    if ($EvidenceRoot.Equals($t10Root, [StringComparison]::OrdinalIgnoreCase) -or
        $EvidenceRoot.StartsWith($t10Root + [System.IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "EvidenceRoot must be outside this Git worktree."
    }
    $t10RunId = Get-Date -Format "yyyyMMdd_HHmmss_fff"
    $t10Project = "task10-$($Mode.ToLowerInvariant())-$t10RunId"
    $t10Run = Join-Path $EvidenceRoot "run-$($Mode.ToLowerInvariant())-$t10RunId"
    New-Item -ItemType Directory -Path $t10Run | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $t10Run "scenarios") | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $t10Run "surefire") | Out-Null
} finally { Pop-Location }

$t10Pattern = '^(SPRING_|REDIS_|M08_|M11_|T9_|T10_|COURSE_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$|COMPOSE_)'
$t10Saved = @{}
Get-ChildItem Env: | Where-Object { $_.Name -match $t10Pattern } | ForEach-Object {
    $t10Saved[$_.Name] = $_.Value
    [Environment]::SetEnvironmentVariable($_.Name, $null, "Process")
}
$env:T10_MYSQL_PORT = [string]$MySqlPort
$env:T10_REDIS_HOST = "127.0.0.1"
$env:T10_REDIS_PORT = [string]$RedisPort
$env:T10_JDBC_URL = "jdbc:mysql://127.0.0.1:$MySqlPort/task10_concurrency?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
$env:T10_DB_USER = "task10_user"
$env:T10_DB_PASSWORD = "task10_local_password"
$env:T10_RUN_ID = $t10RunId
$env:T10_SOURCE_SHA = $t10Sha
$env:T10_EVIDENCE_DIR = Join-Path $t10Run "scenarios"
$env:T10_GUARD_ENABLED = if ($Mode -eq "Final") { "true" } else { "false" }
$t10Dc = @("compose", "-p", $t10Project, "-f", $t10Compose)
$t10Result = "FAIL"
$t10Failure = ""
$t10MavenExit = -999
$t10Java = "not captured"
$t10Summary = $null
$t10Meta = [ordered]@{
    task = 10; mode = $Mode; runId = $t10RunId; sourceSha = $t10Sha
    branch = ((& git -C $t10Root branch --show-current) -join "")
    startedAt = (Get-Date -Format o); timezone = [TimeZoneInfo]::Local.Id
    gitCleanBefore = $true; composeProject = $t10Project
    composeFile = "docs/task10/compose.task10.yml"
    mysqlPort = $MySqlPort; redisPort = $RedisPort
    jdbcUrl = $env:T10_JDBC_URL; database = "task10_concurrency"
    profile = "task10"; guardEnabled = ($Mode -eq "Final")
    policy = "serialized writes; last applied committed PUT wins; one backend JVM"
    execution = "SpringBootTest MOCK + MockMvc; real MySQL/Redis; test JVM on host"
    ttlSeconds = 600; servicesRetained = [bool]$KeepServices
}
Write-T10Json (Join-Path $t10Run "metadata-before.json") $t10Meta
Push-Location $t10Root
try {
    if ((Invoke-T10Logged "docker" @("version") (Join-Path $t10Run "docker-version.log")) -ne 0) {
        throw "Docker Desktop is not ready."
    }
    if ((Invoke-T10Logged "docker" @("compose", "version") (Join-Path $t10Run "compose-version.log")) -ne 0) {
        throw "Docker Compose v2 is required."
    }
    Push-Location (Join-Path $t10Root "backend")
    try {
        $t10Code = Invoke-T10Logged ".\mvnw.cmd" @("-v") (Join-Path $t10Run "maven-version.log")
    } finally { Pop-Location }
    if ($t10Code -ne 0) { throw "Maven Wrapper -v failed." }
    $t10Versions = Get-Content (Join-Path $t10Run "maven-version.log") -Raw -Encoding UTF8
    if ($t10Versions -notmatch 'Java version:\s*(\d+)') { throw "Cannot identify Maven JVM." }
    if ([int]$Matches[1] -lt 21) { throw "Use a JDK >=21. Backend compilation target is Java 21." }
    $t10Archive = Join-Path $t10Run "git-source.zip"
    $t10Code = Invoke-T10Logged "git" @("-C", $t10Root, "archive",
        "--format=zip", "--output=$t10Archive", "HEAD") (Join-Path $t10Run "source-archive.log")
    if ($t10Code -ne 0) { throw "Source archive failed." }
    # Keep exact executed working-tree bytes, including possible CRLF conversion.
    # git-source.zip is the separate committed Git representation.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $t10Stage = Join-Path ([System.IO.Path]::GetTempPath()) ("task10-source-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $t10Stage | Out-Null
    try {
        $t10Tracked = @(& git -c core.quotepath=false ls-files)
        if ($LASTEXITCODE -ne 0) { throw "Cannot enumerate tracked source files." }
        $t10Tracked | ForEach-Object {
            $t10Relative = $_
            $t10File = Join-Path $t10Root $t10Relative
            if (Test-Path -LiteralPath $t10File -PathType Leaf) {
                $t10Copy = Join-Path $t10Stage $t10Relative
                New-Item -ItemType Directory -Path (Split-Path -Parent $t10Copy) -Force | Out-Null
                [System.IO.File]::Copy($t10File, $t10Copy)
                [pscustomobject]@{ Path = $t10Relative; SHA256 = (Get-FileHash -LiteralPath $t10Copy -Algorithm SHA256).Hash }
            } else {
                throw "Cannot snapshot tracked file: $t10Relative"
            }
        } | Export-Csv (Join-Path $t10Run "source-hashes.csv") -NoTypeInformation -Encoding UTF8
        [System.IO.Compression.ZipFile]::CreateFromDirectory(
            $t10Stage, (Join-Path $t10Run "source.zip"))
    } finally { Remove-Item -LiteralPath $t10Stage -Recurse -Force }
    $t10Code = Invoke-T10Logged "docker" ($t10Dc + @("up", "-d", "--wait",
        "--wait-timeout", "300")) (Join-Path $t10Run "compose-up.log")
    if ($t10Code -ne 0) { throw "MySQL/Redis failed to become healthy." }
    $t10Code = Invoke-T10Logged "docker" ($t10Dc + @("exec", "-T", "redis", "redis-cli",
        "INFO", "server")) (Join-Path $t10Run "redis-server.log")
    if ($t10Code -ne 0) { throw "Redis version capture failed." }
    $t10Ids = @(& docker @t10Dc ps -q)
    if ($LASTEXITCODE -ne 0 -or $t10Ids.Count -ne 2) { throw "Expected two experiment containers." }
    $t10Code = Invoke-T10Logged "docker" (@("inspect") + $t10Ids) (Join-Path $t10Run "containers.json")
    if ($t10Code -ne 0) { throw "Container inspection failed." }
    $t10Arguments = @("-Dtest=Task10ConcurrentCacheTests", "-DfailIfNoTests=true",
        "-Djunit.jupiter.execution.parallel.enabled=false", "clean", "test")
    Write-T10Json (Join-Path $t10Run "command.json") ([ordered]@{
        program = ".\mvnw.cmd"; workingDirectory = "backend"; arguments = $t10Arguments
        taskEnvironment = @{
            T10_RUN_ID = $env:T10_RUN_ID; T10_SOURCE_SHA = $env:T10_SOURCE_SHA
            T10_GUARD_ENABLED = $env:T10_GUARD_ENABLED; T10_JDBC_URL = $env:T10_JDBC_URL
            T10_REDIS_HOST = $env:T10_REDIS_HOST; T10_REDIS_PORT = $env:T10_REDIS_PORT
            T10_DB_USER = $env:T10_DB_USER; T10_DB_PASSWORD = $env:T10_DB_PASSWORD
            T10_EVIDENCE_DIR = $env:T10_EVIDENCE_DIR
        }
    })
    Push-Location (Join-Path $t10Root "backend")
    try {
        $t10MavenExit = Invoke-T10Logged ".\mvnw.cmd" $t10Arguments (Join-Path $t10Run "maven-test.log")
    } finally { Pop-Location }
    Set-Content (Join-Path $t10Run "maven-exit-code.txt") $t10MavenExit -Encoding UTF8
    $t10XmlName = "TEST-com.ccnlthd.course_management.Task10ConcurrentCacheTests.xml"
    $t10XmlPath = Join-Path $t10Root "backend/target/surefire-reports/$t10XmlName"
    Get-ChildItem (Join-Path $t10Root "backend/target/surefire-reports") -File |
        Copy-Item -Destination (Join-Path $t10Run "surefire")
    if (-not (Test-Path $t10XmlPath)) { throw "Missing Task 10 Surefire XML." }
    [xml]$t10Xml = Get-Content $t10XmlPath -Raw -Encoding UTF8
    $t10Suite = $t10Xml.testsuite
    $t10Java = ($t10Suite.properties.property | Where-Object { $_.name -eq "java.version" }).value
    $t10Summary = [ordered]@{
        tests = [int]$t10Suite.tests; failures = [int]$t10Suite.failures
        errors = [int]$t10Suite.errors; skipped = [int]$t10Suite.skipped
    }
    Write-T10Json (Join-Path $t10Run "surefire-summary.json") $t10Summary
    if ($t10Summary.tests -ne 3 -or $t10Summary.errors -ne 0 -or $t10Summary.skipped -ne 0) {
        throw "Expected exactly three executed tests and no errors/skips; inspect raw XML."
    }
    $t10Cases = @("twoPutsSameCourseLastAppliedCommitWins",
        "categoryRenameAgainstInFlightMissKeepsBothCoursesConsistent",
        "courseUpdateAgainstInFlightMissDoesNotPublishOldTitle")
    foreach ($t10Case in $t10Cases) {
        $t10CaseFile = Join-Path $t10Run "scenarios/$t10Case/scenario.json"
        if (-not (Test-Path $t10CaseFile)) { throw "Missing scenario $t10Case" }
        $t10Doc = Get-Content $t10CaseFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($t10Doc.run_id -ne $t10RunId -or $t10Doc.source_sha -ne $t10Sha -or
            $t10Doc.guard_enabled -ne ($Mode -eq "Final")) {
            throw "Scenario identity/configuration mismatch: $t10Case"
        }
        if ($Mode -eq "Final" -and -not $t10Doc.passed) { throw "Scenario did not pass: $t10Case" }
    }
    $t10Sql = "SELECT VERSION() AS server_version,DATABASE() AS database_name; SELECT * FROM flyway_schema_history ORDER BY installed_rank; SELECT id,name,description FROM categories ORDER BY id; SELECT id,category_id,title,description,price,level,status FROM courses ORDER BY id;"
    $t10Code = Invoke-T10Logged "docker" ($t10Dc + @("exec", "-T",
        "-e", "MYSQL_PWD=task10_local_password", "mysql", "mysql", "--user=task10_user",
        "--database=task10_concurrency", "--batch", "--raw", "--execute=$t10Sql")) (Join-Path $t10Run "database-final.tsv")
    if ($t10Code -ne 0) { throw "Final DB snapshot failed." }
    if ($Mode -eq "Final") {
        if ($t10MavenExit -ne 0 -or $t10Summary.failures -ne 0) { throw "Final tests failed." }
        $t10Result = "PASS"
    } elseif ($t10MavenExit -ne 0 -and $t10Summary.failures -eq 2) {
        $t10FailedNames = @($t10Suite.testcase | Where-Object {
            $null -ne $_.SelectSingleNode("failure")
        } | ForEach-Object { [string]$_.name })
        if (@($t10FailedNames | Where-Object {
            $_ -notin $t10Cases[1..2]
        }).Count -ne 0) { throw "Unexpected failing test in baseline comparison." }
        $t10Result = "COMPARISON_FAILURE_OBSERVED"
    } else {
        throw "Baseline did not show the expected two stale-cache assertion failures. Inspect results."
    }
} catch {
    $t10Failure = $_.Exception.Message
    $t10Result = "FAIL"
    Write-Host "TASK 10: $t10Failure" -ForegroundColor Red
} finally {
    try {
        try {
            $null = Invoke-T10Logged "docker" ($t10Dc + @("ps")) (Join-Path $t10Run "services.log")
            $null = Invoke-T10Logged "docker" ($t10Dc + @("logs", "--no-color")) (Join-Path $t10Run "containers.log")
            if (-not $KeepServices) {
                $t10Down = Invoke-T10Logged "docker" ($t10Dc + @("down")) (Join-Path $t10Run "compose-down.log")
                $t10Meta["servicesStopped"] = ($t10Down -eq 0)
            }
        } catch { Write-Host "Additional Docker logs unavailable: $($_.Exception.Message)" }
        $t10AfterSha = (& git rev-parse HEAD).Trim()
        $t10AfterStatus = @(& git status --porcelain=v1 --untracked-files=all)
        $t10Unchanged = ($t10AfterSha -eq $t10Sha -and $t10AfterStatus.Count -eq 0)
        if (-not $t10Unchanged) { $t10Result = "FAIL"; $t10Failure += " Source changed during the run." }
        $t10Meta["finishedAt"] = Get-Date -Format o
        $t10Meta["sourceShaAfter"] = $t10AfterSha
        $t10Meta["gitCleanAfter"] = ($t10AfterStatus.Count -eq 0)
        $t10Meta["sourceUnchanged"] = $t10Unchanged
        $t10Meta["javaVersionFromSurefire"] = $t10Java
        $t10Meta["mavenExitCode"] = $t10MavenExit
        $t10Meta["surefire"] = $t10Summary
        $t10Meta["result"] = $t10Result
        $t10Meta["failure"] = $t10Failure
        Write-T10Json (Join-Path $t10Run "metadata-after.json") $t10Meta
        Get-ChildItem $t10Run -Recurse -File | Where-Object { $_.Name -ne "manifest-sha256.csv" } |
            ForEach-Object {
                [pscustomobject]@{
                    Path = $_.FullName.Substring($t10Run.Length + 1).Replace("\", "/")
                    Bytes = $_.Length
                    SHA256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
                }
            } | Export-Csv (Join-Path $t10Run "manifest-sha256.csv") -NoTypeInformation -Encoding UTF8
        Compress-Archive -Path (Join-Path $t10Run "*") -DestinationPath "$t10Run.zip"
        (Get-FileHash -LiteralPath "$t10Run.zip" -Algorithm SHA256).Hash |
            Set-Content "$t10Run.zip.sha256.txt" -Encoding UTF8
    } finally {
        Get-ChildItem Env: | Where-Object { $_.Name -match $t10Pattern } | ForEach-Object {
            [Environment]::SetEnvironmentVariable($_.Name, $null, "Process")
        }
        foreach ($t10Name in $t10Saved.Keys) {
            [Environment]::SetEnvironmentVariable($t10Name, $t10Saved[$t10Name], "Process")
        }
        Pop-Location
    }
}
Write-Host "TASK 10 RESULT: $t10Result"
Write-Host "SOURCE: $t10Sha"
Write-Host "TEST JVM: $t10Java"
Write-Host "PROJECT: $t10Project"
Write-Host "EVIDENCE: $t10Run"
Write-Host "ZIP: $t10Run.zip"
if ($t10Result -eq "FAIL") { exit 1 }
exit 0
