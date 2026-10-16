# M16 — Minh chứng Migration, Profile và Service Unit Test

## Phạm vi và phiên bản nguồn

Bộ minh chứng ghi lại đợt kiểm chứng ngày **16/10/2026 (UTC+07:00)** đối với ứng dụng Spring Boot quản lý khóa học tích hợp. Phần mã nguồn thực nghiệm là mã đã có trong thư mục `backend/`; không tạo dự án hoặc module sản phẩm mới.

- Repository: `ngnhuan282/spring-boot-course-management` (tên repository dùng để truy vết kỹ thuật, không đại diện tên người viết báo cáo).
- Nhánh thực hiện M16: `lab/m16-migration-profile-unit-test`.
- Commit `develop` được ghi nhận **trước khi tạo nhánh M16**: `c8cc695edc24e4ba67356a538ac6ea59519e6251`.

## Môi trường thực nghiệm

| Thành phần | Phiên bản / điều kiện ghi nhận |
| --- | --- |
| Hệ điều hành | Windows 11; chạy backend trên máy host |
| Java | Eclipse Temurin OpenJDK **21.0.12.1** |
| Maven Wrapper | Apache Maven **3.9.16** |
| Spring Boot | **4.1.1** |
| Cơ sở dữ liệu | MySQL **8.4.11** trên Docker Compose |
| Database thử nghiệm | `m16_migration_evidence`, tạo rỗng và tách khỏi `course_management` |
| Redis | Dịch vụ Redis của Compose; cache tắt trong profile migration |
| Active profile | `m11-migration`; HTTP port **8083** |
| Unit test | JUnit Jupiter **6.0.3**, Mockito **5.23.0** (xác nhận bằng Maven dependency tree) |

## Mã nguồn và cấu hình sử dụng

- Migration: `backend/src/main/resources/db/migration/V1__create_course_management_schema.sql`.
- Profile: `backend/src/main/resources/application-m11-migration.yml`.
- Test source: `backend/src/test/java/com/ccnlthd/course_management/CourseCrudTests.java` và `BusinessExceptionTests.java`.
- Trong profile thực nghiệm: `spring.flyway.enabled=true`, `spring.flyway.baseline-on-migrate=false`, `spring.flyway.validate-on-migrate=true`, `spring.jpa.hibernate.ddl-auto=validate`; không dùng `ddl-auto=update` thay migration.

Migration V1 tạo bốn bảng nghiệp vụ: `categories`, `courses`, `students`, `enrollments`; gồm ba ràng buộc UNIQUE và ba FOREIGN KEY, ngoài các khóa chính. Bảng `flyway_schema_history` do Flyway quản lý được tạo thêm sau lần chạy đầu.

## Cách tái lập trên database rỗng (Windows CMD)

1. Từ thư mục gốc repository, khởi động dịch vụ theo hướng dẫn Docker Compose của nhóm. 
2. Trong MySQL, tạo **database riêng và rỗng**; không áp dụng V1 trực tiếp lên database đang chứa dữ liệu sản phẩm:

   ```sql
   CREATE DATABASE m16_migration_evidence
     CHARACTER SET utf8mb4
     COLLATE utf8mb4_0900_ai_ci;
   GRANT ALL PRIVILEGES ON m16_migration_evidence.* TO 'course_user'@'%';
   SHOW TABLES FROM m16_migration_evidence;
   ```

3. Mở CMD tại `backend/`, đặt biến môi trường **trong cửa sổ dùng để chạy migration**. Thay `YOUR_LOCAL_PASSWORD` bằng mật khẩu MySQL được cấu hình trên máy; tuyệt đối không commit giá trị này:

   ```bat
   set "SPRING_PROFILES_ACTIVE=m11-migration"
   set "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:13306/m16_migration_evidence"
   set "SPRING_DATASOURCE_USERNAME=course_user"
   set "SPRING_DATASOURCE_PASSWORD=YOUR_LOCAL_PASSWORD"
   .\mvnw.cmd spring-boot:run
   ```

