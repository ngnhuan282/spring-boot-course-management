# Minh chứng Redis Cache — bản sản phẩm

Nhánh: `feat/week4-redis-cache-setup`. Chạy ngày 2026-10-04 bằng `cd backend` rồi `mvnw.cmd -B verify`: **67 test qua, 0 lỗi**. `CourseCacheIntegrationTests` dùng Redis 7.4 thật qua Testcontainers và H2 cho dữ liệu test.

Ứng dụng dùng TTL mặc định **10 phút** (`COURSE_CACHE_TTL=10m`); riêng test dùng **5 giây** để kiểm tra hết hạn. Chỉ `GET /api/courses/{id}` được cache, với tên `courseDetails`. JSON được giải mã lại thành `CourseResponse`.

## Key, JSON và số truy vấn

Kết quả được in bởi `missThenHitStoresReadableJsonAndAvoidsMoreSql` trong Surefire XML:

```text
key=courseDetails::5
missSql=2, hitSql=2 (lượt hit thêm 0 truy vấn SQL)
ttlMs=4995
json={"id":5,"categoryId":4,"categoryName":"Cache category 1eada9c9-8b29-48d1-b162-c642d8ede823","title":"Original course","description":"Redis cache integration test","price":149.90,"level":"BEGINNER","status":"DRAFT"}
```

## Hết hạn và làm mới dữ liệu

| Trường hợp | Kết quả test |
| --- | --- |
| TTL hết hạn | Key `courseDetails::7` biến mất; lượt GET tiếp theo truy vấn DB lại (`reloadSql=2`) và tạo key mới. |
| Sửa Course | Sau commit, key `courseDetails::9` bị xóa; GET tiếp theo trả `Updated course`. |
| Xóa Course | Key `courseDetails::9` không còn (`deletedKey=false`); GET trả `COURSE_NOT_FOUND`. |
| Đổi tên Category | Course cache có `categoryName` cũ `Cache category 902f438f-59ce-49ed-8fb3-03b419609227`; sau commit, GET trả `Renamed 7ae08bd6-edae-4e99-8aee-f759a2c01335`. |
| Rollback | Sửa Course rồi rollback giữ nguyên key và response cũ. |
| Giao dịch bao ngoài | Key còn tồn tại trước commit và bị xóa sau commit. |

Các ID và tên UUID ở trên do test tạo ra, sẽ thay đổi khi chạy lại. Từ thư mục gốc, khi ứng dụng và Docker Compose đang chạy, có thể xem key, JSON và TTL bằng:

```bash
docker compose exec redis redis-cli --scan --pattern 'courseDetails::*'
docker compose exec redis redis-cli GET 'courseDetails::1'
docker compose exec redis redis-cli TTL 'courseDetails::1'
```
