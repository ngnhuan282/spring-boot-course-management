# Tuần 4: task 28, 29, 30 về Redis

Tài liệu này đối chiếu phần việc của thành viên 3 trong bảng phân công với mã nguồn đã thực hiện. Có hai phiên bản: nhánh sản phẩm `feat/week4-redis-cache-setup` và nhánh demo Course standalone `refactor/course-standalone`. Hai nhánh có mã nguồn riêng; cùng một đường dẫn có thể chứa nội dung khác nhau.

Bảng phân công vẫn ghi trạng thái ban đầu. Kết quả dưới đây dựa trên mã nguồn và [minh chứng kiểm thử của nhánh sản phẩm](cache-evidence.md). Nhánh demo có một file `docs/week4/cache-evidence.md` riêng.

## Task 28: Thiết lập Spring Cache và Redis

**Yêu cầu:** thêm thư viện Spring Cache và Spring Data Redis, chạy Redis bằng Docker Compose, bật cơ chế bộ nhớ đệm, cấu hình kết nối và thời gian tồn tại của dữ liệu.

**Đã thực hiện:** cả hai nhánh đều chạy Redis phiên bản 7.4 cùng MySQL trong Docker Compose. Cấu hình ứng dụng kết nối Redis tại máy đang chạy ứng dụng, cổng mặc định 6379. Dữ liệu chi tiết khóa học được lưu theo định dạng JavaScript Object Notation, có thể đọc bằng `redis-cli`, và được giải mã lại thành đối tượng `CourseResponse`. Thời gian tồn tại mặc định là **10 phút**; có thể thay đổi bằng biến môi trường.

Nhánh sản phẩm dùng tên vùng nhớ đệm `courseDetails` và biến `COURSE_CACHE_TTL`. Nhánh demo dùng tên `standaloneCourseDetails` và biến `APP_CACHE_COURSE_DETAIL_TTL`. Hai tên riêng giúp phân biệt khóa của hai phiên bản nếu cùng dùng một Redis.

## Task 29: Lưu kết quả đọc chi tiết khóa học

**Yêu cầu:** áp dụng annotation `@Cacheable` cho yêu cầu đọc và chứng minh lần chưa có dữ liệu trong bộ nhớ đệm, lần đã có dữ liệu, số câu lệnh truy vấn và khóa trong Redis.

**Đã thực hiện:** phương thức `getCourseById` của `CourseServiceImpl` xử lý yêu cầu `GET /api/courses/{id}`. Giá trị `{id}` là mã định danh khóa học. Redis lưu theo khóa `courseDetails::{id}` ở nhánh sản phẩm và `standaloneCourseDetails::{id}` ở nhánh demo.

Khi khóa chưa tồn tại, Spring Cache cho phép phương thức đọc cơ sở dữ liệu, tạo `CourseResponse` rồi lưu kết quả vào Redis. Khi khóa đã tồn tại, Spring Cache trả đối tượng đã lưu và không gọi truy vấn lấy khóa học lần nữa. Đây là cách lưu dữ liệu sau lần đọc đầu tiên ở tầng ứng dụng. Không lưu kết quả khi khóa học không tồn tại. Danh sách khóa học và danh sách danh mục không thuộc phạm vi được lưu trong bộ nhớ đệm.

**Minh chứng:** các bài kiểm thử tích hợp dùng Redis thật để kiểm tra khóa, nội dung, thời gian tồn tại và số câu lệnh truy vấn. Trong kết quả đã lưu, nhánh sản phẩm ghi 2 câu lệnh sau lần chưa có dữ liệu và vẫn là 2 sau lần đã có dữ liệu; nhánh demo ghi 1 câu lệnh cho lần chưa có dữ liệu và 0 câu lệnh cho lần đã có dữ liệu vì bộ đếm được đặt lại giữa hai lượt. Cả hai cách đo đều cho thấy lần đọc từ Redis không phát sinh thêm câu lệnh truy vấn.

