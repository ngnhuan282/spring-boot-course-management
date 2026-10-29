# Kiểm chứng bảo mật bản tích hợp

`ApiSecurityMySqlTests` kiểm tra hai truy vấn JPA, CRUD với chuỗi mang cú pháp
SQL, CORS và HTML trong JSON. Các ca dùng Service/Repository thật trên
MySQL/Redis riêng do Testcontainers tạo; các ca HTTP đi qua MockMvc với context thật.

## Nội dung kiểm chứng

| Ca kiểm thử | Nội dung kiểm tra |
| --- | --- |
| Keyword | Gọi `searchByKeyword` với đối chứng bình thường, chuỗi không khớp, `' OR 1=1 -- ` và `x' UNION SELECT 1 -- `. Thêm fixture chứa nguyên chuỗi SQL rồi kiểm tra đúng tập ID; snapshot MySQL không đổi sau từng truy vấn. |
| Tên Category | Gọi `findByCategoryName` với hai Category đối chứng và hai chuỗi SQL trên. Khi có Category mang nguyên chuỗi SQL, chỉ trả Course tham chiếu Category ấy; snapshot MySQL không đổi. |
| CRUD với chuỗi SQL | POST title `' OR 1=1 -- `, PUT title thành `x'); DROP TABLE courses; -- `. POST trả 201, PUT/GET trả 200; JSON và JDBC giữ nguyên chuỗi. Chỉ thêm một Course rồi sửa đúng dòng, giữ các cột còn lại ngoài timestamp cập nhật và dữ liệu không liên quan. |
| GET và Origin | Thử không có Origin, cùng Origin và khác Origin: 200/JSON, không có `Access-Control-Allow-Origin` hoặc `Access-Control-Allow-Credentials`; dữ liệu không đổi. |
| CORS preflight | OPTIONS đủ ba header Origin, Access-Control-Request-Method và Access-Control-Request-Headers. Với Spring Framework 7.0.9 khi không có cấu hình CORS, response trả 200 nhưng không cấp hai header cho phép nêu trên; dữ liệu không đổi. Phiên bản runtime được ghi vào log. |
| HTML trong JSON | POST description `<script>alert('api-security')</script><b>sample</b>`, rồi GET theo ID trả về. Kiểm tra status 201/200, Content-Type `application/json` và giá trị sau parse JSON khớp nguyên chuỗi trong MySQL. |

MySQL dùng `mysql:8.4`, Redis dùng `redis:7.4-alpine`, database và cổng riêng.
Flyway áp dụng migration thật, Hibernate `validate`, SQL init `never`, giữ cache
Redis hiện có. Mỗi test có fixture riêng; JDBC đọc connection mới sau commit,
so các bảng Course/Category/Student/Enrollment và giữ dữ liệu đối chứng.

## Cách chạy

Yêu cầu Java 21 và Docker daemon đang hoạt động. Từ thư mục `backend`:

```powershell
.\mvnw.cmd -B clean '-Dtest=ApiSecurityMySqlTests' test
.\mvnw.cmd -B clean verify
```

## Phạm vi kết luận

Hai ca truy vấn gọi trực tiếp Repository, không phải endpoint tìm kiếm HTTP.
Kết quả chỉ bao phủ các truy vấn, payload và đường CRUD đã thử; wildcard của
`LIKE` là hành vi riêng, không dùng làm bằng chứng SQL injection.

HTTP 200 của preflight không tự cấp quyền cross-origin khi thiếu header CORS
cho phép. CORS không thay thế xác thực hoặc bảo vệ CSRF.

HTML được trả như dữ liệu JSON. MockMvc không thực thi JavaScript; kết quả chưa
đánh giá việc render ở frontend hay toàn bộ các tình huống XSS.
