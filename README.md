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

Bài toán minh họa chỉ sử dụng thực thể `Course`, với API tạo, xem, cập nhật và xóa khóa học. Phạm vi nghiệp vụ được giữ ở mức vừa đủ để minh họa các nội dung Spring Boot đã đăng ký.

---

## 10. Git Workflow

Repository sử dụng mô hình:

```text
main
└── develop
    └── feature branches
```

Ví dụ:

```text
feat/week1-core-init
feat/course-crud
feat/bean-validation
feat/global-exception-handler
feat/redis-cache
test/course-service
docs/update-readme
```

Quy trình:

```text
develop
   ↓
feature branch
   ↓
commit
   ↓
push
   ↓
Pull Request
   ↓
Code Review
   ↓
merge vào develop
```

Không commit trực tiếp vào `main`.

### Quy ước commit gợi ý

```text
feat: thêm chức năng mới
fix: sửa lỗi
docs: cập nhật tài liệu
test: thêm hoặc sửa test
refactor: tái cấu trúc code
chore: công việc cấu hình / bảo trì
```

Ví dụ:

```bash
git commit -m "feat: add health check endpoint"
git commit -m "docs: configure Swagger OpenAPI"
```

---

## 11. Tiến độ hiện tại

### Tuần 1

- [x] Khởi tạo Spring Boot project
- [x] Thiết lập cấu trúc package cơ bản
- [x] Tạo `/api/health`
- [x] Cấu hình Swagger / OpenAPI
- [x] Kiểm tra project chạy trên port `8080`
- [ ] Hoàn thiện tài liệu và minh chứng Tuần 1

### Các giai đoạn tiếp theo

- [ ] Spring Data JPA và thiết kế Entity
- [ ] CRUD cơ bản
- [ ] Bean Validation
- [ ] Global Exception Handler
- [ ] Testing
- [ ] Spring Cache + Redis
- [ ] Đo và so sánh hiệu năng
- [ ] Docker / Docker Compose
- [ ] GitHub Actions
- [ ] Hoàn thiện báo cáo và hands-on lab

---

## 12. Lưu ý bảo mật

Không commit các thông tin nhạy cảm như:

- Password
- API Key
- Secret Key
- Token
- File `.env`

Các biến môi trường mẫu được khai báo trong:

```text
.env.example
```

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

## 15. Trạng thái dự án

> **Đang phát triển**

Phiên bản hiện tại tập trung vào việc hoàn thiện nền tảng Spring Boot và các nội dung học tập theo yêu cầu môn học trước khi mở rộng nghiệp vụ.

---

## Chạy nhánh `refactor/course-standalone`

Course dùng REST Controller, Service, Spring Data JPA, Bean Validation và Global Exception Handler. Request/response của Course chỉ gồm `title`, `description`, `price`, `level`, `status` (response có thêm `id`). Backend chỉ còn module Course; các file Category, Student và Enrollment đã được gỡ khỏi mã nguồn.

Chuẩn bị MySQL 8.4 và Redis bằng `docker compose up -d --wait`. Flyway quản lý schema và dữ liệu mẫu bằng ba migration:

| Migration | Nội dung |
|---|---|
| `V1__create_standalone_courses.sql` | Tạo bảng `courses` nếu chưa có; JPA kiểm tra cấu trúc bằng `validate`. |
| `V2__detach_legacy_course_relationships.sql` | Nếu còn schema tích hợp: bỏ khóa ngoại Category, cho phép `courses.category_id` nhận `NULL`, bỏ khóa ngoại `enrollments.course_id`. Giữ các bảng, cột, giá trị và dòng dữ liệu cũ. |
| `V3__seed_standalone_courses.sql` | Nạp ba Course mẫu một lần; không ghi đè các ID đã tồn tại. |

Database mới có bảng nghiệp vụ `courses` và `flyway_schema_history`. Các migration đã áp dụng không chạy lại khi restart, nên Course đã sửa hoặc xóa không bị dữ liệu mẫu khôi phục. File `data.sql` và script chuyển đổi thủ công `migration/course-standalone-mysql.sql` được giữ để đối chiếu lịch sử; quy trình khởi động mới dùng các migration Flyway ở trên.

### Database cũ chưa có `flyway_schema_history`

Cấu hình mặc định `baseline-on-migrate: false` yêu cầu người chạy xác nhận trước khi tiếp nhận database có sẵn. Sao lưu và kiểm tra đúng database Course độc lập trước bước này. Nếu database đã có lịch sử Flyway từ bản tích hợp, dùng database/schema riêng cho nhánh standalone vì hai nhánh có chuỗi migration khác nhau; không sửa hoặc xóa lịch sử cũ.

Với database cũ **chưa có lịch sử Flyway**, chạy một lần từ thư mục `backend`:

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

Sau khi ứng dụng khởi động thành công, dừng bằng `Ctrl+C`; các lần tiếp theo dùng lệnh chạy bình thường, không bật baseline. Phiên bản baseline phải là **0** để V1, V2 và V3 đều được thực hiện. Nếu schema cũ thiếu cột hoặc sai kiểu dữ liệu, Hibernate sẽ báo lỗi kiểm tra schema thay vì tự chỉnh cấu trúc.

Các bảng Category, Student, Enrollment cũ vẫn được giữ, nhưng ứng dụng standalone không ánh xạ hoặc sử dụng chúng. Sau khi bỏ khóa ngoại, dòng Enrollment cũ có thể trỏ tới ID Course đã bị xóa.

### Kiểm tra Flyway và restart

```sql
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Database mới có ba migration SQL thành công, phiên bản hiện tại là `3`. Database cũ được tiếp nhận có thêm dòng baseline `0`. Khi restart, lịch sử migration và dữ liệu Course không đổi.

`StandaloneFlywayIntegrationTests` dùng MySQL 8.4 riêng qua Testcontainers để kiểm tra database mới, restart, từ chối tự baseline và tiếp nhận schema cũ giữ dữ liệu. `CourseCacheIntegrationTests` tiếp tục dùng H2/Redis với Flyway tắt rõ trong lớp test; profile thí nghiệm datasource `m05` cũng tắt Flyway để giữ đúng phạm vi thí nghiệm cũ.

Chạy build/test và ứng dụng từ thư mục `backend`:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Ví dụ request tạo Course:

```http
POST /api/courses
Content-Type: application/json

{"title":"Spring Boot thực hành","description":"REST và JPA","price":499000,"level":"BEGINNER","status":"DRAFT"}
```

API còn có `GET /api/courses`, `GET /api/courses/{id}`, `PUT /api/courses/{id}` (cùng JSON với POST), `DELETE /api/courses/{id}`. DELETE thành công trả `204`; ID không tồn tại trả `404` với code `COURSE_NOT_FOUND`. `title` rỗng hoặc `price` âm trả `400` với code `VALIDATION_FAILED`.
