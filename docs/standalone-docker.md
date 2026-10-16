# Docker, Flyway và CI/CD - thực nghiệm Course độc lập

## Mục tiêu và cơ chế

Nhóm sử dụng bản chỉ có Course cho Phần 2/Chương 4-8, nhằm quan sát riêng cơ chế
đóng gói, migration, CI và CD. Phần hệ thống tích hợp Chương 12 cần minh chứng
của chính phiên bản tích hợp; không lấy kết quả standalone để khai đã triển khai
toàn bộ hệ thống.

Dockerfile có build stage JDK 21 chạy Maven Wrapper và runtime stage JRE 21
khởi động JAR bằng user `app`. Compose tạo mạng riêng; backend gọi `mysql:3306`
và `redis:6379`, HTTP được công bố trên loopback 8091. Health check dịch vụ dữ
liệu giúp backend khởi động sau khi các dịch vụ sẵn sàng. `/api/health` chỉ kiểm
tra tầng web; API nghiệp vụ, row MySQL và Redis TTL được kiểm chứng thêm.

## Thực hành đóng gói

```powershell
docker compose -f docker-compose.standalone.yml up --build -d --wait --wait-timeout 600
```

Mỗi lượt thu minh chứng dùng một project/volume riêng. Trước lệnh đầu, collector
ghi nhận project chưa có container và volume chưa tồn tại. Đây là cơ sở chứng
minh lượt chạy mới; không khai đã SELECT đếm 0 bảng trước khi backend khởi động.

V1 tạo `courses` không có `category_id`; V2 nạp ba Course trong profile `dev`.
Flyway tạo history và kiểm tra checksum. Hibernate chỉ validate schema; không
chạy song song `data.sql`. Restart backend phải giữ nguyên version, checksum,
thời điểm cài migration và dữ liệu nghiệp vụ. Database lịch sử do Hibernate tạo
cần được đánh giá riêng; không tự bật baseline hay repair để che lỗi.

## Thực hành CI/CD

Workflow có ba job nối tiếp. Maven verify phải thành công trước khi chạy thử
Docker. Lượt Docker phải thành công trước khi CD publish image. Image có tag
full SHA, nhãn source revision và digest; CD dùng chính digest đã publish để
chạy một project mới trên runner, sau đó gọi API và kiểm tra migration/cache.

`GITHUB_TOKEN` chỉ được cấp quyền ghi package trong job CD. Các action được
pin bằng commit SHA. Lượt PR kiểm chứng CI và Docker nhưng không publish image.
Nhánh thử lỗi chỉ chứng minh cơ chế chặn pipeline; không phát hành artifact lỗi.

## Kết quả cần ghi nhận

| Ca | Điều kiện | Minh chứng | Giải thích |
|---|---|---|---|
| Một lệnh Compose | Commit sạch, project/volume mới | Lệnh, log build/start, trạng thái ba container, image revision | Source tạo được môi trường chạy độc lập |
| Flyway lần đầu | MySQL mới | History V1/V2, ba seed Course, schema không có Category | Migration quản lý cấu trúc và seed |
| API/DB/cache | Backend healthy | POST/GET/PUT/DELETE, row MySQL, Redis key/TTL | Ứng dụng thực sự hoạt động trong container |
| Ca lỗi HTTP | Request sai và ID đã xóa | HTTP 400/404 và mã lỗi | Lỗi nghiệp vụ vẫn đúng sau đóng gói |
| Restart | Cùng volume | History/checksum/rows trước-sau | Migration không chạy lặp seed, dữ liệu được giữ |
| CI | Push/PR đúng workflow | Run/job logs, XML test, commit SHA | Build/test kiểm tra thay đổi mã nguồn |
| CD | CI và Docker đã đạt | GHCR tag/digest, job deploy, API từ image kéo lại | Artifact đã phát hành chạy được trên runner tạm |
| Test lỗi có kiểm soát | Nhánh `lab/standalone-ci-*` | Run thất bại rồi run sau khi sửa thành công | Pipeline phát hiện test lỗi; CD không phát hành |

Chỉ điền PASS và số liệu sau khi đọc ZIP của lượt thực tế. Ghi thời điểm, nhánh,
full SHA, image ID/digest và workflow run. Các PNG do collector dựng từ log/HTTP
là ảnh trích nội dung thật, không gọi là ảnh chụp Docker Desktop/GitHub UI.
Ảnh giao diện bổ sung có thể chụp thủ công khi cần trình bày trực quan.

## Liên hệ bố cục báo cáo

- 8.1-8.2: phạm vi độc lập, cơ chế build/run, Flyway, CI và CD.
- 8.3: Dockerfile, Compose, profile, một lệnh và runtime API/DB/cache.
- 8.4: workflow, điều kiện job, kết quả test, GHCR và triển khai tạm.
- 8.5: bảng thực nghiệm với kết quả thật, ca thành công và ca lỗi.
- 8.6: đánh giá khả năng tái lập; liên hệ với quyết định kỹ thuật ở Chương 12.

## Tài liệu gốc

- [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/)
- [Docker multi-stage build](https://docs.docker.com/build/building/multi-stage/)
- [GitHub publishing Docker images](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)

Đối chiếu nhánh nền `refactor/course-standalone` tại `d07dafad9dbb6fbe4e27854ddcc33d71b14f1fc8`
và yêu cầu môn học ngày 16/10/2026.
