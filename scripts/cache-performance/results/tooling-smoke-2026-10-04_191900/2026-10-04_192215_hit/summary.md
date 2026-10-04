# Cache Benchmark Summary

| Metric | Value |
|---|---|
| Status | SUCCESS |
| Mode | HIT |
| Endpoint | http://localhost:8080/api/courses/1 |
| Course ID | 1 |
| Course payload SHA-256 | f66005bb73b459e336348f33aa2368d853992d17d396aeb05af6ab121cef1f64 |
| Warm-up | 1 |
| Measured requests | 3 |
| Concurrency | 1 |
| Average (ms) | 5.132 |
| Median (ms) | 5.158 |
| P95 nearest-rank (ms) | 6.11 |
| Min (ms) | 4.126 |
| Max (ms) | 6.11 |
| DB SELECT before | 773 |
| DB SELECT after | 774 |
| DB SELECT delta | 1 |
| Git branch | feat/week4-cache-coverage-regression |
| Git commit | dc0e07f0460e90a493bd8acfe2cec89307c96a61 |
| Raw working tree | dirty |
| Source working tree | dirty |
| Java | java version "22.0.2" 2024-07-16 |
| Spring Boot | 4.1.1 |
| OS | Microsoft Windows 10.0.26200  |
| CPU | Intel64 Family 6 Model 186 Stepping 2, GenuineIntel |
| RAM bytes | 16868974592 |
| Docker | 28.4.0\|28.4.0 |
| Redis key | courseDetails::1 |
| MISS contract TTL (seconds) | UNAVAILABLE |
| TTL after prime (seconds) | 599 |
| TTL after run (seconds) | 598 |

> DB query note: Com_select is a global MySQL counter. Other database traffic during the measured workload can affect the delta.
