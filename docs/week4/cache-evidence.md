# Minh chứng Redis Cache — bản Course standalone

Nhánh demo: `refactor/course-standalone`. Chạy ngày 2026-10-04 bằng `cd backend` rồi `mvnw.cmd -B verify`: **5 test qua, 0 lỗi**. Test dùng Redis 7.4 thật qua Testcontainers và H2 cho dữ liệu test.

Ứng dụng dùng TTL mặc định **10 phút** (`APP_CACHE_COURSE_DETAIL_TTL=10m`); riêng test dùng **5 giây**. Chỉ `GET /api/courses/{id}` dùng cache `standaloneCourseDetails`, tách key khỏi bản sản phẩm.

## Key, JSON và số truy vấn

Kết quả từ `missWritesReadableJsonAndHitSkipsDatabase` trong Surefire XML:

```text
key=standaloneCourseDetails::4
missSql=1, hitSql=0
ttlMs=4995
json={"id":4,"title":"Initial title","description":"Redis cache integration test","price":499000.00,"level":"BEGINNER","status":"PUBLISHED"}
```

Giá trị đọc lại từ Redis có kiểu `CourseResponse`, đúng ID, title và price của lượt GET đầu.

## Hết hạn và làm mới dữ liệu

| Trường hợp | Kết quả test |
| --- | --- |
| TTL hết hạn | Key `standaloneCourseDetails::5` biến mất; GET tiếp theo truy vấn DB lại (`reloadSql=1`) và tạo key mới. |
| Sửa Course | Sau commit, cache bị xóa; GET tiếp theo trả title `After` và truy vấn DB một lần. |
| Xóa Course | Key `standaloneCourseDetails::3` không còn (`deletedKey=false`); GET báo không tìm thấy Course. |
| Rollback | Sửa Course rồi rollback giữ nguyên key và response cũ. |
| Giao dịch bao ngoài | Key còn tồn tại trước commit và bị xóa sau commit. |

ID ở trên do test tạo ra, sẽ thay đổi khi chạy lại. Từ thư mục gốc, khi ứng dụng và Docker Compose đang chạy, có thể xem key, JSON và TTL bằng:

```bash
docker compose exec redis redis-cli --scan --pattern 'standaloneCourseDetails::*'
docker compose exec redis redis-cli GET 'standaloneCourseDetails::1'
docker compose exec redis redis-cli TTL 'standaloneCourseDetails::1'
```
