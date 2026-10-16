# Thực nghiệm đối chiếu Spring Boot, ASP.NET Core và NestJS

## 1. Mục đích và phạm vi

Bộ thực nghiệm gồm ba ứng dụng mẫu độc lập với hệ thống quản lý khóa học trực tuyến của nhóm, dùng để đối chiếu cách tổ chức Controller/Service và Dependency Injection, kiểm tra dữ liệu đầu vào (validation), và thời gian khởi động. Các ứng dụng cung cấp `GET /api/health` và API Product (`/api/products`), sử dụng dữ liệu trong bộ nhớ; không sử dụng JPA, cơ sở dữ liệu hoặc Redis trong phép đo khởi động.

Bộ thực nghiệm được lưu tại `experiments/m02-framework-comparison/`, tách biệt với mã nguồn sản phẩm trong `backend/`.

## 2. Cấu trúc thư mục

```text
m02-framework-comparison/
├── spring-boot/              # Ứng dụng Spring Boot
├── aspnet-core/              # Ứng dụng ASP.NET Core
├── nestjs/                   # Ứng dụng NestJS
├── scripts/
│   ├── measure-spring-boot.ps1
│   ├── measure-aspnet-core.ps1
│   └── measure-nestjs.ps1
├── evidence/                 # CSV và ảnh chụp kết quả thực nghiệm
└── README.md
```

## 3. Môi trường thực nghiệm

Các phép đo được ghi nhận ngày **15/10/2026**, trên cùng một máy tính cá nhân:

- Hệ điều hành: **Windows 11**, 64-bit (phiên bản hệ thống hiển thị: `10.0.26200`).
- CPU: **Intel Core i7-13620H thế hệ 13**.
- RAM: **16 GB** (Windows nhận diện khoảng 15,71 GB).
- Spring Boot: **4.1.1**; **Microsoft OpenJDK 21.0.9**; **Maven 3.9.16** (Maven Wrapper), Java 21.
- ASP.NET Core: **target .NET 9**, ASP.NET Core runtime **9.0.20**; máy có .NET SDK **10.0.401**.
- NestJS: **12.0.1** (`@nestjs/core`, `@nestjs/common`); **Node.js 22.19.0**; **npm 10.8.2**.

**Lưu ý:** JDK Microsoft 21.0.9 của ứng dụng mẫu khác với một số môi trường kiểm chứng của backend sản phẩm. Không dùng số liệu của bộ thực nghiệm này làm số liệu khởi động của hệ thống quản lý khóa học.

## 4. Build và chạy ứng dụng

Các lệnh dưới đây sử dụng **PowerShell trên Windows**. Mở terminal tại thư mục `experiments/m02-framework-comparison/`. Cần cài sẵn JDK 21 (đặt `JAVA_HOME`), .NET SDK thích hợp và Node.js/npm. Hãy build trước khi đo, không dùng hot reload/watch.

### Spring Boot (cổng 8080)

```powershell
cd .\spring-boot
.\mvnw.cmd clean package "-DskipTests"
java -jar .\target\spring-boot-demo-0.0.1-SNAPSHOT.jar
```

### ASP.NET Core (cổng 8081)

```powershell
cd .\aspnet-core
dotnet build .\aspnet-core-demo -c Release
dotnet .\aspnet-core-demo\bin\Release\net9.0\aspnet-core-demo.dll
```

### NestJS (cổng 8082)

```powershell
cd .\nestjs
npm ci
npm run build
node .\dist\main.js
```

Mỗi nhóm lệnh chạy từ thư mục riêng của framework tương ứng. Sau khi ứng dụng chạy, kiểm tra `http://localhost:8080/api/health` (Spring Boot), `http://localhost:8081/api/health` (ASP.NET Core) hoặc `http://localhost:8082/api/health` (NestJS), kỳ vọng **HTTP 200**. Dừng ứng dụng trước khi chạy script đo cùng framework.

## 5. Cách đo thời gian khởi động

Nhóm sử dụng các lệnh PowerShell thực thi trực tiếp trong terminal khi thu số liệu; nội dung lệnh đã được lưu lại trong ba tệp `.ps1` dưới `scripts/` để có thể chạy lại.