4. Xác nhận log có `The following 1 profile is active: "m11-migration"`, `Successfully applied 1 migration` và ứng dụng khởi động thành công. Dừng ứng dụng bằng `Ctrl+C`, chạy lại bằng cùng lệnh (giữ nguyên database). Lượt thứ hai phải có `Schema ... is up to date. No migration necessary.`
5. Kiểm tra bảng sau migration trong MySQL:

   ```sql
   USE m16_migration_evidence;
   SHOW TABLES;
   SELECT installed_rank, version, description,
          script, installed_on, success
     FROM flyway_schema_history ORDER BY installed_rank;
   SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
     FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = 'm16_migration_evidence'
      AND TABLE_NAME IN ('categories','courses','students','enrollments')
    ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;
   ```

**Cơ sở dữ liệu đã tồn tại dữ liệu:** cần đánh giá schema/baseline và quy trình chuyển đổi riêng; không giả định script `CREATE TABLE` có thể chạy an toàn trên các bảng sẵn có. Với profile kiểm chứng này `baseline-on-migrate=false`.

## Chạy test và đóng gói (Windows CMD)

Trước khi chạy test, dùng cửa sổ CMD mới **hoặc xóa các biến ghi đè dành cho migration** trong cửa sổ cũ; nếu giữ lại profile/database này, một số Spring-context test có thể thất bại do dùng nhầm môi trường:

```bat
set "SPRING_PROFILES_ACTIVE="
set "SPRING_DATASOURCE_URL="
set "SPRING_DATASOURCE_USERNAME="
set "SPRING_DATASOURCE_PASSWORD="
```

Từ `backend/` chạy:

```bat
.\mvnw.cmd "-Dtest=CourseCrudTests#createsCourseWhenCategoryExists,BusinessExceptionTests#shouldThrowCourseNotFoundWhenCourseDoesNotExist+shouldThrowCategoryNotFoundWhenCreatingCourseWithMissingCategory" test
.\mvnw.cmd dependency:tree "-Dincludes=org.junit.jupiter:*,org.mockito:*"
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

Các kiểm thử Service được chọn tạo mock trực tiếp bằng Mockito, không kết nối MySQL/Redis hay khởi động toàn bộ Spring context. Các loại kiểm thử web/integration trong tổng lượt chạy phải được phân biệt với bộ ba unit test này.

## Kết quả thực tế ngày 16/10/2026

| Bước | Kết quả quan sát |
| --- | --- |
| Flyway lần đầu | Áp dụng thành công V1 trên schema rỗng; Hibernate khởi tạo JPA thành công với `ddl-auto=validate` |
| Flyway lần hai | Schema đang ở version 1; `No migration necessary` |
| Schema history | Một dòng version `1`, script `V1__create_course_management_schema.sql`, `success=1` |
| SQL constraint | Bốn bảng nghiệp vụ; 3 UNIQUE và 3 FOREIGN KEY (không tính 4 PRIMARY KEY) |
| Ba Service unit test được chọn | **Tests run: 3, Failures: 0, Errors: 0, Skipped: 0**; BUILD SUCCESS |
| Maven `clean test` | **Tests run: 81, Failures: 0, Errors: 0, Skipped: 0**; BUILD SUCCESS |
| Maven `clean package` | **Tests run: 81, Failures: 0, Errors: 0, Skipped: 0**; BUILD SUCCESS; tạo JAR trong `backend/target/` |

Lượt 81 test là tổng bộ kiểm thử backend (gồm nhiều loại test); **không phải 81 Service unit test**. Kết quả thực nghiệm ngày 16/10 không thay thế các số liệu lịch sử được ghi cho các lượt chạy khác ngày/commit.

## Danh mục ảnh minh chứng

| Tệp | Nội dung chứng minh |
| --- | --- |
| `evidence/flyway-first-run.png` | Flyway áp dụng V1 ở lượt đầu và kết nối database thực nghiệm |
| `evidence/flyway-second-run.png` | Lượt khởi động lại xác nhận không áp dụng V1 lần nữa |
| `evidence/flyway-schema-history.png` | `SHOW TABLES` và kết quả `flyway_schema_history` |
| `evidence/service-unit-tests.png` | 3 Service unit test được chọn, số liệu Surefire |
| `evidence/full-tests.png` | 81 test thành công trong lượt `clean test` |
| `evidence/clean-package.png` | 81 test thành công và tạo JAR trong lượt `clean package` |
| `evidence/active-profile.png` | Log xác nhận Java 21.0.12.1 và active profile `m11-migration` |

Mã SQL, profile, các phương thức test và log dependency được đối chiếu từ `backend/` và ảnh trong báo cáo M16; không sao chép lại toàn bộ backend vào thư mục này.
