# Week 4 Bug Log

## Benchmark tooling issue found in Phase B2

The committed PowerShell benchmark treats native stderr as a terminating error under `$ErrorActionPreference = 'Stop'`. On this Windows environment:

- `java -version` writes its valid version string to stderr, producing `2026-10-04_165353_off/failure.json` with zero measured requests.
- MySQL emits its password warning to stderr before returning the valid read-only counter, producing `2026-10-04_165532_off/failure.json` with zero measured requests.

This is a real benchmark execution/portability issue, not a Course API or cache behavior defect. The official runs used a process-local PowerShell adapter that preserved native exit codes, normalized native stderr and executed the intended read-only `Com_select` query. No repository script, production source, test or configuration was changed during measurement.

Status: `OPEN_TOOLING_FIX_AFTER_MEASUREMENT`. A source correction should be reviewed separately because Phase B2 required the benchmark script to remain byte-for-byte unchanged from measurement commit `2aa11d2aff81dab06dc0804016caf4f9e4425b28`.

## Product regression status

No qualifying real application/cache product bug was found during Phase A, Phase B1 or Phase B2.

Final integration regression completed with `71/71` tests passing, including `6/6` cache integration tests. The OFF/MISS/HIT payload and invariant checks passed, and the isolated Course demo was reproducible. No production fix was required.

The Maven Central access denial in the restricted tool sandbox, unavailable Docker daemon during the historical Phase A attempt and the failed process-local adapter scope attempt at `2026-10-04_165856_off` are environment/operator conditions, not product bugs.

No bug entry has been invented to fill the report.
