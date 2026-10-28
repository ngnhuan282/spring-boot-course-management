# Spring Boot Course Management

Đồ án môn **Các Công nghệ Lập trình Hiện đại** với đề tài:

> **Tìm hiểu và ứng dụng công nghệ Spring Boot**

Bài toán **quản lý khóa học trực tuyến** được sử dụng làm case study để minh họa và kiểm chứng các cơ chế của Spring Boot. Trọng tâm của đồ án là **làm chủ công nghệ**, không phải xây dựng một nền tảng E-Learning hoàn chỉnh.

---

## 1. Mục tiêu học tập

### Tầng 1 — Bắt buộc lõi

Nhóm tập trung làm rõ và thực hành các nội dung:

- **IoC và Dependency Injection**
- **REST Controller và Service Layer**
- **Spring Data JPA**

### Nội dung chọn thêm

- **Bean Validation**
- **Global Exception Handler**

### Tầng 3 — Nâng cao

- **Spring Cache**
- **Redis**
- Cache hit / cache miss
- TTL
- Cache invalidation
- Đo thời gian phản hồi trước và sau khi áp dụng cache

---

## 2. Công nghệ sử dụng

### Backend

- Java 21
- Spring Boot 4.1.1
- Maven
- Spring Web
- Bean Validation
- Lombok
- Springdoc OpenAPI / Swagger UI

### Dự kiến bổ sung

- Spring Data JPA
- Database
- Spring Cache
- Redis
- Testing
- GitHub Actions
- Docker / Docker Compose

---

## 3. Cấu trúc repository

```text
spring-boot-course-management/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/ccnlthd/coursemanagement/
│   │   │   │       ├── config/
│   │   │   │       ├── controller/
│   │   │   │       ├── dto/
│   │   │   │       │   ├── request/
│   │   │   │       │   └── response/
│   │   │   │       ├── entity/
│   │   │   │       ├── exception/
│   │   │   │       ├── repository/
│   │   │   │       ├── service/
│   │   │   │       │   └── impl/
│   │   │   │       └── CourseManagementApplication.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── docs/
├── .env.example
├── .gitignore
├── docker-compose.yml
└── README.md
```

> Một số package có thể chưa xuất hiện trên GitHub khi còn rỗng vì Git không theo dõi thư mục rỗng.

---

## 4. Kiến trúc backend dự kiến

