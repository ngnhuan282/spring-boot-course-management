# Đo cache OFF / MISS / HIT

## Phương pháp thống nhất

Cả ba kịch bản dùng **System.Net.Http.HttpClient + System.Diagnostics.Stopwatch**.
Timer bắt đầu ngay trước `GetAsync(..., ResponseContentRead)` và dừng sau khi
`ReadAsStringAsync()` đọc xong toàn bộ body. Mỗi kịch bản tái sử dụng một HttpClient,
tắt tự động redirect, gửi request tuần tự (concurrency = 1).

`latency_ms` là độ trễ HTTP quan sát ở client, gồm gửi/nhận và đọc/giải mã body.
Đây **không phải curl time_total, thời gian tiến trình curl hay thời gian riêng Service**.
DEL/EXISTS/PTTL, lệnh Docker, query counter, kiểm tra JSON và warm-up nằm ngoài timer.
Kiểm tra Redis tạo khoảng nghỉ giữa request; bài này không đo throughput/tải đồng thời.

| Kịch bản | Chuẩn bị backend | Dấu hiệu kiểm tra |
|---|---|---|
| OFF | `spring.cache.type=none` | Xóa key thử nghiệm một lần trước preflight; GET không tạo lại key (EXISTS=0, PTTL=-2); counter DB tăng trong mỗi lượt. |
| MISS | `spring.cache.type=redis` | Xóa đúng key trước từng warm-up/request; key vắng trước GET, tồn tại và PTTL dương sau GET. |
| HIT | `spring.cache.type=redis` | GET preflight tạo key trước đo; key tồn tại/PTTL dương trước và sau từng request; dừng nếu TTL tăng bất thường, có thể key đã được tạo lại. |

Script chỉ DEL key `${CacheName}::${CourseId}`, không FLUSHALL/FLUSHDB, không sửa Course.
MySQL và Redis phải chạy trong Compose cho **cả ba** kịch bản, kể cả OFF để quan sát key.
Mode là cấu hình người chạy khai báo; giữ log startup/cấu hình thực tế để chứng minh.
Dùng môi trường thử nghiệm riêng, không có client/worker khác đọc/ghi cùng Course/key.
OFF còn có ba GET kiểm chứng trước warm-up, lưu `off-verification.json`; các probe này
không nằm trong CSV mẫu đo. Kết quả lỗi vẫn giữ các check đã thu.

**Kiểm tra Task 4 từ mục 5.2:** xem [README_TASK4.md](../../README_TASK4.md), mục 7–9.
Dùng `-Warmup 5 -Rounds 1 -Runs 5`, SQL log bật để xác minh chức năng. Đây không phải
phép đo 90 mẫu chính thức của Task 11. Hướng dẫn có Compose riêng để tránh tên container
MySQL cố định trùng môi trường cũ, và bộ STT01 Host trước/sau từng ca.

## Số mẫu và thống kê

- Mặc định: **20 warm-up**, **3 lượt x 30 request = 90 mẫu/kịch bản**.
- Warm-up giữ mặc định 20 của công cụ hiện có, loại khỏi thống kê. Nếu baseline đã
  chốt số khác, truyền cùng `-Warmup` cho cả ba; ghi rõ lựa chọn trong hồ sơ mới.
- `-Runs` là số request mỗi lượt; `-Rounds` là số lượt. Muốn lặp workload cũ 100
  request của công cụ: truyền `-Rounds 1 -Runs 100` rõ ràng cho cả ba kịch bản.
- Mọi request đo phải HTTP 200, đúng ID và cùng SHA256 body với preflight. Lưu mẫu
  lỗi trước khi dừng; lỗi kết nối ghi status 0, lỗi đọc body giữ status đã nhận.
- Median tính từ toàn bộ mẫu, không lấy median của ba median. P95 nearest-rank
  lấy hạng `ceil(0.95*n)` sau sắp xếp (90 mẫu: hạng 86). Độ phân tán là min/max.
- CSV lưu double dạng round-trip, dấu thập phân `.`; không điền số liệu khi chưa đo.

`rounds.csv` lưu MySQL `Com_select` trước/sau mỗi lượt, không tính preflight/warm-up.
Đây là counter **toàn server**, không phân biệt Course/Category hay traffic khác.
Cần log/trace riêng để đếm theo entity. HIT có delta khác 0 sẽ cảnh báo, không tự
quy mọi query đó cho Course. Không chạy traffic khác trong lúc đo.

## Giữ đúng bộ STT01

- Thu môi trường **before/after mỗi lượt đo thực tế** bằng bộ STT01; chọn Host/Docker,
  PID/container/database/URL/log startup đúng lượt. Đặt environment và kết quả benchmark
  trong cùng thư mục hồ sơ ngoài repo. Script không tự restart ứng dụng hoặc chạy STT01.
- Giữ dataset, ID, source, JVM, logging và tham số giống nhau cho OFF/MISS/HIT, chỉ đổi
  cấu hình cache. Đối chiếu `CoursePayloadSha256` của cả ba trước khi so sánh.
- Git trước/sau được lưu. HEAD không đại diện đầy đủ cho tree dirty; không reset-hard
  để làm sạch. Script không tạo archive toàn source hay chứng minh runtime khớp checkout.
