# Week 4 AI Issue Log

## Issue 1 - MISS benchmark lacked cache-contract verification

The first benchmark draft deleted `courseDetails::<id>` before a request but did not prove that the request created that Redis key. A disabled cache or a different cache-name/key contract could therefore be mislabeled as MISS while every request still reached the database.

This was found during benchmark logic review. The corrected preflight is:

```text
DEL key -> GET Course -> EXISTS == 1 -> TTL > 0 -> DEL key -> MISS workload
```

The run now fails with `failure.json` and no successful summary when the contract is not satisfied.

## Issue 2 - Benchmark output polluted Git metadata

An earlier script version created the current result directory before calling `Get-GitMetadata`. A clean repository could therefore be reported as dirty only because the benchmark had created its own output.

This was found by reviewing operation order. The script now captures metadata before creating the current result directory. Phase B also records both `RawWorkingTree` and `SourceWorkingTree`; the latter excludes only prior benchmark result folders so consecutive OFF/MISS/HIT runs can prove that source stayed unchanged.