Luồng xử lý chính:

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
Database
```

Nguyên tắc:

- `Controller`: tiếp nhận HTTP request và trả HTTP response.
- `Service`: chứa logic nghiệp vụ.
- `Repository`: phụ trách truy cập dữ liệu.
- `DTO`: tách dữ liệu request/response khỏi Entity.
- `Exception`: xử lý lỗi tập trung.
- `Config`: chứa cấu hình của ứng dụng.

Business logic không được đặt trực tiếp trong Controller.

---

## 5. Yêu cầu môi trường

Cài đặt trước:

- **Java 21**
- **Git**
- IDE hỗ trợ Java/Spring Boot, khuyến nghị IntelliJ IDEA

Kiểm tra Java:

```bash
java -version
```

Kết quả cần sử dụng Java 21.

---

## 6. Cách chạy project

Clone repository:

```bash
git clone https://github.com/ngnhuan282/spring-boot-course-management.git
```

Nếu muốn chạy bản Course độc lập, chọn đúng nhánh sau khi tải mã:

```powershell
cd spring-boot-course-management
git switch refactor/course-standalone
```

Mở thư mục gốc bằng IntelliJ IDEA và nhập [pom.xml](pom.xml) ở thư mục gốc như một dự án Maven. Tệp này khai báo mô-đun `backend`; các thư viện Spring Cache và Spring Data Redis nằm trong [backend/pom.xml](backend/pom.xml). Chọn Java 21 cho dự án, rồi dùng **Reload All Maven Projects** sau mỗi lần chuyển nhánh hoặc kéo mã mới. Không chép riêng `CacheConfig.java` sang một nhánh khác mà thiếu `backend/pom.xml`.

Trước khi chạy ứng dụng, kiểm tra mã từ thư mục gốc:

```powershell
.\backend\mvnw.cmd -B -f pom.xml compile
```

Nếu lệnh trên báo `BUILD SUCCESS` nhưng trình soạn thảo vẫn tô đỏ `org.springframework.data.redis`, tải lại dự án Maven trong trình soạn thảo; đó là trạng thái thư viện của trình soạn thảo, không phải lỗi biên dịch Java. Nếu Maven báo lỗi, gửi dòng `[ERROR]` đầu tiên để xác định thiếu thư viện hay cấu hình.

Từ thư mục gốc, chạy MySQL và Redis, rồi vào backend:

```bash
cd spring-boot-course-management
docker compose up -d --wait
cd backend
```

Redis mặc định ở `localhost:6379`; TTL cache Course detail là 10 phút. Có thể đổi bằng `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` và `APP_CACHE_COURSE_DETAIL_TTL`.

Flyway được bật mặc định và chạy migration trong `backend/src/main/resources/db/migration` trước khi JPA khởi tạo. Hibernate dùng `ddl-auto: validate`; `spring.sql.init.mode: never` ngăn chạy lại `data.sql` mỗi lần khởi động. Database mới tự được chuẩn bị khi chạy ứng dụng. Database cũ chưa có lịch sử Flyway cần làm theo phần **Database cũ** bên dưới.

Chỉ `GET /api/courses/{id}` dùng cache `standaloneCourseDetails`. Sau khi sửa hoặc xóa Course thành công, entry tương ứng được xóa sau commit. Từ thư mục gốc có thể xem JSON và TTL bằng:

```bash
docker compose exec redis redis-cli --scan --pattern 'standaloneCourseDetails::*'
docker compose exec redis redis-cli GET 'standaloneCourseDetails::1'
docker compose exec redis redis-cli TTL 'standaloneCourseDetails::1'
```

### Windows PowerShell / CMD

```bash
mvnw.cmd spring-boot:run
```

hoặc:

```bash
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
./mvnw spring-boot:run
```

Khi chạy thành công, ứng dụng mặc định hoạt động tại:

```text
http://localhost:8080
```

---

## 7. Health Check

Endpoint kiểm tra trạng thái backend:

```http
GET /api/health
```

URL:

```text
http://localhost:8080/api/health
```

Ví dụ response:

```json
{
  "status": "UP",
  "application": "Course Management API",
  "timestamp": "2026-09-18T..."
}
```

---

## 8. Swagger / OpenAPI

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

Swagger được sử dụng để:

- Theo dõi danh sách API
- Kiểm tra request/response
- Test endpoint trong quá trình phát triển
- Làm tài liệu API cho đồ án

---

## 9. Phạm vi case study

Nhánh `refactor/course-standalone` tập trung vào thực thể `Course`, theo luồng Controller → Service → Repository. Dữ liệu đầu vào gồm `title`, `description`, `price`, `level`, `status`; phản hồi Course có thêm `id`.

### API Course

| Phương thức | Endpoint | Kết quả thành công |
|---|---|---|
| GET | `/api/courses` | `200`, danh sách Course |
| GET | `/api/courses/{id}` | `200`, chi tiết Course |
| POST | `/api/courses` | `201`, Course được tạo |
| PUT | `/api/courses/{id}` | `200`, Course sau cập nhật |
| DELETE | `/api/courses/{id}` | `204`, thân phản hồi rỗng |

Ví dụ JSON cho POST và PUT trong Swagger, với `Content-Type: application/json`:

```json
{
  "title": "Spring Boot thực hành",
  "description": "REST và JPA",
  "price": 499000,
  "level": "BEGINNER",
  "status": "DRAFT"
}
```

`title`, `price`, `level`, `status` là các trường bắt buộc; giá phải từ 0 trở lên. Phản hồi thành công trả đối tượng Course hoặc danh sách Course trực tiếp. Phản hồi lỗi gồm `code`, `message`, `result`, `timestamp`.

| Trường hợp | HTTP | Mã lỗi |
|---|---|---|
| Dữ liệu vi phạm validation, ví dụ title rỗng hoặc giá âm | 400 | `VALIDATION_FAILED` |
| Đọc, cập nhật hoặc xóa ID Course không tồn tại | 404 | `COURSE_NOT_FOUND` |

---

## 10. Git Workflow

Các thay đổi của bản tích hợp được quản lý trên `develop`; các thay đổi riêng của Course độc lập được tạo từ `refactor/course-standalone` và mở pull request về chính nhánh này.

Quy trình cho Course độc lập:

1. Chọn nhánh `refactor/course-standalone` làm nguồn.
2. Tạo nhánh chức năng hoặc tài liệu cho công việc cụ thể.
3. Commit, push và mở pull request về `refactor/course-standalone`.
4. Review thay đổi, đọc kết quả build/test rồi merge.

Giữ các thay đổi của Course độc lập trong đúng phạm vi nhánh; không commit trực tiếp vào `main`.

### Quy ước commit gợi ý

```text
feat: thêm chức năng mới
fix: sửa lỗi
docs: cập nhật tài liệu
test: thêm hoặc sửa test
refactor: tái cấu trúc code
chore: công việc cấu hình hoặc bảo trì
```

---

## 11. Chức năng đã triển khai

- [x] Health check và Swagger/OpenAPI.
- [x] REST Controller, Service và Constructor Injection.
- [x] Spring Data JPA, MySQL và CRUD Course.
- [x] Bean Validation và xử lý ngoại lệ tập trung.
- [x] Spring Cache kết hợp Redis, TTL và vô hiệu hóa cache sau commit.
- [x] Flyway quản lý schema và dữ liệu mẫu.
- [x] Kiểm thử tự động cho cache, migration và các bài thực nghiệm bean/dependency.
- [x] Docker Compose cung cấp MySQL và Redis.
- [x] Cấu hình GitHub Actions build và kiểm thử nhánh Course độc lập.

---

## 12. Lưu ý bảo mật

Giữ mật khẩu thực, API key, secret, token và file `.env` ngoài Git. Các giá trị trong `.env.example` là cấu hình mẫu cho môi trường demo cục bộ.

Compose đọc `.env` tại thư mục gốc. Backend chạy bằng Maven nhận biến môi trường của tiến trình chạy ứng dụng; khi thay đổi tài khoản hoặc database, cần đồng bộ thông tin kết nối của backend với MySQL.

---

## 13. Thành viên

Nhóm gồm **3 thành viên**.

Phân công chi tiết được quản lý trong file kế hoạch tiến độ của nhóm. Mọi thành viên đều cần hiểu các nội dung công nghệ đã được khai báo vì phần thực hành tại chỗ có thể chọn ngẫu nhiên thành viên.

---

## 14. Tài liệu tham khảo chính

Ưu tiên sử dụng tài liệu chính thức:

- Spring Boot Documentation
- Spring Framework Documentation
- Spring Data JPA Documentation
- Springdoc OpenAPI Documentation
- Redis Documentation

---

## 15. Chạy và kiểm chứng Course độc lập

Các bước dưới đây tiếp tục sau mục 8, giữ cấu hình cổng mặc định:

| Thành phần | Cách chạy | Địa chỉ hoặc cổng mặc định |
|---|---|---|
| Backend | Maven trên máy | `http://localhost:8080` |
| MySQL | Docker, image `mysql:8.4` | `localhost:3306`, database `course_management` |
| Redis | Docker, image `redis:7.4-alpine` | `localhost:6379` |

