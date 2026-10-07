# HƯỚNG DẪN CHẠY DỰ ÁN TỪ LÚC CLONE CODE

> Dành cho máy mới: từ clone tới gọi được API qua gateway, khoảng 15–25 phút
> (chủ yếu chờ pull image + build Maven lần đầu).

## 0. Yêu cầu trước

- Git, Java 21 (`java -version` → 21.x), Maven 3.9+ (`mvn -version`).
- Docker Desktop đang chạy (`docker ps` có output, không báo lỗi pipe).
- Port trống: 8080 (gateway), 8761 (eureka). Postgres/Kafka/MinIO chạy trong Docker
  nên không cần cài gì thêm.

## 1. Clone + setup env (2 phút)

```powershell
git clone <URL-REPO> rental-system-backend
cd rental-system-backend
Copy-Item .env.example .env
# Mở .env, thay JWT_SECRET bằng chuỗi NGẪU NHIÊN >= 32 ký tự (bắt buộc, Boot fail-fast nếu ngắn):
#   JWT_SECRET=<tự-đặt-32-ký-tự-trở-lên>
# Các password dev mặc định trong file dùng được ngay, KHÔNG commit .env (đã gitignore).
```

## 2. Kiểm tra build (5–10 phút lần đầu, do tải dependencies)

```powershell
# Build + test toàn repo (common-lib 27, gateway 13, auth 58 test + 1 IT cần Docker):
mvn clean verify
# Kỳ vọng cuối: Reactor 5/5 SUCCESS (parent, common-lib, discovery-server,
# api-gateway, auth-service). Lỗi ở bước này thì chưa cần đụng Docker — sửa code trước.
```

## 3. Lên toàn bộ stack bằng compose (5–15 phút lần đầu build image)

```powershell
cd .\infra
docker compose --env-file ..\.env up -d postgres kafka kafka-init discovery-server auth-service api-gateway
mvn clean install -DskipTests
# Chờ tới khi TẤT CẢ healthy (gateway lên chậm nhất, khoảng 1-3 phút):
# rental-postgres, rental-kafka, rental-discovery, rental-auth, rental-gateway
```

> Muốn thêm tool quan sát thì lên thêm: `docker compose --env-file .\.env up -d kafka-ui minio minio-init`
> (Kafka UI `:8090`, MinIO console `:9001`).
>
> LƯU Ý: mọi lệnh compose đều phải kèm `--env-file .\.env` vì file compose nằm trong
> `infra/` còn `.env` nằm ở root. Quên là lỗi `required variable ... is missing`.

## 4. Kiểm tra sống (1 phút)

```powershell
curl.exe -s http://localhost:8080/actuator/health   # gateway: {"status":"UP"}
# Compose CHỈ publish :8080 ra host (đúng docs mục 4) nên mọi kiểm tra đi qua gateway.
# Muốn chạm thẳng service khác thì dùng docker exec (ví dụ mục Eureka bên dưới).
```

```powershell
# Mở dashboard Eureka: trong container (hoặc publish thêm 8761 lúc dev)
# Kiểm tra service đã đăng ký:
docker exec rental-discovery wget -qO- http://localhost:8761/eureka/apps | grep -o "<name>[A-Z-]*</name>"
# Kỳ vọng: AUTH-SERVICE, API-GATEWAY
```

## 5. Gọi API đầu tiên (2 phút)

```powershell
# Đăng ký (public, không cần token) -> 201
$b = @{username="test01@gmail.com"; password="matkhau123"; fullName="Test A"; email="test01@gmail.com"} | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/api/v1/auth/register -Method Post -ContentType "application/json" -Body $b
# Đúng: 201 {"accountId":..,"username":"test01@gmail.com","accountType":"CUSTOMER"}

# Đăng nhập -> 200 + cặp token
$login = Invoke-RestMethod http://localhost:8080/api/v1/auth/login -Method Post -ContentType "application/json" -Body (@{username="test01@gmail.com"; password="matkhau123"} | ConvertTo-Json)
# Đúng: 200 {"accessToken":"eyJ...","refreshToken":"...","tokenType":"Bearer","expiresIn":900,...}

# Gọi API cần đăng nhập (gateway tự verify JWT + gắn header) -> 200
Invoke-RestMethod http://localhost:8080/api/v1/auth/me -Headers @{"Authorization"="Bearer $($login.accessToken)"}
# Đúng: 200 {"accountId":..,"username":"test01@gmail.com","accountType":"CUSTOMER","roles":["CUSTOMER"]}

# Không token -> 401 (gateway chặn)
try { Invoke-RestMethod http://localhost:8080/api/v1/auth/me } catch { [int]$_.Exception.Response.StatusCode }
# Đúng: 401
```

## 6. Quy trình dev hằng ngày

```powershell
cd infra
docker compose --env-file .\.env ps                       # xem trạng thái
docker compose --env-file .\.env logs -f api-gateway       # xem log 1 service (Ctrl+C thoát)
docker compose --env-file .\.env up -d --build auth-service  # sửa code xong build lại 1 service
docker compose --env-file .\.env restart api-gateway       # restart nhanh (không build)
docker compose --env-file .\.env down                      # tắt, GIỮ data DB
docker compose --env-file .\.env down -v                    # tắt + XÓA data (DB về trắng, Flyway migrate lại)
```

## 7. Muốn test gì tiếp theo

| Nhu cầu | Mở file |
|---|---|
| Test nhanh 8 API qua compose (blitz) | `docs/huong-dan-test-nhanh-compose.md` |
| Test đầy đủ 21 case + Kafka + DB + sự cố | `docs/huong-dan-test-toan-bo.md` |
| Báo cáo từng task đã làm (0.1 → E1) | `docs/bao-cao-task-*.md` (13 file) |

## 8. Sự cố hay gặp

| Triệu chứng | Sửa |
|---|---|
| `required variable ... is missing` | Thêm `--env-file .\.env` vào lệnh compose |
| `The container name ... is already in use` | Container standalone cũ trùng tên: `docker rm -f <tên>` rồi `up` lại |
| Build image lỗi `Child module ... does not exist` | Đã fix ở Dockerfile (COPY đủ module); `git pull` bản mới rồi build lại |
| `mvn ... BUILD FAILURE` dù test xanh trên máy dev | Tắt process `java -jar` đang giữ khóa file jar rồi build lại |
| `docker` báo lỗi pipe / daemon không chạy | Mở Docker Desktop, chờ `docker ps` có output |
| API qua `:8080` timeout sau khi up | Gateway/Eureka đăng ký mất ~30s; chờ gateway healthy rồi gọi lại |
| `curl.exe -d '{...}'` trả 400 body lạ | PowerShell phá escape JSON — dùng `Invoke-RestMethod` + hashtable như mục 5 |