## Task 30: Xóa dữ liệu cũ trong bộ nhớ đệm

**Yêu cầu:** sau khi cập nhật hoặc xóa khóa học, yêu cầu đọc tiếp theo không được trả thông tin cũ.

**Đã thực hiện:** khi cập nhật hoặc xóa khóa học thành công, `CourseServiceImpl` yêu cầu `CourseDetailCacheInvalidator` xóa khóa theo mã định danh. Việc xóa chỉ có hiệu lực **sau khi giao dịch cơ sở dữ liệu hoàn tất thành công**. Nếu giao dịch bị hủy và thay đổi dữ liệu không được lưu, nội dung cũ trong Redis vẫn còn. Cả hai nhánh đều có kiểm thử cho trường hợp giao dịch thành công, giao dịch bị hủy và giao dịch bao ngoài.

Ở nhánh sản phẩm, `CourseResponse` chứa tên danh mục. Vì vậy, khi `CategoryServiceImpl` cập nhật danh mục, toàn bộ vùng nhớ đệm chi tiết khóa học được xóa sau khi giao dịch hoàn tất; yêu cầu đọc tiếp theo nhận tên danh mục mới. Nhánh demo không có chức năng Category, nên chỉ cần xóa khóa của khóa học được sửa hoặc xóa. Giải pháp này tương đương mục tiêu của annotation `@CacheEvict` trong bảng phân công, nhưng dùng một thành phần riêng để kiểm soát thời điểm xóa.

## Các thư mục có vai trò gì

| Thư mục | Vai trò |
| --- | --- |
| `backend/src/main/java/com/ccnlthd/course_management/config/` | Chứa cấu hình bật Spring Cache, chọn cách lưu dữ liệu vào Redis và thời gian tồn tại. |
| `backend/src/main/java/com/ccnlthd/course_management/controller/` | Nhận yêu cầu từ người dùng và chuyển tới tầng xử lý nghiệp vụ. |
| `backend/src/main/java/com/ccnlthd/course_management/service/` và `backend/src/main/java/com/ccnlthd/course_management/service/impl/` | Chứa phương thức đọc khóa học, chú thích lưu bộ nhớ đệm và logic xóa dữ liệu cũ sau giao dịch. |
| `backend/src/main/java/com/ccnlthd/course_management/entity/` | Chứa các đối tượng biểu diễn bảng dữ liệu khóa học và danh mục. |
| `backend/src/main/java/com/ccnlthd/course_management/repository/` | Truy vấn cơ sở dữ liệu khi không tìm thấy dữ liệu trong Redis. |
| `backend/src/main/java/com/ccnlthd/course_management/dto/response/` | Chứa đối tượng dữ liệu trả về cho người dùng; `CourseResponse` cũng là giá trị được lưu trong Redis. |
| `backend/src/test/` | Chứa bài kiểm thử và cấu hình dữ liệu kiểm thử. |
| `docs/week4/` | Chứa tài liệu đối chiếu nhiệm vụ và kết quả kiểm chứng. |

Các thư mục trên có sẵn trong cấu trúc dự án. Bảng mô tả vai trò của chúng; không có nghĩa mọi file trong từng thư mục đều được thay đổi cho ba nhiệm vụ này.

## Vai trò từng file ở nhánh sản phẩm

