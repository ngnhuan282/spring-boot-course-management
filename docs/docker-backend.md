# Chạy backend Spring Boot bằng Docker

Từ thư mục gốc repository, cần Git và Docker Desktop đang chạy Linux containers.
Không cần cài Java hoặc Maven trên host cho cách chạy này.

```powershell
docker compose -f docker-compose.backend.yml up --build -d --wait --wait-timeout 300
```

Ứng dụng: `http://127.0.0.1:8088/swagger-ui/index.html`.

```powershell
docker compose -f docker-compose.backend.yml ps
curl.exe -i http://127.0.0.1:8088/api/health
curl.exe -i http://127.0.0.1:8088/api/courses
docker compose -f docker-compose.backend.yml logs --no-color backend
```

## Cấu hình container

`docker-compose.backend.yml` là cấu hình độc lập gồm backend, MySQL và Redis.
Luôn chỉ định `-f docker-compose.backend.yml`. Project mặc định là
`course-management-docker-flyway`, dùng volume `docker_backend_mysql_flyway_data`
trong project này. Tên mới tách dữ liệu khỏi cấu hình Docker thử nghiệm trước đó.
MySQL/Redis không công bố cổng host; backend kết nối qua `mysql:3306` và
`redis:6379`. Cổng HTTP chỉ được công bố trên loopback của máy chạy Docker.

Dockerfile có hai stage: JDK 21 chạy Maven Wrapper để tạo JAR, sau đó copy JAR
vào image JRE 21 và chạy bằng user `app` (UID 10001). Không cần build JAR trước
trên host. `-DskipTests` chỉ dùng cho bước đóng gói image vì các test
Testcontainers cần Docker daemon riêng; kết quả kiểm thử được thu bằng CI hoặc
lượt chạy Maven riêng.

MySQL/Redis có health check; backend chờ hai dịch vụ healthy. Health của backend
gọi `/api/health`; endpoint này chỉ xác nhận tầng web. Minh chứng kết nối dữ
liệu cần có API nghiệp vụ, row MySQL và cache Redis tương ứng.

## Flyway và dữ liệu mẫu

Compose cấp các biến môi trường sau để ghi đè cấu hình mặc định:

| Thuộc tính | Giá trị Docker | Vai trò |
|---|---|---|
| `spring.flyway.enabled` | `true` | Chạy migration khi khởi động backend. |
| `spring.flyway.locations` | `classpath:db/migration,classpath:db/demo` | V1 tạo schema; V2 nạp dữ liệu mẫu local. |
| `spring.flyway.baseline-on-migrate` | `false` | Không tự bỏ qua schema cũ chưa có lịch sử Flyway. |
| `spring.flyway.validate-on-migrate` | `true` | Kiểm tra migration đã áp dụng trước khi migrate. |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate đối chiếu mapping với schema, không tạo/sửa bảng. |
| `spring.jpa.defer-datasource-initialization` | `false` | Bỏ cơ chế hoãn khởi tạo SQL theo Hibernate. |
| `spring.sql.init.mode` | `never` | Không chạy `data.sql` trong Docker. |

V1 hiện có được giữ nguyên. `db/demo/V2__seed_demo_data.sql` dùng INSERT thông
thường, tạo 2 Category, 3 Course, 2 Student và 3 Enrollment trên database mới.
Flyway ghi phiên bản, checksum và trạng thái vào `flyway_schema_history`.
Khi backend khởi động lại trên cùng database, migration đã áp dụng không được
chạy lại. Migration đã áp dụng cần được giữ nguyên; thay đổi sau này dùng một
phiên bản migration mới.

Thư mục `db/demo` chỉ có hiệu lực khi được thêm vào `flyway.locations`, như cấu
hình Docker này. Cấu hình migration chỉ đọc `db/migration` vẫn chạy riêng V1.
`application.yml` và `data.sql` mặc định chưa được thay đổi; báo cáo cần nêu rõ
cấu hình Docker thực sự được dùng thay vì khẳng định mọi môi trường đều đã bật
Flyway. Không kích hoạt profile thí nghiệm migration riêng cho lượt Docker vì
profile đó tắt Redis và dùng cổng khác.

Không trỏ cấu hình này vào volume từng được Hibernate tạo schema. Nếu database
đã có bảng nhưng chưa có lịch sử Flyway, cần lập phương án chuyển đổi dữ liệu
riêng; không bật baseline hoặc xóa volume để che lỗi. Lượt thu tự động dùng
project và volume mới nên có thể kiểm chứng từ database trống mà giữ dữ liệu cũ.

## Kiểm tra khởi động lại

```powershell
docker compose -f docker-compose.backend.yml restart backend
docker compose -f docker-compose.backend.yml up -d --no-build --wait --wait-timeout 300
docker compose -f docker-compose.backend.yml logs --no-color --tail 100 backend
```

Đối chiếu lịch sử V1/V2, checksum, thời điểm cài đặt và số dòng trước/sau restart.
Collector bên ngoài repo thực hiện những kiểm tra này và lưu log/JSON/HTTP/PNG.
Một lượt PASS chỉ phản ánh commit và điều kiện được ghi trong metadata.

Mật khẩu mặc định chỉ dành cho thực hành local. Có thể cấp
`COURSE_DOCKER_DB_PASSWORD` và `COURSE_DOCKER_ROOT_PASSWORD` trước khi database
được tạo lần đầu. Đổi biến sau khi volume có dữ liệu không tự đổi mật khẩu
MySQL trong volume đó. Không đưa mật khẩu thật vào Git hoặc ảnh minh chứng.

Đổi cổng nếu 8088 đã được dùng:

```powershell
$env:COURSE_DOCKER_HTTP_PORT = '8089'
docker compose -f docker-compose.backend.yml up --build -d --wait --wait-timeout 300
```

Dừng project và giữ dữ liệu:

```powershell
docker compose -f docker-compose.backend.yml down
```

Nguồn: [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/),
[startup order](https://docs.docker.com/compose/how-tos/startup-order/),
[compose up](https://docs.docker.com/reference/cli/docker/compose/up/).
Đối chiếu ngày 16/10/2026.
