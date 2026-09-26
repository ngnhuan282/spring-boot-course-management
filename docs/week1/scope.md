# PHẠM VI ĐỀ TÀI

## 1. Tên đề tài

**Tìm hiểu và ứng dụng công nghệ Spring Boot**

---

## 2. Công nghệ chính

- Công nghệ: Spring Boot
- Loại: Backend Framework
- Ngôn ngữ sử dụng: Java
- Hệ sinh thái: Spring Framework

Spring Boot là công nghệ chính được nhóm nghiên cứu trong đồ án.

Trọng tâm của đồ án là tìm hiểu bản chất, cách hoạt động và khả năng
ứng dụng của Spring Boot thay vì tập trung xây dựng một hệ thống
nghiệp vụ có phạm vi quá lớn.

---

## 3. Bài toán minh họa

Nhóm lựa chọn bài toán:

**Hệ thống quản lý khóa học trực tuyến**

Hệ thống được sử dụng như một case study để nhóm áp dụng và minh họa
các kiến thức Spring Boot đã nghiên cứu.

Đây không phải trọng tâm chính của đề tài.

Các chức năng nghiệp vụ chỉ được xây dựng ở mức đủ để chứng minh
việc ứng dụng các cơ chế của Spring Boot.

---

# 4. TẦNG 1 — KIẾN THỨC BẮT BUỘC LÕI

Theo yêu cầu môn học, nhóm phải nghiên cứu và chứng minh được
các nội dung cốt lõi sau.

## 4.1. IoC và Dependency Injection

Nhóm cần tìm hiểu:

- IoC là gì.
- Spring Container là gì.
- Bean là gì.
- Bean được tạo ra khi nào.
- Bean do ai quản lý.
- Dependency Injection hoạt động như thế nào.
- Constructor Injection.
- Sự khác nhau giữa Dependency Injection và tự sử dụng `new`.

Mục tiêu:

Giải thích được câu hỏi:

> Bean này được tạo ra lúc nào và ai tạo?
> Nếu tự `new` object thì mất những gì từ Spring?

---

## 4.2. REST Controller và Service Layer

Nhóm xây dựng kiến trúc:

Controller
→ Service
→ Repository
→ Database

Trong đó:

- Controller chịu trách nhiệm tiếp nhận HTTP Request.
- Service chứa business logic.
- Repository chịu trách nhiệm truy cập dữ liệu.
- Không đặt business logic trực tiếp trong Controller.

Nhóm phải hiểu được lý do tại sao các tầng cần được tách riêng.

---

## 4.3. Spring Data JPA

Nhóm nghiên cứu:

- Entity.
- Repository.
- JpaRepository.
- Mapping Entity với Database.
- Quan hệ giữa các Entity.
- Cơ chế Spring Data JPA sinh câu SQL.
- Các thao tác CRUD thông qua Repository.

Nhóm phải có khả năng quan sát và giải thích được câu SQL
mà Hibernate/JPA sinh ra.

---

# 5. NỘI DUNG CHỌN THÊM

Nhóm lựa chọn hai nội dung sau.

## 5.1. Bean Validation

Nghiên cứu cách Spring Boot kiểm tra dữ liệu đầu vào thông qua:

- `@Valid`
- `@NotNull`
- `@NotBlank`
- `@Size`
- `@Email`
- `@Min`
- `@Max`

Ứng dụng Bean Validation vào DTO của hệ thống quản lý khóa học.

Mục tiêu:

API có khả năng từ chối dữ liệu không hợp lệ trước khi
business logic được thực hiện.

---

## 5.2. Global Exception Handler

Nhóm nghiên cứu cơ chế xử lý lỗi tập trung thông qua:

- `@ControllerAdvice`
- `@ExceptionHandler`
- Custom Exception
- Error Code
- Error Response

Mục tiêu:

Các API trả lỗi theo một cấu trúc thống nhất thay vì
mỗi Controller tự xử lý lỗi theo một cách khác nhau.

---

# 6. TẦNG 3 — KỸ THUẬT NÂNG CAO

Nhóm lựa chọn:

## Cache và tối ưu truy vấn

Công nghệ dự kiến:

- Spring Cache
- Redis

Các nội dung nghiên cứu:

- Cache là gì.
- Cache-aside.
- Cache hit.
- Cache miss.
- TTL.
- Cache invalidation.
- `@Cacheable`.
- `@CacheEvict`.
- `@CachePut`.

Nhóm sẽ lựa chọn một hoặc một số API phù hợp để áp dụng Cache.

Ví dụ:

GET /api/courses/{id}

Nhóm thực hiện đo:

Không Cache
→ Response Time A

Có Cache
→ Response Time B

Từ đó đánh giá ảnh hưởng của Cache tới hiệu năng hệ thống.

---

# 7. CHUẨN KỸ NGHỆ PHẦN MỀM — TẦNG 2

Nhóm dự kiến áp dụng:

- Git.
- Feature Branch.
- Pull Request.
- Code Review.
- Docker.
- Docker Compose.
- Environment Variables.
- Swagger / OpenAPI.
- Testing.
- GitHub Actions.
- README.
- Quản lý secret.
- Một số nguyên tắc bảo mật cơ bản.
- AI Disclosure.

Không commit trực tiếp vào branch `main`.

---

# 8. PHẠM VI NGHIỆP VỤ

Case study quản lý khóa học trực tuyến sử dụng một Entity `Course` độc lập.
API minh họa thao tác tạo, xem chi tiết, xem danh sách, cập nhật và xóa Course.

Các chức năng nghiệp vụ chỉ được xây dựng ở mức đủ để minh họa
Spring Boot và các kỹ thuật được nghiên cứu.

Nhóm không đặt mục tiêu xây dựng một nền tảng E-Learning hoàn chỉnh.

---

# 9. MỤC TIÊU CUỐI CÙNG

Sau khi hoàn thành đề tài, nhóm phải có khả năng:

1. Giải thích bản chất của Spring Boot.
2. Giải thích IoC và Dependency Injection.
3. Xây dựng REST API theo kiến trúc Controller-Service-Repository.
4. Sử dụng Spring Data JPA để thao tác Database.
5. Áp dụng Bean Validation.
6. Xử lý Exception tập trung.
7. Áp dụng Cache bằng Spring Cache và Redis.
8. Đo và so sánh hiệu năng trước và sau Cache.
9. Viết Test cho một số thành phần quan trọng.
10. Đóng gói và chạy lại hệ thống.
11. Đọc và sử dụng tài liệu chính thức của Spring.
12. Có khả năng giải thích và sửa phần code của nhóm tại chỗ.
