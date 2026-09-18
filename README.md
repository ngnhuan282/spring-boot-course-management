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

Di chuyển vào backend:

```bash
cd spring-boot-course-management/backend
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

Bài toán minh họa dự kiến sử dụng các thực thể chính:

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
