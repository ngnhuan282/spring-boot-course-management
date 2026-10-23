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

### Backend và dữ liệu

- Java 21
- Spring Boot 4.1.1
- Maven
- Spring Web
- Spring Data JPA / Hibernate
- Bean Validation
- Spring Cache
- Redis 7.4
- MySQL 8.4 cho runtime
- H2 cho các test dùng cấu hình mặc định; MySQL 8.4/Redis 7.4 riêng cho các ca tích hợp cache và transaction
- Lombok
- Springdoc OpenAPI / Swagger UI

### Kiểm thử và vận hành

- JUnit Jupiter, Mockito và MockMvc; phiên bản do POM của commit đang chạy quản lý
- Testcontainers MySQL/Redis cho các ca tích hợp tương ứng; không dùng database đang phát triển
- Docker Compose cho backend, MySQL và Redis; Flyway quản lý schema
- GitHub Actions workflow cho Maven verify và Surefire artifacts

---

## 3. Cấu trúc repository

```text
spring-boot-course-management/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/ccnlthd/course_management/
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
├── Dockerfile
├── docker-compose.backend.yml
├── docker-compose.yml
└── README.md
```

> Một số package có thể chưa xuất hiện trên GitHub khi còn rỗng vì Git không theo dõi thư mục rỗng.

---

## 4. Kiến trúc backend hiện tại

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

Để chạy hệ thống bằng Docker: Git, Docker Desktop chạy Linux containers và
Docker Compose v2.20 trở lên. Không cần Java/Maven trên host cho cách chạy này.
Java 21 cần khi chạy backend/test trên host hoặc IDE. Cache integration test
cần Docker daemon vì sử dụng Testcontainers.

---

## 6. Cách chạy project

Từ thư mục gốc repository, chạy toàn bộ backend, MySQL và Redis bằng **một lệnh**:

```powershell
docker compose -f docker-compose.backend.yml up --build -d --wait --wait-timeout 600
```

Cần Docker Desktop chạy Linux containers và Git; Java 21/Maven chạy trong image.
Swagger: <http://127.0.0.1:8088/swagger-ui/index.html>.

```powershell
docker compose -f docker-compose.backend.yml ps
curl.exe -i http://127.0.0.1:8088/api/health
curl.exe -i http://127.0.0.1:8088/api/courses
```

`docker-compose.backend.yml` độc lập với `docker-compose.yml` cũ. MySQL/Redis
không công bố cổng host. Project mặc định dùng volume riêng; lệnh `down` giữ dữ liệu.
Có thể đặt `COURSE_DOCKER_HTTP_PORT=8089` trong môi trường trước khi chạy nếu 8088 bận.
Các giá trị mẫu có trong `.env.example`; hướng dẫn đầy đủ ở
[docs/docker-backend.md](docs/docker-backend.md).

### Quản lý schema bằng Flyway

`application.yml` mặc định bật Flyway, dùng Hibernate `validate` và tắt SQL init.
V1 tạo schema. Compose chọn thêm location `db/demo` để V2 nạp dữ liệu mẫu local
(2 Category, 3 Course, 2 Student, 3 Enrollment). Migration đã áp dụng được giữ nguyên.

Cấu hình không tự baseline database cũ do Hibernate tạo. Với dữ liệu cũ cần giữ,
cần kế hoạch chuyển đổi riêng; không xóa volume hay dùng `repair` để che lỗi.
Cách chạy Docker ở trên sử dụng database trong volume riêng. Khi chạy backend
trên host/IDE, cấp `SPRING_DATASOURCE_URL`, username/password và `REDIS_HOST` cho
MySQL mới hoặc database đã có đúng history Flyway; profile mặc định `dev` đọc cả `db/migration` và `db/demo`.
Profile migration riêng vẫn chọn `db/migration` theo cấu hình của profile đó.
Nếu chạy lại trên database từng dùng V2, cần giữ location chứa V2 để Flyway
validate được đầy đủ các migration đã áp dụng.

### Kiểm thử

Từ thư mục `backend`, với Java 21 và Docker daemon đang chạy:

```powershell
.\mvnw.cmd -B verify
```