Giữ cửa sổ backend hoạt động. Mở PowerShell thứ hai tại thư mục gốc repository để chạy các lệnh kiểm tra. Docker Desktop cần hoạt động khi dùng Compose hoặc Testcontainers.

### 15.1. Kiểm tra API và image dịch vụ

```powershell
$base = 'http://localhost:8080'

Invoke-RestMethod "$base/api/health" | ConvertTo-Json -Compress
Invoke-RestMethod "$base/api/courses" | Format-Table id, title, price

docker compose ps
docker compose images
```

Health trả `UP`; hai container MySQL và Redis cần ở trạng thái `healthy`. Các image dịch vụ cần khớp khai báo `mysql:8.4` và `redis:7.4-alpine` trong cấu hình Compose. Backend được biên dịch và chạy từ mã nguồn bằng Maven; Compose cung cấp hai dịch vụ phụ thuộc.

### 15.2. Kiểm tra cache

Chỉ `GET /api/courses/{id}` dùng cache `standaloneCourseDetails`; TTL mặc định là 10 phút. Chọn ID đang tồn tại trong danh sách Course. Ví dụ dưới đây dùng ID `1`:

```powershell
Invoke-RestMethod "$base/api/courses/1"

docker compose exec -T redis redis-cli GET 'standaloneCourseDetails::1'
docker compose exec -T redis redis-cli TTL 'standaloneCourseDetails::1'
```

