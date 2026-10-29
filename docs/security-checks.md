# Kiểm chứng bảo mật Course độc lập

`StandaloneApiSecurityMySqlTests` kiểm tra CRUD với chuỗi mang cú pháp SQL,
CORS và dữ liệu HTML trong JSON. Các ca dùng Spring MVC, Service và JPA thật
trên MySQL/Redis riêng do Testcontainers tạo.

## Nội dung kiểm chứng

| Ca kiểm thử | Nội dung kiểm tra |
| --- | --- |
| CRUD với chuỗi SQL | POST title `' OR 1=1 -- ` và description `x' UNION SELECT 1 -- `; PUT title thành `x'); DROP TABLE courses; -- `. POST trả 201, PUT/GET trả 200. JSON và MySQL giữ nguyên chuỗi; chỉ thêm hoặc sửa dòng được yêu cầu, giữ Course đối chứng, dữ liệu mẫu và các dòng khác. |
| GET và Origin | Thử request không có Origin, cùng Origin và khác Origin. Response trả 200/JSON, không có `Access-Control-Allow-Origin` hoặc `Access-Control-Allow-Credentials`; dữ liệu MySQL không đổi. |
| CORS preflight | OPTIONS có đủ ba header Origin, Access-Control-Request-Method và Access-Control-Request-Headers. Với Spring Framework 7.0.9 khi không có cấu hình CORS, response trả 200 nhưng không cấp hai header cho phép nêu trên; dữ liệu MySQL không đổi. |
| HTML trong JSON | POST description `<script>alert('api-security')</script><b>sample</b>`, rồi GET theo ID trả về. Kiểm tra status 201/200, Content-Type `application/json` và giá trị sau parse JSON khớp nguyên chuỗi trong MySQL. |

MySQL dùng `mysql:8.4`, Redis dùng `redis:7.4-alpine`, với database và cổng riêng.
Flyway áp dụng V1/V2/V3; Hibernate kiểm tra schema bằng `validate`. JDBC đọc dữ
liệu sau commit; dữ liệu mẫu và Course đối chứng được giữ để so sánh.

## Cách chạy

Yêu cầu Java 21 và Docker daemon đang hoạt động. Từ thư mục `backend`:

```powershell
.\mvnw.cmd -B clean '-Dtest=StandaloneApiSecurityMySqlTests' test
.\mvnw.cmd -B clean verify
```

## Phạm vi kết luận

Kiểm chứng SQL injection tập trung vào đường ghi và đọc CRUD hiện có,
đối chiếu đúng ID và dữ liệu trong MySQL.

HTTP 200 của preflight không tự cấp quyền cross-origin khi thiếu header CORS
cho phép. CORS không thay thế xác thực hoặc bảo vệ CSRF.

Ca HTML/JSON xác nhận API trả HTML như dữ liệu JSON. MockMvc không thực thi
JavaScript; kết quả chưa đánh giá việc render ở frontend hay toàn bộ tình huống XSS.
