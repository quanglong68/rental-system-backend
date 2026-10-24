# Hướng dẫn chạy backend từ lúc clone code

## Chuẩn bị

- Cài Docker Desktop và bảo đảm Docker Engine đang chạy. Nếu muốn chạy Maven trên máy, cài thêm Java 21 và Maven 3.9+.
- Đặt file `.env` của dự án tại thư mục gốc backend, cùng cấp với `compose.yaml`. Lấy giá trị cấu hình từ nhóm dự án; không đưa mật khẩu hoặc token vào Git.
- Để trống cổng `8080` cho API Gateway. Discovery Server và các service còn lại chạy trong mạng Docker.

## Khởi động API Gateway và Swagger

Mở PowerShell tại thư mục gốc backend, sau đó chạy:

```powershell
cd .\infra
docker compose --env-file ..\.env -f ..\compose.yaml config --quiet
docker compose --env-file ..\.env -f ..\compose.yaml up -d --build api-gateway
docker compose --env-file ..\.env -f ..\compose.yaml ps
```

`config --quiet` kiểm tra `.env` và cấu hình Compose trước khi khởi động. Lệnh `up` sẽ chạy các dependency của gateway (Postgres, Kafka, Discovery Server và Auth Service); lần build đầu tiên có thể mất vài phút. Chờ `rental-postgres`, `rental-kafka`, `rental-discovery`, `rental-auth` và `rental-gateway` báo `healthy`.

Mở [Swagger UI](http://localhost:8080/swagger-ui.html), rồi chọn service ở danh sách phía trên. Gateway cung cấp OpenAPI JSON theo mẫu `http://localhost:8080/v3/api-docs/{service-name}`, ví dụ [`property-service`](http://localhost:8080/v3/api-docs/property-service). Chỉ những service đang chạy và đã đăng ký với Discovery Server mới tải được tài liệu. `properties-service` cần được khởi động từ dự án riêng của nó. Nếu trình duyệt trả lỗi, chạy lại lệnh `ps` ở trên và xem log gateway:

```powershell
docker compose --env-file ..\.env -f ..\compose.yaml logs --tail 100 api-gateway
```

## Chỉ build lại API Gateway

Khi các dependency đã chạy và bạn chỉ sửa code gateway, dùng:

```powershell
docker compose --env-file ..\.env -f ..\compose.yaml build api-gateway
docker compose --env-file ..\.env -f ..\compose.yaml up -d --no-deps --no-build api-gateway
```

`api-gateway` là tên service trong Compose. `rental-system-backend` là tên thư mục dự án, không phải tên service. Khi chạy lệnh từ `infra`, luôn dùng `--env-file ..\.env` để Compose đọc biến trong `.env` ở thư mục gốc.

Swagger chỉ cần các service trên. Nếu khởi động thêm MinIO và Asset Maintenance Service, bạn phải cung cấp `MINIO_LICENSE_TOKEN` hợp lệ trong `.env`; MinIO sẽ không khởi động khi thiếu token này.

## Kiểm tra bằng Maven (tùy chọn)

Chạy từ thư mục gốc backend nếu muốn biên dịch và chạy test trên máy:

```powershell
mvn clean verify
```
