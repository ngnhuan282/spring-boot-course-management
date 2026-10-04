# Cache Benchmark Summary

| Metric | Value |
|---|---|
| Status | SUCCESS |
| Mode | HIT |
| Endpoint | http://localhost:8080/api/courses/1 |
| Course ID | 1 |
| Course payload SHA-256 | f66005bb73b459e336348f33aa2368d853992d17d396aeb05af6ab121cef1f64 |
| Warm-up | 20 |
| Measured requests | 100 |
| Concurrency | 1 |
| Average (ms) | 9.854 |
| Median (ms) | 8.883 |
| P95 nearest-rank (ms) | 15.909 |
| Min (ms) | 6.554 |
| Max (ms) | 34.845 |
| DB SELECT before | 615 |
| DB SELECT after | 616 |
| DB SELECT delta | 1 |
| Git branch | feat/week4-cache-coverage-regression |
| Git commit | 2aa11d2aff81dab06dc0804016caf4f9e4425b28 |
| Raw working tree | dirty |
| Source working tree | clean |
| Java | java version "22.0.2" 2024-07-16 |
| Spring Boot | 4.1.1 |
| OS | Microsoft Windows 10.0.26200  |
| CPU | Intel64 Family 6 Model 186 Stepping 2, GenuineIntel |
| RAM bytes | 16868974592 |
| Docker | 28.4.0\|28.4.0 |
| Redis key | courseDetails::1 |
| MISS contract TTL (seconds) | UNAVAILABLE |
| TTL after prime (seconds) | 600 |
| TTL after run (seconds) | 597 |

> DB query note: Com_select is a global MySQL counter. Other database traffic during the measured workload can affect the delta.