| File | Chức năng cụ thể |
| --- | --- |
| [docker-compose.yml](../../docker-compose.yml) | Khai báo dịch vụ MySQL và Redis, cổng kết nối, kiểm tra tình trạng hoạt động của từng dịch vụ. |
| [.env.example](../../.env.example) | Mẫu tên biến môi trường cho MySQL, địa chỉ Redis, cổng Redis và thời gian tồn tại của bộ nhớ đệm. |
| [backend/pom.xml](../../backend/pom.xml) | Khai báo thư viện Spring Cache, Spring Data Redis, cơ sở dữ liệu H2 và Testcontainers phục vụ kiểm thử. |
| [backend/src/main/resources/application.yml](../../backend/src/main/resources/application.yml) | Chọn Redis làm nơi lưu bộ nhớ đệm, đặt tên `courseDetails`, cấu hình kết nối và thời gian tồn tại mặc định 10 phút. |
| [backend/src/main/java/com/ccnlthd/course_management/config/CourseCacheConfig.java](../../backend/src/main/java/com/ccnlthd/course_management/config/CourseCacheConfig.java) | Bật bộ nhớ đệm, cấu hình thứ tự xử lý so với giao dịch, cách chuyển `CourseResponse` thành văn bản có cấu trúc và bộ quản lý bộ nhớ đệm có nhận biết giao dịch. |
| [backend/src/main/java/com/ccnlthd/course_management/controller/CourseController.java](../../backend/src/main/java/com/ccnlthd/course_management/controller/CourseController.java) | Cung cấp đường dẫn đọc, sửa và xóa khóa học; chuyển yêu cầu tới `CourseServiceImpl`. |
| [backend/src/main/java/com/ccnlthd/course_management/service/impl/CourseServiceImpl.java](../../backend/src/main/java/com/ccnlthd/course_management/service/impl/CourseServiceImpl.java) | Đặt `@Cacheable` trên phương thức đọc chi tiết; yêu cầu xóa khóa tương ứng sau khi sửa hoặc xóa khóa học. |
| [backend/src/main/java/com/ccnlthd/course_management/service/impl/CategoryServiceImpl.java](../../backend/src/main/java/com/ccnlthd/course_management/service/impl/CategoryServiceImpl.java) | Yêu cầu xóa toàn bộ bộ nhớ đệm chi tiết khóa học sau khi cập nhật danh mục. |
| [backend/src/main/java/com/ccnlthd/course_management/service/CourseDetailCacheInvalidator.java](../../backend/src/main/java/com/ccnlthd/course_management/service/CourseDetailCacheInvalidator.java) | Thực hiện xóa một khóa hoặc toàn bộ vùng nhớ đệm sau khi giao dịch thành công. |
| [backend/src/main/java/com/ccnlthd/course_management/dto/response/CourseResponse.java](../../backend/src/main/java/com/ccnlthd/course_management/dto/response/CourseResponse.java) | Định nghĩa dữ liệu chi tiết khóa học được trả về và lưu trong Redis, gồm cả tên danh mục. |
| [backend/src/main/java/com/ccnlthd/course_management/entity/Course.java](../../backend/src/main/java/com/ccnlthd/course_management/entity/Course.java) và [backend/src/main/java/com/ccnlthd/course_management/repository/CourseRepository.java](../../backend/src/main/java/com/ccnlthd/course_management/repository/CourseRepository.java) | Biểu diễn khóa học trong cơ sở dữ liệu và đọc khóa học khi bộ nhớ đệm chưa có dữ liệu. |
| [backend/src/test/java/com/ccnlthd/course_management/CourseCacheIntegrationTests.java](../../backend/src/test/java/com/ccnlthd/course_management/CourseCacheIntegrationTests.java) | Sáu bài kiểm thử với Redis thật: đọc lần đầu, đọc lại, hết hạn, sửa và xóa, cập nhật tên danh mục, hủy giao dịch và thời điểm hoàn tất giao dịch. |
| [backend/src/test/resources/application.yml](../../backend/src/test/resources/application.yml) | Chọn cơ sở dữ liệu H2 và tắt bộ nhớ đệm cho các bài kiểm thử thông thường; bài kiểm thử Redis tự bật Redis. |
| [docs/week4/cache-evidence.md](cache-evidence.md) | Lưu kết quả đo thực tế gồm khóa Redis, nội dung dữ liệu, thời gian còn lại, số câu lệnh truy vấn và kết quả sau thay đổi. |
| [README.md](../../README.md) và [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | Hướng dẫn khởi động ứng dụng; chạy kiểm thử tự động khi có thay đổi được gửi lên GitHub. |

## Vai trò từng file ở nhánh demo

Những đường dẫn sau phải được xem trên nhánh `refactor/course-standalone`; chúng không phải là file của nhánh sản phẩm đang mở.

| File | Chức năng cụ thể |
| --- | --- |
| `docker-compose.yml` | Chạy MySQL và Redis cho bản demo. |
| `.env.example` | Nêu tên biến môi trường cho địa chỉ Redis, cổng Redis và thời gian tồn tại của bộ nhớ đệm. |
| `backend/pom.xml` | Khai báo thư viện lưu bộ nhớ đệm, kết nối Redis, cơ sở dữ liệu H2 và Testcontainers. |
| `backend/src/main/resources/application.yml` | Cấu hình địa chỉ Redis; `app.cache.course-detail-ttl` mặc định là 10 phút. |
| `backend/src/main/java/com/ccnlthd/course_management/config/CacheConfig.java` | Tạo bộ quản lý bộ nhớ đệm Redis, định dạng dữ liệu của `CourseResponse`, thời gian tồn tại và xử lý có nhận biết giao dịch. |
| `backend/src/main/java/com/ccnlthd/course_management/controller/CourseController.java` | Cung cấp đường dẫn đọc, sửa và xóa khóa học trong phiên bản Course standalone. |
| `backend/src/main/java/com/ccnlthd/course_management/service/impl/CourseServiceImpl.java` | Dùng `@Cacheable` cho yêu cầu đọc; dùng `standaloneCourseDetails` làm tên vùng nhớ đệm; yêu cầu xóa khóa khi sửa hoặc xóa khóa học. |
| `backend/src/main/java/com/ccnlthd/course_management/service/CourseDetailCacheInvalidator.java` | Xóa khóa chi tiết khóa học theo mã định danh sau khi giao dịch thành công. |
| `backend/src/main/java/com/ccnlthd/course_management/dto/response/CourseResponse.java` | Định nghĩa dữ liệu chi tiết khóa học được lưu trong Redis; bản này không có tên danh mục. |
| `backend/src/test/java/com/ccnlthd/course_management/CourseCacheIntegrationTests.java` | Năm bài kiểm thử Redis thật về đọc lần đầu, đọc lại, hết hạn, sửa và xóa, hủy giao dịch và thời điểm hoàn tất giao dịch. |
| `docs/week4/cache-evidence.md` | Lưu kết quả kiểm chứng riêng của nhánh demo. |
| `README.md` | Hướng dẫn chạy MySQL, Redi |

## Cách tự kiểm tra

1. Khởi động Docker trước khi chạy kiểm thử, vì Testcontainers cần Docker để tạo Redis tạm cho bài kiểm thử.
2. Tại thư mục `backend` của từng nhánh, chạy `.\mvnw.cmd -B verify` trên Windows hoặc `sh ./mvnw -B verify` trên Linux và macOS.
3. Để xem dữ liệu khi ứng dụng đang chạy, tại thư mục gốc chạy `docker compose exec redis redis-cli --scan --pattern 'courseDetails::*'` ở nhánh sản phẩm. Ở nhánh demo, thay mẫu khóa bằng `standaloneCourseDetails::*`. Dùng lệnh `GET` của `redis-cli` để đọc nội dung theo khóa và lệnh `TTL` để xem số giây còn lại; hai lệnh này là tên lệnh thực tế nên được giữ nguyên.

Theo minh chứng ngày, nhánh sản phẩm có **67 bài kiểm thử thành công**, bản demo có **5 bài kiểm thử thành công**. Các mã định danh được tạo trong bài kiểm thử có thể thay đổi sau mỗi lần chạy.