Test mặc định dùng `src/test/resources/application.yml` (H2, Flyway tắt,
Hibernate `create-drop`). Các ca MySQL/Redis và đồng thời có cấu hình
riêng, tự tạo Testcontainers MySQL/Redis và bật Flyway với Hibernate
`validate`. Phải có Docker daemon để chạy đầy đủ `verify`. Không suy
ra database của một ca chỉ từ cấu hình test mặc định. Dockerfile dùng `-DskipTests` để đóng gói; GitHub Actions mới là
lượt build/test tự động. Không suy ra test đã chạy từ log build image.

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

Bài toán minh họa sử dụng các thực thể chính:

- `Category`
- `Course`
- `Student`
- `Enrollment`

Quan hệ cơ bản:

```text
Category 1 ----- N Course

Student  1 ----- N Enrollment
Course   1 ----- N Enrollment
```

Phạm vi nghiệp vụ sẽ được giữ ở mức vừa đủ để minh họa các nội dung Spring Boot đã đăng ký.

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

## 11. Các mốc và phần đã triển khai

### Tuần 1

- [x] Khởi tạo Spring Boot project
- [x] Thiết lập cấu trúc package cơ bản
- [x] Tạo `/api/health`
- [x] Cấu hình Swagger / OpenAPI
- [x] Kiểm tra project chạy trên port `8080`
- [ ] Hoàn thiện tài liệu và minh chứng Tuần 1

### Các phần đã triển khai

- [x] Spring Data JPA và thiết kế Entity
- [x] CRUD Category, Course, Student và Enrollment
- [x] Bean Validation
- [x] Global Exception Handler
- [x] Unit, repository, API và cache integration tests
- [x] Spring Cache + Redis cho Course detail
- [x] Docker Compose cho backend, MySQL và Redis
- [x] GitHub Actions workflow đã được cấu hình
- Đo OFF/MISS/HIT: công cụ đã có trong scripts/cache-performance; số liệu nghiệm thu gắn SHA nằm trong hồ sơ ngoài Git
- Báo cáo và hands-on lab: bản nộp được quản lý trong hồ sơ cuối kỳ ngoài repository

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

> **Trạng thái nghiệm thu được ghi trong hồ sơ theo commit**

Phạm vi sản phẩm là backend Category, Course, Student và Enrollment. Phiên bản nộp được xác định bằng SHA và bộ kết quả nghiệm thu tương ứng ở mục dưới.

---

## Nghiệm thu phiên bản cuối

Hoàn tất README, mã nguồn và workflow trước khi chọn commit nghiệm thu.
Nhóm dùng commit đã merge vào `develop`; lấy đầy đủ SHA bằng
`git rev-parse HEAD`. Bộ nghiệm thu lưu SHA, README, CI `push`, nguồn
đóng gói, kết quả kiểm thử, HTTP, Flyway và benchmark của cùng commit.
Kết quả và Bảng phủ được lưu ngoài repository trong hồ sơ nộp.

Cách chạy sản phẩm là lệnh ở mục 6. Khi nghiệm thu, chọn project
Compose/volume riêng bằng `COMPOSE_PROJECT_NAME`, và đổi cổng bằng
`COURSE_DOCKER_HTTP_PORT` để tránh dùng database đang phát triển.
Dữ liệu mẫu local thuộc V2; `down` không kèm `-v` giữ volume.

Benchmark chạy công cụ trong `scripts/cache-performance/benchmark.ps1`,
cùng dataset, 3 vòng × 30 mẫu/chế độ OFF, MISS, HIT, warm-up 20.
Đối chứng OFF chỉ đổi `SPRING_CACHE_TYPE` qua cấu hình nghiệm thu
ngoài repo; image/mã nguồn giữ nguyên. Số liệu là latency HTTP phía
client gồm đọc toàn bộ response; không đồng nhất với thời gian Service.
Bộ đếm MySQL toàn cục có thể gồm health check.

Các mốc minh chứng trước được giữ là lịch sử. Nếu sửa mã, POM,
README hoặc CI sau khi chọn commit, nghiệm thu lại phiên bản mới.
Không ghi SHA của chính commit vào README rồi tạo thêm commit để
thay SHA đó; SHA đầy đủ nằm trong hồ sơ nghiệm thu ngoài Git.
