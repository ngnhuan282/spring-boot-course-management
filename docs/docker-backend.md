# Chạy backend bằng Docker và Flyway

Từ thư mục gốc repo, một lệnh dựng image và khởi động backend, MySQL, Redis:

```powershell
docker compose -f docker-compose.backend.yml up --build -d --wait --wait-timeout 600
```

Docker Desktop cần dùng Linux containers, Compose v2.20 trở lên. Java/Maven
chạy trong build stage JDK 21; JAR được copy sang JRE 21 và chạy bằng user app.
Swagger: <http://127.0.0.1:8088/swagger-ui/index.html>.

```powershell
docker compose -f docker-compose.backend.yml ps
curl.exe -i http://127.0.0.1:8088/api/health
curl.exe -i http://127.0.0.1:8088/api/courses
docker compose -f docker-compose.backend.yml logs --no-color backend
```

## Cấu hình và dữ liệu

`application.yml` mặc định bật Flyway, tắt baseline, bật validation, dùng
Hibernate `ddl-auto=validate`, `defer-datasource-initialization=false` và
`spring.sql.init.mode=never`. `data.sql` không chạy trong runtime chính.
Profile mặc định `dev` chọn cả `db/migration` và `db/demo`; Compose cũng chọn
rõ hai location này. V1 tạo 4 bảng và các ràng buộc; V2 nạp 2 Category, 3 Course,
2 Student, 3 Enrollment cho thực hành local. V1/V2 đã áp dụng không bị sửa.
Các profile thí nghiệm migration riêng giữ location của chúng để không đổi
ý nghĩa kết quả lịch sử.

Khi restart backend trên cùng database, Flyway kiểm tra history/checksum và
không nạp lại V1/V2. Không dùng `repair`, tự bật baseline hoặc xóa volume để
cho qua lỗi migration. Database cũ do Hibernate tạo cần được chuyển đổi theo
kế hoạch riêng nếu phải giữ dữ liệu. Với cách chạy này, MySQL dùng named volume
riêng của project; collector dùng project/volume mới cho mỗi lượt thực nghiệm.
Nếu dùng lại database có V2, luôn giữ location chứa V2 khi validate.

Compose là cấu hình độc lập, gồm đúng 3 dịch vụ; không ghép với
`docker-compose.yml` cũ. MySQL/Redis không công bố cổng host. Backend dùng
`mysql:3306`, `redis:6379` và chỉ công bố HTTP trên loopback 8088→8080.
Health check MySQL thực hiện SELECT 1 có xác thực. Backend chờ MySQL/Redis
healthy; `/api/health` chỉ xác nhận tầng web nên cần kiểm tra API nghiệp vụ,
row MySQL và cache Redis tương ứng.

Mật khẩu mặc định trong Compose và `.env.example` là giá trị mẫu local. Có thể
cấp `COURSE_DOCKER_DB_PASSWORD` và `COURSE_DOCKER_ROOT_PASSWORD` trước khi tạo
volume lần đầu. Đổi env sau khi volume tồn tại không tự đổi mật khẩu MySQL.
Không commit mật khẩu thật. Có thể dùng tệp env riêng khi chạy thủ công:

```powershell
docker compose --env-file .env.example -f docker-compose.backend.yml up --build -d --wait --wait-timeout 600
```

Đổi cổng nếu cần:

```powershell
$env:COURSE_DOCKER_HTTP_PORT = '8089'
docker compose -f docker-compose.backend.yml up --build -d --wait --wait-timeout 600
```

Dừng project và giữ dữ liệu:

```powershell
docker compose -f docker-compose.backend.yml down
```

## Phạm vi kiểm chứng

Collector bên ngoài repo ghi SHA/nhánh, source ZIP, image ID/revision và trạng
thái project/volume trước lệnh đầu. Lệnh đầu là một `compose up --build` cho
cả 3 dịch vụ; không có lệnh build hoặc khởi động MySQL/Redis riêng trước đó.
Sau đó kiểm tra V1/V2, seed, FK/UNIQUE, API/DB/cache và restart backend.
Database mới được xác lập bằng volume chưa tồn tại và log tạo history; không
khai là đã chạy SELECT đếm 0 bảng trước backend trong lượt một lệnh này.

Dockerfile dùng `-DskipTests` để đóng gói. CI GitHub Actions chạy Maven verify
và lưu Surefire XML; kết quả CI phải gắn cùng SHA và không suy ra từ build image.
Test H2 dùng cấu hình riêng, Flyway tắt và Hibernate create-drop. Migration
MySQL được chứng minh bằng lượt Docker riêng. CI hiện chưa có publish/deploy,
vì vậy không ghi đã triển khai CD.

Nguồn: [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[Docker compose up](https://docs.docker.com/reference/cli/docker/compose/up/),
[startup order](https://docs.docker.com/compose/how-tos/startup-order/),
[multi-stage build](https://docs.docker.com/build/building/multi-stage/).
Đối chiếu ngày 16/10/2026.
