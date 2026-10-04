# Cache Benchmark Summary

| Metric | Value |
|---|---|
| Status | SUCCESS |
| Mode | MISS |
| Endpoint | http://localhost:8080/api/courses/1 |
| Course ID | 1 |
| Course payload SHA-256 | f66005bb73b459e336348f33aa2368d853992d17d396aeb05af6ab121cef1f64 |
| Warm-up | 20 |
| Measured requests | 100 |
| Concurrency | 1 |
| Average (ms) | 35.962 |
| Median (ms) | 29.204 |
| P95 nearest-rank (ms) | 86.971 |
| Min (ms) | 12.392 |
| Max (ms) | 138.636 |
| DB SELECT before | 411 |
| DB SELECT after | 612 |
| DB SELECT delta | 201 |
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
| MISS contract TTL (seconds) | 600 |
| TTL after prime (seconds) | UNAVAILABLE |
| TTL after run (seconds) | UNAVAILABLE |

> DB query note: Com_select is a global MySQL counter. Other database traffic during the measured workload can affect the delta.