Sau lần đọc tạo cache, Redis lưu dữ liệu chi tiết và TTL bắt đầu gần `600` giây, sau đó giảm theo thời gian.

Khi PUT hoặc DELETE thành công, ứng dụng xóa khóa cache tương ứng sau transaction commit. Sau khi thực hiện thao tác đó qua Swagger, kiểm tra đúng ID vừa sửa hoặc xóa:

```powershell
docker compose exec -T redis redis-cli EXISTS 'standaloneCourseDetails::1'
```

Kết quả `0` xác nhận khóa đã bị xóa. Nếu gọi GET chi tiết lại trước phép kiểm tra, ứng dụng có thể tạo lại khóa cache.

### 15.3. Migration Flyway

Flyway được bật mặc định và chạy migration trước khi JPA khởi tạo. Hibernate dùng `ddl-auto: validate` để kiểm tra cấu trúc; `spring.sql.init.mode: never` tắt việc chạy lại `data.sql` mỗi lần khởi động.

| Migration | Nội dung |
|---|---|
| `V1__create_standalone_courses.sql` | Tạo bảng `courses` nếu chưa có; JPA kiểm tra cấu trúc bằng `validate`. |
| `V2__detach_legacy_course_relationships.sql` | Nếu còn schema tích hợp: bỏ khóa ngoại Category, cho phép `courses.category_id` nhận `NULL`, bỏ khóa ngoại `enrollments.course_id`. Giữ các bảng, cột, giá trị và dòng dữ liệu cũ. |
| `V3__seed_standalone_courses.sql` | Nạp ba Course mẫu một lần; không ghi đè các ID đã tồn tại. |

Database mới có bảng nghiệp vụ `courses` và bảng lịch sử `flyway_schema_history`. Các migration đã áp dụng giữ nguyên qua các lần khởi động. Quy trình khởi tạo dùng ba migration Flyway ở trên; `data.sql` và script chuyển đổi thủ công được giữ để đối chiếu lịch sử.

Kiểm tra từ thư mục gốc repository với tài khoản và database mặc định:

```powershell
docker compose exec -T mysql mysql -ucourse_user -pcourse_password -D course_management -e "SELECT installed_rank, version, description, type, script, success FROM flyway_schema_history ORDER BY installed_rank; SELECT id, title FROM courses ORDER BY id;"
```

Database mới có ba migration SQL thành công, phiên bản hiện tại là `3`. Nếu đã cấu hình tài khoản hoặc tên database riêng trước đó, dùng thông tin tương ứng cho câu lệnh truy vấn.

### 15.4. Dữ liệu sau khởi động lại

Thực hiện trên database Course dùng cho demo:

1. Qua Swagger, sửa một Course mẫu và xóa một Course mẫu khác; ghi lại ID và dữ liệu sau thao tác.
2. Kiểm tra danh sách Course và lịch sử Flyway bằng các lệnh ở trên.
3. Ở cửa sổ backend, nhấn `Ctrl+C`; nếu được hỏi kết thúc batch job, chọn `Y`.
4. Từ thư mục `backend`, chạy lại `.\mvnw.cmd spring-boot:run` trong cùng cửa sổ để giữ cấu hình kết nối.
5. Chạy lại API và truy vấn SQL. Course đã sửa phải giữ nội dung mới; Course đã xóa giữ trạng thái đã xóa. Flyway xác nhận schema đã cập nhật và giữ nguyên lịch sử migration.

### 15.5. Database cũ chưa có lịch sử Flyway

