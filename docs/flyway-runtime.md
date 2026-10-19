# Hợp đồng Flyway của bản tích hợp

Flyway là cơ chế tạo/cập nhật schema của runtime MySQL. Hibernate kiểm tra schema
bằng `validate`; SQL initializer không chạy `data.sql`. Cấu hình này đã được tích
hợp qua PR #29. Không sửa migration V1/V2 đã áp dụng vào một database.

## Profile, dữ liệu mẫu và biến môi trường

| Cách chạy | Flyway locations | Dữ liệu mẫu | Hibernate / SQL init |
| --- | --- | --- | --- |
| Host/IDE, không chọn active profile | `db/migration`, `db/demo` do default profile `dev` | V2 nạp demo một lần | `validate` / `never` |
| Active profile `prod`, database mới chưa có V2 | `db/migration` từ cấu hình gốc | Không nạp demo | `validate` / `never` |
| Compose backend trong README | Hai locations được khai báo trực tiếp trong environment service backend | Có V2, phục vụ local | `validate` / `never` |
| Profile `m11-migration` | `db/migration`, database lab riêng | Không nạp demo | `validate` / `never` |
| Test H2 trong `src/test/resources` | Flyway tắt có chủ đích | `test-data.sql` | `create-drop` / `always` |

`prod` ở đây là một active profile tường minh: hiện chưa có file
`application-prod.yml`, nên nó dùng cấu hình gốc và không kích hoạt default `dev`.
Đây là cách chọn schema-only, chưa phải một cấu hình vận hành production đầy đủ.
Nếu thêm cấu hình profile sau này, cần giữ hợp đồng khởi tạo database này.

Với database **đã từng áp dụng V2**, tiếp tục cung cấp location `db/demo` để Flyway
validate history đầy đủ; V2 không chạy lại chỉ vì location vẫn còn. Không chuyển
database đó sang cấu hình schema-only rồi xóa/repair history để né lỗi.

Các biến cho host/IDE:

| Biến | Ý nghĩa |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC URL tới database mới hoặc database có history Flyway hợp lệ |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Tài khoản database thực tế; không đưa mật khẩu vào Git/log |
| `REDIS_HOST`, `REDIS_PORT` | Redis dùng cho cache runtime |
| `SPRING_PROFILES_ACTIVE` | Chọn profile tường minh; để trống sẽ dùng default `dev` |
| `SPRING_FLYWAY_LOCATIONS` | Chỉ override sau khi đối chiếu history V1/V2 của database đích |

Compose backend dùng các biến `COURSE_DOCKER_HTTP_PORT`,
`COURSE_DOCKER_DB_PASSWORD`, `COURSE_DOCKER_ROOT_PASSWORD` theo README.
Đổi mật khẩu trong environment sau khi volume đã khởi tạo không tự đổi tài khoản MySQL.
Environment/command line có thể override YAML; phải kiểm tra lại cấu hình runtime
thật ở lượt CV7. Kiểm thử cấu hình không chứng minh các biến trên máy đã được cấp đúng.

## Database cũ có dữ liệu nhưng chưa có history Flyway

`baseline-on-migrate=false` là chủ đích. Không trỏ bản mới vào schema cũ rồi tự
baseline, dùng `repair`, đổi `ddl-auto=update` hoặc xóa volume để chạy cho qua.
Mỗi nhóm dữ liệu cũ cần một kế hoạch chuyển đổi được review riêng.

Quy trình chuẩn bị và chuyển đổi:

1. Ghi SHA ứng dụng cũ, JDBC URL/schema, phiên bản MySQL và số hàng của từng bảng.
   Kiểm tra `flyway_schema_history` nếu có. Xác định thời điểm ngừng ghi dữ liệu
   hoặc cơ chế đồng bộ thay đổi; thống nhất thời gian chuyển đổi và người thực hiện.
2. Sao lưu schema và dữ liệu bằng tài khoản phù hợp; lưu dump ở ngoài repo, kiểm tra
   phục hồi trên môi trường riêng. Giữ database/volume cũ để có đường quay lại.
   Không coi một tệp dump chưa thử phục hồi là bản sao lưu đã được kiểm chứng.
3. Tạo database đích hoặc Compose project/volume **mới**. Không dùng lại tên volume
   của nhóm. Khi cần giữ dữ liệu thật, chọn schema-only để không nạp V2 demo vào đích.
   Để Flyway tạo V1 trên đích mới, giữ Hibernate `validate` và SQL init `never`.
4. Đối chiếu dữ liệu nguồn với schema đích: tên cột/kiểu/nullability, PK/FK,
   Category name, Student email, cặp Student/Course duy nhất và status Enrollment.
   Viết riêng bước làm sạch/chuyển dữ liệu có danh sách cột rõ ràng; giữ quan hệ ID,
   timestamp và kiểm tra giá trị AUTO_INCREMENT. Không restore nguyên DDL cũ đè lên V1.
5. Chuyển dữ liệu trên bản thử nghiệm; đối chiếu số hàng, quan hệ, UNIQUE/FK và các
   API nghiệp vụ. Restart trên cùng đích để kiểm tra history/checksum và dữ liệu.
   Nếu không đạt thì dừng, giữ nguyên nguồn và sửa quy trình chuyển đổi.
6. Chỉ chuyển JDBC URL của ứng dụng sau khi bản thử đã đạt, dữ liệu cuối đã được
   đồng bộ và nhóm đã review. Lưu log, SHA và kết quả đối soát. Nếu phải quay lại,
   dùng bản ứng dụng và database cũ theo kế hoạch đã thống nhất; đối soát riêng các
   ghi phát sinh sau chuyển đổi trước khi mở lại hệ thống.

Đây là quy trình, không phải script ETL dùng chung cho mọi database. Nếu nhóm cần
adopt trực tiếp schema cũ bằng baseline, phải review diff schema với V1, chọn version
baseline đúng và thử trên bản sao trước. Bộ CV3 không tự thực hiện quyết định này.

## Kiểm chứng và bàn giao CV7

`FlywayRuntimeConfigurationTests` dùng Spring ConfigData để nạp **YAML runtime
trong src/main/resources** theo profile kiểm soát, độc lập với YAML H2 của test.
Nó kiểm tra cơ chế khởi tạo schema, tách demo, cấu hình Compose, classpath Flyway/
MySQL và tính hợp lệ của Enrollment demo. Test không mở kết nối database, không
chạy migration SQL và không khởi động server/container. File dữ liệu minh chứng
được xuất khi cấp `-Dcv3.evidenceDir=<thư-mục-ngoài-repo>`.

CV7 dùng clone sạch tại SHA đã tích hợp CV3, project/volume mới và lệnh Compose
trong README để kiểm chứng thực tế: V1/V2 trong `flyway_schema_history`, Hibernate
validate thành công, API/MySQL/Redis, restart giữ checksum/history và dữ liệu.
Kết quả H2 hoặc kiểm thử cấu hình của CV3 không thay cho các bằng chứng MySQL này.

Nguồn chính thức:

- [Spring Boot 4.1 — Database initialization](https://docs.spring.io/spring-boot/4.1/how-to/data-initialization.html)
- [Spring Boot 4.1 — Externalized configuration](https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html)
- [Spring Boot 4.1 — Test utilities](https://docs.spring.io/spring-boot/4.1/reference/testing/test-utilities.html)
