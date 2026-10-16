# Spring Boot - Course standalone

Nhánh `refactor/course-standalone` và nhánh chuẩn bị `docker-standalone` là bản
thực nghiệm độc lập cho Chương 4-8 của báo cáo CCNLTHD. Nhóm sử dụng một thực
thể Course để kiểm chứng Spring Boot, JPA, validation, cache, migration và quy
trình đóng gói/phát hành. Kết quả của bản này không tự thay thế minh chứng hệ
thống tích hợp ở Chương 12.

## Công nghệ

Java 21, Spring Boot 4.1.1, Maven Wrapper, Spring Data JPA, Flyway, MySQL 8.4,
Spring Cache/Redis 7.4, JUnit và Testcontainers. Phạm vi nghiệp vụ chỉ có Course;
request không có `categoryId`. Backend sử dụng Controller -> Service -> Repository,
DTO và Global Exception Handler.

## Khởi động bằng một lệnh

Mở Docker Desktop ở chế độ Linux containers; cần Compose v2.20 trở lên. Từ thư
mục gốc repository:

```powershell
docker compose -f docker-compose.standalone.yml up --build -d --wait --wait-timeout 600
```

Lệnh trên build JAR bằng JDK 21 trong Docker, tạo image chạy JRE 21 bằng user
`app`, rồi khởi động backend, MySQL và Redis. Backend chờ hai dịch vụ dữ liệu
healthy. Không cần cài Java/Maven trên máy nếu chạy theo cách này.

- Swagger: <http://127.0.0.1:8091/swagger-ui/index.html>
- Health: <http://127.0.0.1:8091/api/health>
- Courses: <http://127.0.0.1:8091/api/courses>

```powershell
docker compose -f docker-compose.standalone.yml ps
docker compose -f docker-compose.standalone.yml logs --no-color backend
```

MySQL/Redis không công bố cổng host. Named volume `course-standalone_standalone_mysql_data`
được tách khỏi volume của các bài thực hành cũ. HTTP mặc định là 8091; đổi bằng
`STANDALONE_HTTP_PORT` khi cần. Các mật khẩu mẫu chỉ phục vụ thực nghiệm local;
biến có thể thay đổi được liệt kê trong `.env.example`.

## Migration và dữ liệu mẫu

Flyway được bật mặc định. V1 tạo đúng bảng nghiệp vụ `courses`; Flyway quản lý
thêm `flyway_schema_history`. Profile local mặc định `dev` áp dụng V2 để nạp ba
khóa học mẫu một lần. Hibernate dùng `ddl-auto=validate`, SQL initialization là
`never`; `data.sql` cũ không được thực thi trong runtime này.

Profile `prod` chỉ tìm migration schema, yêu cầu biến môi trường cho datasource
và Redis. Đây là cấu hình để minh họa phân tách môi trường, chưa chứng minh một
dịch vụ production đang được vận hành. Database đã áp dụng V2 phải tiếp tục có
location chứa V2 khi kiểm tra checksum; không chuyển tùy tiện database demo
sang profile schema-only.

Không sửa migration đã áp dụng. Thay đổi tiếp theo dùng phiên bản migration mới.
Database cũ do Hibernate hoặc mô hình bốn thực thể tạo cần kế hoạch chuyển đổi
riêng nếu giữ dữ liệu. Cấu hình không tự baseline, repair hay xóa dữ liệu cũ.
Script `migration/course-standalone-mysql.sql` được giữ cho lịch sử bài thực hành
trước đây; không dùng nó để thay thế Flyway trên database mới này.

## API và cache

```http
POST /api/courses
Content-Type: application/json

{"title":"Spring Boot thực hành","description":"REST và JPA","price":499000,"level":"BEGINNER","status":"PUBLISHED"}
```

API gồm GET danh sách, GET theo ID, POST, PUT và DELETE Course. POST trả 201,
DELETE trả 204; dữ liệu không hợp lệ trả 400 với `VALIDATION_FAILED`, ID không
tồn tại trả 404 với `COURSE_NOT_FOUND`.

`GET /api/courses/{id}` dùng cache `standaloneCourseDetails`; TTL mặc định 10
phút. Update/delete thành công làm mất hiệu lực cache sau commit.

```powershell
docker compose -f docker-compose.standalone.yml exec redis redis-cli --scan --pattern 'standaloneCourseDetails::*'
docker compose -f docker-compose.standalone.yml exec redis redis-cli TTL 'standaloneCourseDetails::1'
```

## CI và CD của bản thực nghiệm

Workflow `.github/workflows/standalone-backend.yml` chạy trên push vào
`docker-standalone`, `refactor/course-standalone`, nhánh kiểm chứng lỗi
`lab/standalone-ci-*`, và PR có base `refactor/course-standalone`.

1. `build-and-test`: Maven `verify`, gồm cache test H2/Redis và test Flyway với
   MySQL/Redis thật bằng Testcontainers; lưu `standalone-test-reports`.
2. `docker-one-command`: tạo project/volume mới, build và khởi động cả ba dịch vụ
   bằng một Compose command, kiểm tra API/DB/cache/migration/restart; lưu
   `standalone-docker-proof`.
3. `deliver-ghcr`: sau hai job trên thành công, push trên hai nhánh chính của
   bản standalone phát hành image lên GHCR với tag full commit. Job kéo lại
   image theo digest, triển khai vào runner tạm, kiểm chứng rồi lưu
   `standalone-delivery-proof`.

CD dùng `GITHUB_TOKEN` có `packages: write` trong job phát hành. Không cần tự
điền PAT, mật khẩu GHCR hay SSH key vào repository. PR và nhánh thử lỗi không
phát hành image. Image nằm tại
`ghcr.io/ngnhuan282/spring-boot-course-management-standalone:sha-<FULL_SHA>`.
Package mới có thể là private; việc kéo trên runner sử dụng quyền của workflow.

Phạm vi CD là phát hành image và triển khai kiểm chứng vào môi trường tạm của
GitHub Actions; runner được dọn sau job. Chưa suy ra rằng nhóm đã triển khai
một máy chủ/AWS hay vận hành hệ thống Chương 12.

## Chạy test và dừng môi trường

Nếu chạy Maven trực tiếp, cần Java 21 và Docker Engine cho Testcontainers:

```powershell
Set-Location backend
.\mvnw.cmd -B verify
```

Các thí nghiệm bean lifecycle, DI và datasource hiện có được giữ nguyên. Test
H2 dùng chiến lược tạo schema riêng và tắt Flyway; test migration bật Flyway rõ
ràng với MySQL thật. Build image dùng `-DskipTests`; kết quả test phải đọc từ CI,
không suy ra từ build image.

Từ thư mục gốc, dừng ba dịch vụ và giữ dữ liệu:

```powershell
docker compose -f docker-compose.standalone.yml down
```

Hướng dẫn kiểm chứng và cách viết báo cáo: [docs/standalone-docker.md](docs/standalone-docker.md).
Tools thu minh chứng, PNG và ZIP để ngoài repository. PR của phần này có base
`refactor/course-standalone`; không merge nguyên nhánh Docker của bản bốn thực
thể sang standalone.