- Build trước khi đo; không dùng `spring-boot:run`, `dotnet watch` hoặc `npm run start:dev`.
- Mỗi lượt dùng `Start-Process` để chạy ứng dụng đã build và `System.Diagnostics.Stopwatch` để đo thời gian từ trước khi gọi lệnh khởi chạy đến lần đầu `GET /api/health` nhận **HTTP 200**.
- Script polling với khoảng chờ **50 ms**, thực hiện **10 lần chạy độc lập** cho mỗi framework, dừng tiến trình và chờ giữa các lượt.
- Lưu từng lượt theo `Run`, `Seconds`, `Status` vào CSV; tính median trên 10 lượt thành công. IQR trong báo cáo được tính bằng trung vị nửa trên trừ trung vị nửa dưới của dãy đã sắp xếp.
- Thời gian bao gồm chi phí tạo tiến trình và độ trễ quan sát do polling; không phải thời gian xử lý nội bộ thuần túy của framework.

**Chạy lại script:** Sau khi build, mở một PowerShell mới và `cd` vào thư mục project của framework cần đo (vì script dùng đường dẫn tương đối). Đảm bảo cổng tương ứng đang trống.

```powershell
# Chạy tại spring-boot/
..\scripts\measure-spring-boot.ps1

# Chạy tại aspnet-core/
..\scripts\measure-aspnet-core.ps1

# Chạy tại nestjs/
..\scripts\measure-nestjs.ps1
```

Mỗi script hiện xuất CSV ra **Desktop** của người chạy. Những tệp CSV đã thu ở lần thực nghiệm được lưu tại `evidence/`; khi tái lập, **không ghi đè** lên dữ liệu gốc trong repository.

## 6. Kết quả ghi nhận

| Framework | Số lượt thành công | Median (s) | Min (s) | Max (s) | IQR (s) |
|---|---:|---:|---:|---:|---:|
| Spring Boot | 10/10 | 3,2865 | 2,896 | 5,818 | 0,668 |
| ASP.NET Core | 10/10 | 0,674 | 0,631 | 0,746 | 0,037 |
| NestJS | 10/10 | 1,176 | 1,157 | 1,223 | 0,019 |

Dữ liệu từng lượt được lưu trong:

- `evidence/M02_springboot_10lan.csv`
- `evidence/M02_aspnet_10lan.csv`
- `evidence/M02_nestjs_10lan.csv`

Ảnh tại `evidence/` ghi nhận phiên bản công cụ, build, kiểm tra health, các lượt đo và phản hồi validation.

## 7. Đối chiếu validation

Gửi cùng request đến `POST /api/products` trên cổng của từng ứng dụng, với JSON:

```json
{"name":"", "price":-1}
```

Cả ba ứng dụng trả **HTTP 400**. Spring Boot và ASP.NET Core trả đối tượng lỗi có mã `VALIDATION_FAILED` cùng một thông báo; NestJS trả danh sách hai thông báo. Trong ứng dụng mẫu ASP.NET Core, `VALIDATION_FAILED` là dạng phản hồi **được tùy chỉnh bằng `InvalidModelStateResponseFactory` trong `Program.cs`**, không phải `ValidationProblemDetails` mặc định của `[ApiController]`.

## 8. Lưu ý về truy vết

Bộ ứng dụng mẫu được xây dựng và chạy độc lập trước khi được lưu vào thư mục thực nghiệm của repository nhóm. **Commit lưu trữ bộ thực nghiệm được tạo sau khi thu số liệu**, do đó không diễn đạt rằng commit này đã tồn tại tại thời điểm đo. Khi đối chiếu, sử dụng mã nguồn, script, CSV và ảnh trong cùng thư mục, cùng với mã commit thực tế ghi trong lịch sử Git.

Phép đo này chỉ so sánh thời gian khởi động của ba ứng dụng mẫu trong môi trường trên; không dùng để kết luận hiệu năng xử lý request, chịu tải hoặc hiệu năng tổng thể của các framework.
