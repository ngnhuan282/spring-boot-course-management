param(
    [int]$MySqlPort = 13309,
    [int]$RedisPort = 16379,
    [string]$EvidenceRoot = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$t9Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$t9Compose = Join-Path $PSScriptRoot "compose.task9.yml"
$t9Base = "3b18854be205af54a0cbaf80614d603839b3fb48"

function Invoke-T9Logged {
    param([string]$Program, [string[]]$ArgumentList, [string]$LogFile)
    $null = Get-Command $Program -ErrorAction Stop
    Set-Content -LiteralPath $LogFile -Value "" -Encoding UTF8
    $t9PreviousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell can wrap native stderr as NativeCommandError.
        # Preserve stderr and use the native process exit code for the result.
        $ErrorActionPreference = "Continue"
        & $Program @ArgumentList 2>&1 | ForEach-Object {
            $t9Line = $_.ToString()
            Write-Host $t9Line
            Add-Content -LiteralPath $LogFile -Value $t9Line -Encoding UTF8
        }
        $t9Code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $t9PreviousPreference
    }
    return [int]$t9Code
}

function Write-T9Json {
    param([string]$Path, [object]$Value)
    $Value | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Assert-T9PortFree {
    param([int]$Port)
    $t9Listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try {
        $t9Listener.Start()
    } catch {
        throw "Port $Port is busy. Choose another port with -MySqlPort or -RedisPort."
    } finally {
        $t9Listener.Stop()
    }
}

$t9Sha = (& git -C $t9Root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw "Not a Git working tree." }
$t9BeforeStatus = @(& git -C $t9Root status --porcelain=v1 --untracked-files=all)
if ($LASTEXITCODE -ne 0) { throw "Cannot read Git status." }
if ($t9BeforeStatus.Count -ne 0) {
    throw "Commit the Task 9 source files first. Run git status and keep this worktree clean."
}
& git -C $t9Root merge-base --is-ancestor $t9Base $t9Sha
if ($LASTEXITCODE -ne 0) { throw "This source does not contain the Task 7 baseline $t9Base." }
Assert-T9PortFree $MySqlPort
Assert-T9PortFree $RedisPort

if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $EvidenceRoot = Join-Path (Split-Path -Parent $t9Root) "task09-evidence"
}
$EvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot)
if ($EvidenceRoot.Equals($t9Root, [StringComparison]::OrdinalIgnoreCase) -or
    $EvidenceRoot.StartsWith($t9Root + [System.IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Use an evidence directory outside this Git working tree."
}
$t9RunId = Get-Date -Format "yyyyMMdd_HHmmss_fff"
$t9Project = "task9-$t9RunId"
$t9Run = Join-Path $EvidenceRoot "run-$t9RunId"
New-Item -ItemType Directory -Path $t9Run -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $t9Run "scenarios") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $t9Run "surefire") -Force | Out-Null

$t9EnvironmentPattern = '^(SPRING_|REDIS_|M08_|M11_|T9_|COURSE_CACHE_TTL$|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)'
$t9SavedEnvironment = @{}
Get-ChildItem Env: | Where-Object { $_.Name -match $t9EnvironmentPattern } | ForEach-Object {
    $t9SavedEnvironment[$_.Name] = $_.Value
    [Environment]::SetEnvironmentVariable($_.Name, $null, "Process")
}
$env:T9_MYSQL_PORT = [string]$MySqlPort
$env:T9_REDIS_PORT = [string]$RedisPort
$env:T9_REDIS_HOST = "127.0.0.1"
$env:T9_JDBC_URL = "jdbc:mysql://127.0.0.1:$MySqlPort/task09_cache?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
$env:T9_DB_USER = "task09_user"
$env:T9_DB_PASSWORD = "task09_local_password"
$env:T9_RUN_ID = $t9RunId
$env:T9_SOURCE_SHA = $t9Sha
$t9Dc = @("compose", "-p", $t9Project, "-f", $t9Compose)
$t9Result = "FAIL"
$t9Failure = ""
$t9MavenExit = -999
$t9JavaVersion = "not captured"
$t9NativeGit = "git"
$t9Metadata = [ordered]@{
    task = 9
    runId = $t9RunId
    startedAt = (Get-Date -Format o)
    timezone = [TimeZoneInfo]::Local.Id
    sourceSha = $t9Sha
    branch = ((& git -C $t9Root branch --show-current) -join "")
    task7Baseline = $t9Base
    gitCleanBefore = $true
    composeProject = $t9Project
    composeFile = "docs/task9/compose.task9.yml"
    mysqlPort = $MySqlPort
    redisPort = $RedisPort
    jdbcUrl = $env:T9_JDBC_URL
    database = "task09_cache"
    cache = "courseDetails"
    ttlSeconds = 600
    profile = "task09"
    execution = "SpringBootTest MOCK + MockMvc; real MySQL and Redis; test JVM on host"
}
Write-T9Json (Join-Path $t9Run "metadata-before.json") $t9Metadata