Cấu hình mặc định `baseline-on-migrate: false` yêu cầu người chạy xác nhận trước khi tiếp nhận database có sẵn. Sao lưu và xác nhận đúng database Course trước bước này. Database đã có lịch sử Flyway của bản tích hợp cần dùng database/schema riêng cho standalone, vì hai nhánh có chuỗi migration khác nhau; giữ nguyên lịch sử đã có.

Đặt thông tin datasource tới đúng database Course cũ cần tiếp nhận. Với database **chưa có `flyway_schema_history`**, bật baseline một lần tại phiên bản `0`, từ thư mục `backend`:

```powershell
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'true'
$env:SPRING_FLYWAY_BASELINE_VERSION = '0'
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_FLYWAY_BASELINE_VERSION -ErrorAction SilentlyContinue
}
```

Sau khi khởi động thành công, dừng ứng dụng để khối `finally` dọn hai biến baseline. Các lần tiếp theo chạy bình thường. Baseline `0` cho phép V1, V2 và V3 đều được thực hiện; lịch sử có thêm dòng baseline `0`. Hibernate báo lỗi nếu cấu trúc còn sai kiểu dữ liệu hoặc thiếu cột.

Các bảng Category, Student và Enrollment cũ vẫn được giữ, còn ứng dụng standalone chỉ sử dụng Course. Sau khi bỏ khóa ngoại, dòng Enrollment cũ có thể tham chiếu ID Course đã bị xóa.

### 15.6. Kiểm thử tự động và CI

Khi Docker Desktop đang hoạt động, mở PowerShell tại thư mục `backend` để chạy:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -B clean verify
```

Đọc tổng kết `Tests run` và trạng thái build ở cuối kết quả. Báo cáo kiểm thử được lưu trong `backend/target/surefire-reports/`, tính từ thư mục gốc repository. Nếu đang chạy backend, có thể dừng bằng `Ctrl+C` trước lệnh `clean verify`, rồi khởi động lại sau khi kiểm thử kết thúc.

`StandaloneFlywayIntegrationTests` dùng MySQL riêng qua Testcontainers để kiểm tra database mới, restart, từ chối tự baseline và tiếp nhận schema cũ giữ dữ liệu. `CourseCacheIntegrationTests` dùng H2 với Flyway tắt trong lớp test và Redis riêng để kiểm tra cache. Bộ kiểm thử backend còn gồm các bài thực nghiệm vòng đời bean và lựa chọn dependency.

Workflow [Standalone backend](.github/workflows/standalone-backend.yml) được cấu hình chạy khi push hoặc mở pull request vào `refactor/course-standalone`. Job dùng Java 21, chạy Maven `verify` và lưu báo cáo Surefire XML.

### 15.7. Kết quả kiểm chứng đã ghi nhận

Lượt kiểm chứng ngày 28/10/2026 dùng nguồn [092574a3](https://github.com/ngnhuan282/spring-boot-course-management/tree/092574a301415977c24e6081cc67242cd12f3d85), Java 21 và Maven 3.9.16, với database và cổng cấu hình riêng. Bảng này lưu kết quả của lượt đó; hướng dẫn chạy trong README dùng cổng mặc định.

| Nội dung | Kết quả ghi nhận |
|---|---|
| Mã nguồn | Giữ nguyên `092574a3`, trạng thái làm việc sạch trước và sau lượt chạy. |
| Image dịch vụ | `mysql:8.4`, `redis:7.4-alpine` khớp cấu hình tại cùng nguồn; hai container `healthy`. |
| Build và kiểm thử | 12 ca đạt, 0 failure, 0 error, 0 skipped; `BUILD SUCCESS`, mã thoát 0. |
| Khởi động lần đầu | Ba migration thành công, schema ở phiên bản 3; health trả `UP`, có ba Course mẫu. |
| Cache | TTL 600 giây sau GET; phép kiểm tra tồn tại khóa trả 0 sau PUT. |
| Xóa Course | HTTP 204. |
| Sau restart | Ba migration được xác thực; dữ liệu đã sửa và trạng thái đã xóa được giữ nguyên. |

### 15.8. Kết thúc phiên chạy

Dừng backend bằng `Ctrl+C`. Từ thư mục gốc repository, dừng các dịch vụ:

```powershell
docker compose down
```

Lệnh trên giữ volume MySQL để sử dụng lại dữ liệu.
