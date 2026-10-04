# Tuần 3 — Task 23: Minh chứng quy trình GitHub Actions

## Quy trình hiện có trong mã nguồn

File [.github/workflows/ci.yml](../../.github/workflows/ci.yml) đặt tên quy trình là `Backend CI`. Quy trình tự khởi chạy khi có sự kiện `push` hoặc `pull_request`, nghĩa là khi gửi mã hoặc mở, cập nhật đề nghị hợp nhất mã.

Trên máy Ubuntu do GitHub cung cấp, quy trình lấy mã nguồn bằng `actions/checkout@v7`, cài Java 21 bằng `actions/setup-java@v6`, rồi chạy `sh ./mvnw -B verify` trong thư mục `backend`. Bước `actions/upload-artifact@v7` tải các báo cáo kiểm thử từ `backend/target/surefire-reports/*.xml` lên GitHub ngay cả khi bước kiểm thử thất bại. Nếu không có file báo cáo, bước này chỉ đưa ra cảnh báo.

Phiên bản hiện tại của quy trình không chạy dịch vụ MySQL riêng và không đặt địa chỉ MySQL cho mọi bài kiểm thử. Các bài kiểm thử thông thường lấy cấu hình cơ sở dữ liệu H2 từ [backend/src/test/resources/application.yml](../../backend/src/test/resources/application.yml).

## Lần chạy thành công đã được nhìn thấy

Ảnh minh chứng:

## Lần chạy thất bại và bản sửa sau đó

Sau khi nhánh tuần 4 bổ sung bài kiểm thử Redis, một lần chạy GitHub Actions kết thúc với **67 bài kiểm thử, không có bài thất bại do khẳng định sai, nhưng có 10 lỗi khi khởi tạo ứng dụng kiểm thử**. Các dòng thông báo về giới hạn số lần khởi tạo chỉ là hậu quả sau lỗi đầu tiên.

Nguyên nhân đã được tái hiện trên máy cá nhân: cấu hình quy trình cũ đặt địa chỉ kết nối MySQL bằng biến môi trường cho toàn bộ công việc, trong khi cấu hình kiểm thử chọn trình điều khiển cơ sở dữ liệu H2. Trình điều khiển H2 không chấp nhận địa chỉ MySQL, làm các nhóm kiểm thử cần khởi tạo ứng dụng không chạy được.

Commit có thông điệp `fix(ci): use H2 datasource for tests` đã bỏ các biến MySQL ở cấp công việc và dịch vụ MySQL không cần thiết khỏi quy trình. Sau bản sửa, lệnh `mvn verify` trên máy cá nhân chạy thành công **67 bài kiểm thử, không có lỗi**, bao gồm kiểm thử Redis với Docker đang hoạt động.

## Tự kiểm tra và hoàn thiện minh chứng

1. Mở mục Actions của repository, chọn quy trình `Backend CI` và tìm lần chạy của nhánh cần báo cáo.
2. Chụp toàn bộ phần thể hiện commit, tên nhánh, trạng thái `Success` và công việc `build-and-test`. Lưu tệp ảnh thật vào `docs/week3/`; sau đó liên kết tệp ngay trong tài liệu này.
3. Nếu cần báo cáo lần chạy thất bại đã được sửa, lưu ảnh hoặc đường dẫn của lần thất bại và lần thành công sau sửa. Tránh dùng kết quả chạy trên máy cá nhân để thay cho bằng chứng GitHub Actions.
