# Week 4 Bug Log

## Chapter 13.2 candidate - Benchmark native-command portability

Status: `RESOLVED_POST_PHASE_B2`.

### Symptom

The benchmark script at the Phase B2 measurement commit treated valid native stderr as a terminating PowerShell error because the script-wide `$ErrorActionPreference` was `Stop`:

- `java -version` writes its valid version string to stderr. This produced `scripts/cache-performance/results/2026-10-04_165353_off/failure.json` with zero measured requests.
- MySQL writes its password warning to stderr before returning the valid read-only counter. This produced `scripts/cache-performance/results/2026-10-04_165532_off/failure.json` with zero measured requests.
- The first direct post-Phase-B2 smoke exposed a second Windows portability issue: nested quotes in the `sh -c` SQL command were lost at the native-process boundary. The resulting SQL syntax failure is retained at `scripts/cache-performance/results/tooling-smoke-2026-10-04_191900/2026-10-04_191944_off/failure.json`.

These are real benchmark tooling defects, not Course API, database data or Redis cache behavior defects. The official OFF/MISS/HIT results remain tied to measurement commit `2aa11d2aff81dab06dc0804016caf4f9e4425b28` and were not rerun or replaced during this fix.

### Root cause

The script invoked native commands directly while `$ErrorActionPreference = 'Stop'`. PowerShell therefore promoted native stderr records to terminating errors even when the process exit code was `0`. The database counter command also relied on nested shell quotes that did not survive direct Windows PowerShell native argument construction consistently.

Before:

```powershell
$output = & docker @Arguments 2>&1
$output = & $Command 2>&1
$dbCommand = 'mysql ... -e "SHOW GLOBAL STATUS LIKE ''Com_select'';"'
```

### Correction

`Invoke-NativeCommand` now temporarily makes native stderr non-terminating, captures combined output and `$LASTEXITCODE`, restores `$ErrorActionPreference` in `finally`, and returns output only when the native exit code is `0`. A non-zero or unavailable exit code still throws with command context and the exit code.

Git, `docker version`, Docker Compose and `java -version` now use the same helper. The database counter command uses a shell-safe hexadecimal literal for `Com_select`; `Get-DbSelectCounter` still accepts only the parsed `Com_select` result line, so a MySQL warning cannot become the counter or expose the password.

### Verification

- PowerShell parser: passed.
- Direct smoke without a process-local adapter: OFF, MISS and HIT all passed with Course ID `1`, warm-up `1`, runs `3` and concurrency `1` under `scripts/cache-performance/results/tooling-smoke-2026-10-04_191900/`.
- Successful smoke summaries: `2026-10-04_192114_off/summary.json`, `2026-10-04_192210_miss/summary.json` and `2026-10-04_192215_hit/summary.json` under that smoke root.
- The smoke run is tooling verification only and is not performance evidence.
- Non-zero behavior: an isolated child process with an invalid `DOCKER_HOST` exited `1` and wrote `scripts/cache-performance/results/tooling-nonzero-2026-10-04_191833/2026-10-04_191833_off/failure.json` containing the Docker Compose exit-code failure.
- Both smoke backend logs contained zero Hibernate SQL/DEBUG matches after removing `DEBUG` and setting `SPRING_JPA_SHOW_SQL=false`.
- The official Phase B2 result files and comparison were not modified.

## Product regression status

No qualifying real application/cache product bug was found during Phase A, Phase B1 or Phase B2.

Final integration regression completed with `71/71` tests passing, including `6/6` cache integration tests. The OFF/MISS/HIT payload and invariant checks passed, and the isolated Course demo was reproducible. No production fix was required.

The Maven Central access denial in the restricted tool sandbox, unavailable Docker daemon during the historical Phase A attempt and the failed process-local adapter scope attempt at `2026-10-04_165856_off` are environment/operator conditions, not product bugs. The resolved Chapter 13.2 candidate above is a benchmark tooling bug and is not presented as an application/cache product bug.

No bug entry has been invented to fill the report.