Push-Location $t9Root
try {
    $t9Code = Invoke-T9Logged "docker" @("version") (Join-Path $t9Run "docker-version.log")
    if ($t9Code -ne 0) { throw "Docker is not ready." }
    $t9Code = Invoke-T9Logged "docker" @("compose", "version") (Join-Path $t9Run "compose-version.log")
    if ($t9Code -ne 0) { throw "Docker Compose v2 is required." }
    $t9Maven = Join-Path $t9Root "backend/mvnw.cmd"
    $t9Code = Invoke-T9Logged $t9Maven @("-v") (Join-Path $t9Run "maven-version.log")
    if ($t9Code -ne 0) { throw "Maven Wrapper -v failed." }
    $t9VersionText = Get-Content (Join-Path $t9Run "maven-version.log") -Encoding UTF8 -Raw
    if ($t9VersionText -notmatch 'Java version:\s*(\d+)') { throw "Cannot identify Maven JVM." }
    if ([int]$Matches[1] -lt 21) { throw "Use JDK 21 or newer; this project compiles with release 21." }

    $t9SourceZip = Join-Path $t9Run "source.zip"
    $t9Code = Invoke-T9Logged $t9NativeGit @("-C", $t9Root, "archive", "--format=zip", "--output=$t9SourceZip", "HEAD") (Join-Path $t9Run "source-archive.log")
    if ($t9Code -ne 0) { throw "Cannot archive the committed source." }
    @(& git -C $t9Root ls-files) | ForEach-Object {
        $t9Relative = $_
        $t9FullPath = Join-Path $t9Root $t9Relative
        if (Test-Path -LiteralPath $t9FullPath -PathType Leaf) {
            $t9Hash = Get-FileHash -Algorithm SHA256 -LiteralPath $t9FullPath
            [pscustomobject]@{ Path = $t9Relative; SHA256 = $t9Hash.Hash }
        }
    } | Export-Csv (Join-Path $t9Run "source-hashes.csv") -NoTypeInformation -Encoding UTF8

    $t9Code = Invoke-T9Logged "docker" ($t9Dc + @("up", "-d", "--wait", "--wait-timeout", "300")) (Join-Path $t9Run "compose-up.log")
    if ($t9Code -ne 0) { throw "MySQL/Redis did not become healthy." }
    $t9Code = Invoke-T9Logged "docker" ($t9Dc + @("ps")) (Join-Path $t9Run "services-before-test.log")
    if ($t9Code -ne 0) { throw "Cannot inspect services." }
    $t9ContainerIds = @(& docker @t9Dc ps -q)
    $t9Code = Invoke-T9Logged "docker" (@("inspect") + $t9ContainerIds) (Join-Path $t9Run "containers.json")
    if ($t9Code -ne 0) { throw "Cannot inspect the experiment containers." }
    $t9Code = Invoke-T9Logged "docker" ($t9Dc + @("exec", "-T", "redis", "redis-cli", "INFO", "server")) (Join-Path $t9Run "redis-server.log")
    if ($t9Code -ne 0) { throw "Cannot read Redis server info." }

    $t9Scenarios = Join-Path $t9Run "scenarios"
    $t9Arguments = @(
        "-Dtest=Task09MySqlCacheTests",
        "-DfailIfNoTests=true",
        "-Djunit.jupiter.execution.parallel.enabled=false",
        "-Dtask9.evidence.directory=$t9Scenarios",
        "clean", "test"
    )
    Write-T9Json (Join-Path $t9Run "command.json") ([ordered]@{
        program = ".\mvnw.cmd"; workingDirectory = "backend"; arguments = $t9Arguments
    })
    $t9CommandText = ".\mvnw.cmd " + (($t9Arguments | ForEach-Object { '"' + $_ + '"' }) -join " ")
    Set-Content (Join-Path $t9Run "command.txt") $t9CommandText -Encoding UTF8
    Write-Host "TASK 9 COMMAND: $t9CommandText"
    Push-Location (Join-Path $t9Root "backend")
    try {
        $t9MavenExit = Invoke-T9Logged ".\mvnw.cmd" $t9Arguments (Join-Path $t9Run "maven-test.log")
    } finally { Pop-Location }
    Set-Content (Join-Path $t9Run "maven-exit-code.txt") $t9MavenExit -Encoding UTF8

    $t9XmlName = "TEST-com.ccnlthd.course_management.Task09MySqlCacheTests.xml"
    $t9XmlPath = Join-Path $t9Root "backend/target/surefire-reports/$t9XmlName"
    $t9TextPath = Join-Path $t9Root "backend/target/surefire-reports/com.ccnlthd.course_management.Task09MySqlCacheTests.txt"
    foreach ($t9Report in @($t9XmlPath, $t9TextPath)) {
        if (Test-Path $t9Report) { Copy-Item $t9Report (Join-Path $t9Run "surefire") }
    }
    if ($t9MavenExit -ne 0) { throw "Tests failed; review maven-test.log and copied Surefire reports." }
    if (-not (Test-Path $t9XmlPath)) { throw "Missing Surefire XML." }
    [xml]$t9Xml = Get-Content $t9XmlPath -Encoding UTF8 -Raw
    $t9Suite = $t9Xml.testsuite
    if ([int]$t9Suite.tests -ne 6 -or [int]$t9Suite.failures -ne 0 -or
        [int]$t9Suite.errors -ne 0 -or [int]$t9Suite.skipped -ne 0) {
        throw "Expected exactly six passing tests, with no skipped test."
    }
    $t9JavaVersion = ($t9Suite.properties.property | Where-Object { $_.name -eq "java.version" }).value
    $t9ExpectedScenarios = @("miss-hit", "course-update-delete", "category-rename-two-courses", "category-rollback", "category-delete-conflict", "course-rollback")
    foreach ($t9Scenario in $t9ExpectedScenarios) {
        $t9JsonPath = Join-Path $t9Scenarios "$t9Scenario.json"
        if (-not (Test-Path $t9JsonPath)) { throw "Missing scenario: $t9Scenario" }
        $t9Document = Get-Content $t9JsonPath -Encoding UTF8 -Raw | ConvertFrom-Json
        if ($t9Document.environment.database -ne "MySQL" -or
            $t9Document.environment.source_sha -ne $t9Sha -or
            $t9Document.environment.run_id -ne $t9RunId) {
            throw "Scenario environment/source mismatch: $t9Scenario"
        }
    }

    $t9Sql = "SELECT VERSION() AS server_version, DATABASE() AS database_name, @@hostname AS server_hostname; SELECT * FROM flyway_schema_history ORDER BY installed_rank; SELECT id,name,description FROM categories ORDER BY id; SELECT id,category_id,title,price,level,status FROM courses ORDER BY id;"
    $t9Code = Invoke-T9Logged "docker" ($t9Dc + @("exec", "-T", "-e", "MYSQL_PWD=task09_local_password", "mysql", "mysql", "--user=task09_user", "--database=task09_cache", "--batch", "--raw", "--execute=$t9Sql")) (Join-Path $t9Run "database-final.tsv")
    if ($t9Code -ne 0) { throw "Cannot save final MySQL snapshot." }
    $t9Code = Invoke-T9Logged "docker" ($t9Dc + @("exec", "-T", "redis", "redis-cli", "--scan", "--pattern", "courseDetails::*")) (Join-Path $t9Run "redis-keys-final.log")
    if ($t9Code -ne 0) { throw "Cannot save final Redis key inventory." }
    $t9Result = "PASS"
} catch {
    $t9Failure = $_.Exception.Message
    Write-Host "TASK 9 RESULT: FAIL - $t9Failure" -ForegroundColor Red
} finally {
    try {
    try {
        $null = Invoke-T9Logged "docker" ($t9Dc + @("ps")) (Join-Path $t9Run "services-after-test.log")
        $null = Invoke-T9Logged "docker" ($t9Dc + @("logs", "--no-color")) (Join-Path $t9Run "containers.log")
    } catch { Write-Host "Additional Docker logs were unavailable: $($_.Exception.Message)" }
    $t9AfterSha = (& git -C $t9Root rev-parse HEAD).Trim()
    $t9AfterStatus = @(& git -C $t9Root status --porcelain=v1 --untracked-files=all)
    $t9SourceUnchanged = ($t9AfterSha -eq $t9Sha -and $t9AfterStatus.Count -eq 0)
    if (-not $t9SourceUnchanged) {
        $t9Result = "FAIL"
        $t9Failure = $t9Failure + " Source changed or worktree became dirty during the run."
    }
    $t9Metadata["finishedAt"] = Get-Date -Format o
    $t9Metadata["sourceShaAfter"] = $t9AfterSha
    $t9Metadata["gitCleanAfter"] = ($t9AfterStatus.Count -eq 0)
    $t9Metadata["sourceUnchanged"] = $t9SourceUnchanged
    $t9Metadata["javaVersionFromSurefire"] = $t9JavaVersion
    $t9Metadata["mavenExitCode"] = $t9MavenExit
    $t9Metadata["result"] = $t9Result
    $t9Metadata["failure"] = $t9Failure
    Write-T9Json (Join-Path $t9Run "metadata-after.json") $t9Metadata
    Get-ChildItem $t9Run -Recurse -File | Where-Object { $_.Name -ne "manifest-sha256.csv" } | ForEach-Object {
        [pscustomobject]@{
            Path = $_.FullName.Substring($t9Run.Length + 1).Replace("\", "/")
            Bytes = $_.Length
            SHA256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
        }
    } | Export-Csv (Join-Path $t9Run "manifest-sha256.csv") -NoTypeInformation -Encoding UTF8
    Compress-Archive -Path (Join-Path $t9Run "*") -DestinationPath "$t9Run.zip" -Force
    $t9ArchiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath "$t9Run.zip").Hash
    Set-Content "$t9Run.zip.sha256.txt" $t9ArchiveHash -Encoding UTF8
    } finally {
    Get-ChildItem Env: | Where-Object { $_.Name -match $t9EnvironmentPattern } | ForEach-Object {
        [Environment]::SetEnvironmentVariable($_.Name, $null, "Process")
    }
    foreach ($t9Name in $t9SavedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($t9Name, $t9SavedEnvironment[$t9Name], "Process")
    }
    Pop-Location
    }
}

Write-Host "TASK 9 RESULT: $t9Result"
Write-Host "SOURCE: $t9Sha"
Write-Host "TEST JVM: $t9JavaVersion"
Write-Host "PROJECT: $t9Project"
Write-Host "EVIDENCE: $t9Run"
Write-Host "ZIP: $t9Run.zip"
Write-Host "Containers are kept for inspection. Use this project name for later commands."
if ($t9Result -ne "PASS") { throw $t9Failure }