- Java trong summary chỉ là **host java -version**, không phải JVM backend/forked test.
  Lấy runtime thực tế từ STT01. Giữ riêng môi trường lịch sử, không thay JVM cũ bằng JVM mới.
- PNG 01–04 của STT01 là ảnh xuất từ log. Giữ log/JSON gốc và kiểm manifest khi đóng gói
  theo `Huong-dan.md`. ZIP thành công không tự chứng minh kiểm thử đã đạt.

## Cách chạy

Chọn một Course có thật trong dataset đã chốt; ID 1 dưới đây chỉ là ví dụ.
Backend/MySQL/Redis phải được chuẩn bị theo README/profile hiện tại của repo.
Khởi động backend có thể áp dụng migration/seed theo cấu hình: dùng đúng DB thử nghiệm.

Terminal backend OFF (dừng backend cũ trước để tránh trùng cổng):

```powershell
Set-Location 'D:\ccclthd\spring-boot-course-management\backend'
Remove-Item Env:DEBUG -ErrorAction SilentlyContinue
$env:SPRING_CACHE_TYPE = 'none'
$env:SPRING_JPA_SHOW_SQL = 'false'
# Giữ datasource/profile của môi trường đã chọn; lưu startup log cho STT01.
.\mvnw.cmd spring-boot:run
```

Terminal đo, sau khi thu STT01-before:

```powershell
Set-Location 'D:\ccclthd\spring-boot-course-management'
.\scripts\cache-performance\benchmark.ps1 -Mode off -CourseId 1 `
  -Warmup 20 -Rounds 3 -Runs 30 `
  -ResultsRoot 'D:\ccclthd\cache-benchmark-evidence'
```

Thu STT01-after. Sau đó dừng backend OFF, chạy lại cùng source/dataset/cấu hình khác,
chỉ đổi `$env:SPRING_CACHE_TYPE = 'redis'`. Giữ SQL logging tắt cho cả ba kịch bản.
Thu STT01 trước/sau **mỗi** lệnh sau:

```powershell
.\scripts\cache-performance\benchmark.ps1 -Mode miss -CourseId 1 `
  -Warmup 20 -Rounds 3 -Runs 30 `
  -ResultsRoot 'D:\ccclthd\cache-benchmark-evidence'

.\scripts\cache-performance\benchmark.ps1 -Mode hit -CourseId 1 `
  -Warmup 20 -Rounds 3 -Runs 30 `
  -ResultsRoot 'D:\ccclthd\cache-benchmark-evidence'
```

Nếu cần ảnh SQL, chạy một lượt xác minh riêng; không dùng làm mẫu latency.
Nếu Hibernate SQL DEBUG vẫn xuất hiện, tìm nguồn logging trước khi đo.

| Tham số khác | Mặc định |
|---|---|
| `-BaseUrl` | `http://localhost:8080` |
| `-ComposeFile` | `docker-compose.yml` trong repo |
| `-DbService` / `-RedisService` | `mysql` / `redis` |
| `-CacheName` | `courseDetails` |
| `-TtlMinutes` | 10, giá trị kỳ vọng, không thay PTTL quan sát |
| `-ResultsRoot` | `cache-benchmark-evidence` cạnh repo, bắt buộc ngoài repo |

Redis CLI hiện dùng kết nối mặc định trong container. Môi trường cần ACL/auth/TLS
phải cấu hình theo thực tế, không đưa credential vào lệnh/log.

## Hồ sơ đầu ra

Mỗi lần tạo thư mục timestamp/mode/mã riêng ngoài repo, gồm:

- `benchmark.ps1`: bản script đã dùng.
- `course-preflight.json`: dữ liệu đối chiếu.
- `requests.csv`: scenario, round, request, http_status, latency_ms, key, EXISTS/PTTL...
- `rounds.csv`: số mẫu và counter DB trước/sau từng lượt.
- `summary.json`, `summary.md`: phương pháp, n/median/p95/min/max, Git và giới hạn.
- `off-verification.json` (OFF): ba probe, HTTP status, key/PTTL, counter GLOBAL trước/sau.

`summary.json.Measurement` ghi PowerShell/.NET và SHA256 bản script đã dùng cùng phương
pháp đo. `OffVerification` chứa kết quả probe riêng, không gộp vào mẫu thống kê.

Nếu lỗi: `failure.json` và CSV đã thu, không tạo successful summary.
Không gộp run khác commit thành một lần đo; không thay kết luận standalone cũ, kể cả
52,87%, bằng số liệu chưa có.

Mô tả dùng cho báo cáo **sau khi chạy thật**, điền các chỗ trống từ hồ sơ:

> Lượt [mã/thời điểm], commit [full SHA], đo OFF/MISS/HIT bằng HttpClient + Stopwatch.
> Timer từ gửi GET đến đọc xong body; lệnh Redis và query counter nằm ngoài timer.
> Mỗi kịch bản có [warm-up] request khởi động không tính mẫu và [lượt] x [request]
> mẫu tuần tự. Median/p95 nearest-rank/min/max tính trên toàn bộ mẫu hợp lệ.
> Dataset, JVM runtime và cấu hình của lượt được lưu kèm hồ sơ STT01.
