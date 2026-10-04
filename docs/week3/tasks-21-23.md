# Tuần 3: Nhiệm vụ số 21 và 23 của thành viên 3

Tài liệu này đối chiếu trong bảng phân công với mã nguồn hiện có trên nhánh sản phẩm. Phụ trách chính **nhiệm vụ task 21 — Chuẩn hóa ngoại lệ toàn hệ thống** và **nhiệm vụ task 23 — Thiết lập GitHub Actions để xây dựng và kiểm thử tự động**. 

## Task 21: Chuẩn hóa ngoại lệ

**Yêu cầu trong bảng phân công:** không xử lý lỗi rải rác trong từng bộ điều khiển và tầng nghiệp vụ; đưa lỗi kiểm tra dữ liệu, không tìm thấy, xung đột và lỗi chưa phân loại về một dạng phản hồi thống nhất. Sản phẩm cần có danh sách mã lỗi và bài kiểm thử phản hồi.

**Cách thực hiện:** tầng nghiệp vụ ném `AppException` cùng một giá trị `ErrorCode`. `GlobalExceptionHandler` nhận ngoại lệ từ bộ điều khiển và chuyển thành `ApiResponse` với bốn trường `code`, `message`, `result`, `timestamp`. Những ngoại lệ do dữ liệu yêu cầu không hợp lệ, đường dẫn không tồn tại và ràng buộc cơ sở dữ liệu cũng được xử lý tại đây. Lỗi chưa phân loại trả thông điệp chung cho người gọi và được ghi chi tiết trong nhật ký máy chủ.

**Minh chứng:** [error-codes.md](error-codes.md) liệt kê đủ 14 mã lỗi hiện có, mã trạng thái, thông điệp mặc định, tình huống sử dụng, đường dẫn mã nguồn và bài kiểm thử. Ngày 2026-10-04, bốn nhóm kiểm thử liên quan chạy thành công **14 bài, không có lỗi**. Kết quả này chứng minh những trường hợp được viết bài kiểm thử; không có nghĩa mọi mã lỗi trong danh sách đều có một bài kiểm thử riêng.

## Task 23: Xây dựng và kiểm thử tự động bằng GitHub Actions

**Yêu cầu trong bảng phân công:** tạo quy trình tự chạy khi gửi mã lên GitHub hoặc mở đề nghị hợp nhất mã; lưu ảnh một lần chạy thành công và, nếu có, một lần chạy thất bại đã được sửa.

**Cách thực hiện hiện tại:** [.github/workflows/ci.yml](../../.github/workflows/ci.yml) chạy trên máy Ubuntu do GitHub cung cấp. Quy trình lấy mã nguồn, cài Java 21, lưu tạm các thư viện Maven, chuyển vào thư mục `backend` rồi chạy `sh ./mvnw -B verify`. Sau đó quy trình tải lên các báo cáo kiểm thử trong `backend/target/surefire-reports/`, kể cả khi bước kiểm thử thất bại.

Các bài kiểm thử thông thường dùng cơ sở dữ liệu H2 theo `backend/src/test/resources/application.yml`.

**Minh chứng và giới hạn:** [ci-evidence.md](ci-evidence.md) ghi thông tin nhìn thấy trong ảnh lần chạy thành công do bạn đã gửi, lần thất bại được báo lại và cách sửa. 

## Thư mục và file nào làm việc gì

| Đường dẫn đầy đủ trong repository | Vai trò |
| --- | --- |
| `backend/src/main/java/com/ccnlthd/course_management/exception/` | Chứa kiểu ngoại lệ nghiệp vụ, danh sách mã lỗi và bộ xử lý lỗi chung của nhiệm vụ số 21. |
| [backend/src/main/java/com/ccnlthd/course_management/exception/AppException.java](../../backend/src/main/java/com/ccnlthd/course_management/exception/AppException.java) | Mang mã lỗi từ tầng nghiệp vụ tới bộ xử lý lỗi. |
| [backend/src/main/java/com/ccnlthd/course_management/exception/ErrorCode.java](../../backend/src/main/java/com/ccnlthd/course_management/exception/ErrorCode.java) | Định nghĩa mã lỗi, thông điệp mặc định và mã trạng thái phản hồi. |
| [backend/src/main/java/com/ccnlthd/course_management/exception/GlobalExceptionHandler.java](../../backend/src/main/java/com/ccnlthd/course_management/exception/GlobalExceptionHandler.java) | Chuẩn hóa phản hồi cho lỗi nghiệp vụ, kiểm tra dữ liệu, không tìm thấy, xung đột và lỗi chưa phân loại. |
| [backend/src/main/java/com/ccnlthd/course_management/dto/response/ApiResponse.java](../../backend/src/main/java/com/ccnlthd/course_management/dto/response/ApiResponse.java) | Định nghĩa hình dạng phản hồi có bốn trường dùng cho lỗi. |
| `backend/src/main/java/com/ccnlthd/course_management/dto/request/` | Chứa quy tắc kiểm tra dữ liệu trước khi gọi tầng nghiệp vụ. |
| `backend/src/main/java/com/ccnlthd/course_management/service/impl/` | Chứa quy tắc nghiệp vụ và nơi ném `AppException` khi dữ liệu không hợp lệ theo nghiệp vụ. |
| `backend/src/test/java/com/ccnlthd/course_management/` | Chứa bốn nhóm bài kiểm thử liên quan đến phản hồi lỗi và việc khởi động ứng dụng, được liệt kê cụ thể trong [error-codes.md](error-codes.md). |
| [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | Định nghĩa lúc nào quy trình tự chạy, phiên bản Java, lệnh xây dựng và nơi tải báo cáo kiểm thử lên GitHub. |
| [backend/mvnw](../../backend/mvnw) và [backend/.mvn/wrapper/maven-wrapper.properties](../../backend/.mvn/wrapper/maven-wrapper.properties) | Cung cấp trình khởi chạy Maven và phiên bản Maven để quy trình sử dụng nhất quán. |
| [backend/pom.xml](../../backend/pom.xml) | Khai báo thư viện ứng dụng và kiểm thử để lệnh `verify` xây dựng, chạy bài kiểm thử. |
| [backend/src/test/resources/application.yml](../../backend/src/test/resources/application.yml) | Chọn cơ sở dữ liệu H2 cho các bài kiểm thử thông thường. |
| `backend/target/surefire-reports/` | Thư mục báo cáo được tạo khi kiểm thử; GitHub Actions tải các tệp báo cáo lên, còn Git không lưu thư mục sinh ra này. |
| [docs/week3/error-codes.md](error-codes.md), [docs/week3/ci-evidence.md](ci-evidence.md) | Tài liệu danh sách mã lỗi, kết quả kiểm thử và trạng thái minh chứng của quy trình tự động. |

## Cách kiểm tra lại

- Để kiểm tra nhiệm vụ số 21 trên Windows, vào thư mục `backend` và chạy `.\mvnw.cmd -B '-Dtest=GlobalExceptionHandlerTests,BusinessExceptionTests,CourseManagementApplicationTests,CourseControllerValidationTests' test`.
- Sau khi gửi mã lên GitHub, xem lần chạy `Backend CI` trong mục Actions. Tài liệu [ci-evidence.md](ci-evidence.md) nêu chính xác phần nào đã có bằng chứng và phần nào cần bổ sung ảnh.
