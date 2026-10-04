# Báo cáo Task C4 – API Đăng ký + Tạo tài khoản STAFF/ADMIN

> Nguồn: `docs/plan-infra-auth-team.md` Task C4 + `docs/Đặc tả…` Table 7, mục 7.1/7.3/8/9.1 + `AIRule.md`.
> Plan C4 đã được bạn duyệt (3 quyết định mục 5). Không boot, không Eureka, verify bằng test.

## 1. File tạo mới (11)

- `auth-service/.../dto/RegisterRequest.java`: `username/password(min 8)/fullName` bắt buộc
  (`@Valid`), `email?/phone?` optional.
- `auth-service/.../dto/RegisterResponse.java`: `accountId, username, accountType` (đúng plan).
- `auth-service/.../dto/CreateAccountRequest.java`: như trên + `accountType: STAFF|ADMIN`
  + `roles[]`.
- `auth-service/.../dto/AccountResponse.java`: `accountId, username, accountType, active, roles[]`.
- `auth-service/.../dto/UpdateAccountStatusRequest.java`: `{active: Boolean @NotNull}`.
- `auth-service/.../dto/UpdateAccountRolesRequest.java`: `{roles: @NotEmpty}`.
- `auth-service/.../dto/PageResponse.java`: `{content,page,size,totalElements,totalPages}`
  generic đúng mục 8 (đặt ở auth-service, chưa đụng common-lib).
- `auth-service/.../service/AccountService.java`: `@Transactional` — `register()` (chuẩn hoá
  username trim+lowercase, username phải là email hoặc SĐT VN `^(0|\+84)(3|5|7|8|9)\d{8}$`
  và trùng email/phone khai báo, `USERNAME_EXISTS→409`, BCrypt, gán role CUSTOMER),
  `createAccount()` (STAFF bắt buộc roles + chỉ `QUAN_LY/KY_THUAT/SALE`, ADMIN auto-gán
  `ADMIN` bỏ qua input), `listAccounts()` (lọc `accountType?/active?`, page 0-based,
  size default 20/max 100, sort id desc), `updateStatus()` (thiếu → 404),
  `updateRoles()` (chỉ STAFF, sai → 422). `fullName/email/phone` KHÔNG lưu auth_db
  (thuộc User Service, mục 9.1) — Task C6 forward qua outbox `AccountRegistered`.
- `auth-service/.../controller/AuthController.java`: `POST /api/v1/auth/register` → 201
  (public, `SecurityConfig` đã permitAll sẵn từ C3).
- `auth-service/.../controller/AdminAccountController.java`:
  `@PreAuthorize("hasRole('ADMIN')")` cấp class — `POST /accounts` → 201,
  `GET /accounts?accountType&active&page&size` → 200, `PUT /{id}/status`,
  `PUT /{id}/roles` → 200 + `AccountResponse`.
- Test: `service/AccountServiceTest` (12 test Mockito, không DB) +
  `controller/AccountControllerWebTest` (7 test `@WebMvcTest` + `@Import(SecurityConfig,
  GlobalExceptionHandler)`, `AccountService` mock — kiểm tra chain bảo mật THẬT).

## 2. File sửa (5, tối thiểu)

- `repository/AccountRepository.java`: thêm 3 derived query phân trang
  (`findByAccountType / findByActive / findByAccountTypeAndActive`) — không query tay.
- `repository/RoleRepository.java`: thêm `existsByCode` (service cần).
- `pom.xml` (root): thêm `<parameters>true</parameters>` cho `maven-compiler-plugin`
  (giữ tên tham số cho Spring đọc `@RequestParam/@PathVariable` — giống
  `spring-boot-starter-parent`). Xem mục 4.
- `AuthServiceApplication.java`: `@Import(GlobalExceptionHandler.class)` — **fix quan trọng
  nhất phát hiện khi test thật**, xem mục 5.1.
- `service/AccountService.java`: tách `parseListAccountType()` cho phép lọc cả CUSTOMER
  (xem mục 5.2).

## 3. Kết quả verify

- `mvn -f auth-service/pom.xml clean verify` → **Tests run: 29, Failures: 0, Errors: 0**,
  **BUILD SUCCESS** (10 cũ + 12 service + 7 web). `common-lib install` + `discovery package` xanh.
- Ma trận web slice đã chứng minh: register public → 201; body sai → 400
  `VALIDATION_ERROR` + details; trùng username → 409; CUSTOMER `POST /accounts` → 403
  (`@PreAuthorize` thật); ADMIN → 201; thiếu header → 401 filter; `GET /accounts` ADMIN → 200
  đúng format trang.
- Không thêm lib, không sửa migration, không secret trong git.

## 4. Bốn sự cố gặp khi implement/test (đã fix, ghi lại để team tránh)

1. `@MockitoBean` không tồn tại: `spring-test` thực tế là **6.1.14** (Boot 3.3 line) —
   `@MockitoBean` chỉ có từ Framework 6.2/Boot 3.4. Dùng `@MockBean`
   (`org.springframework.boot.test.mock.mockito`, deprecated nhưng đúng cho Boot 3.3).
2. `anyBoolean()` không khớp tham số `Boolean` wrapper → stub trượt, mock trả null.
   Dùng `any()` cho wrapper.
3. **Thiếu `-parameters` ở parent pom custom** (Task 0.1 không kế thừa
   `spring-boot-starter-parent`): `@RequestParam String accountType` không tên tường minh
   → runtime `IllegalArgumentException: ... Ensure that the compiler uses the '-parameters' flag`
   → 500. Fix gốc ở root pom (mục 2). Bài học: từ nay mọi module build lại sau đổi pom
   phải `clean` (compiler không tự recompile khi chỉ đổi config) — test chỉ xanh sau
   `mvn clean verify`.
4. **Vật lý build trên Windows**: khi `java -jar auth-service.jar` đang chạy thì file jar bị
   khóa, `maven-jar-plugin`/`repackage` không ghi được → `BUILD FAILURE` dù test xanh
   (hiện tượng: `Tests run: 29 ... Failures: 0` rồi `BUILD FAILURE`, jar vẫn giữ timestamp cũ).
   Phải `Stop-Process` đúng pid của app **trước** khi `mvn package`. Đây là lý do tôi từng
   chạy nhầm bản cũ — kiểm tra `LastWriteTime` của jar là cách nhận biết nhanh nhất.

## 5. Hai lỗi THẬT chỉ lộ ra khi test với DB + HTTP thật

### 5.1 `GlobalExceptionHandler` không được đăng ký → mất status code và mất body (đã fix)

- Hiện tượng: `POST /register` trùng username trả **HTTP 500** (thay vì 409);
  `PUT /roles` sai role trả **422 nhưng body rỗng**; `POST /accounts` sai dữ liệu trả
  **400 nhưng body rỗng**. Riêng 401/403 có body vì đến từ `accessDeniedHandler` của
  `SecurityConfig`, không qua advice.
- Nguyên nhân: `AuthServiceApplication` **không khai báo `@Import(GlobalExceptionHandler.class)`**
  và không cấu hình `spring.factories`/`AutoConfiguration.imports`. Nên `@RestControllerAdvice`
  trong `common-lib` chưa từng được đăng ký vào context.
  - `BusinessException` với status ≠ 400 bị `DispatcherServlet` đẩy sang `/error` dưới dạng
    `sendError(status)` → `BasicErrorController` trả 500 vì không còn handler nào đổi status
    → 409 thành 500.
  - `@Valid` lỗi (`MethodArgumentNotValidException`) được xử lý **mặc định** của Spring MVC
    trả 400 `ProblemDetail`, nhưng advice chặn `Accept: application/json` không còn nên
    `sendError(400)` → `/error` với `error` rỗng → 400 body rỗng.
- Đã verify bằng cách so `curl -i` (có body `{"code":"USERNAME_EXISTS",...,"status":409}`)
  với `Invoke-WebRequest` bị mất body do status ≠ 200 → chứng minh lỗi ở tầng server, không
  phải client.
- Fix: `@Import(GlobalExceptionHandler.class)` trên `AuthServiceApplication`. Sau fix **không
  cần** đổi `handleValidation` (đã map `FieldError` → `FieldViolation(field, message)` đúng
  format mục 8, không có vòng lặc đệ quy) và `ddl-auto` không cần `PROD`.
- Bài học cho team: **mọi service import common-lib đều phải khai `@Import(GlobalExceptionHandler.class)`**
  (đã ghi rõ trong javadoc của handler). Test `@WebMvcTest` không bắt được lỗi này vì test
  tự `@Import` thủ công — phải có integration test mới thấy.

### 5.2 `GET /accounts?accountType=CUSTOMER` bị 400 (đã fix)

- `parseAccountType()` vốn chỉ nhận `STAFF|ADMIN` (đúng cho `POST /accounts`), bị tái dùng cho
  bộ lọc danh sách nên `accountType=CUSTOMER` bị từ chối. Tách `parseListAccountType()` cho
  phép đủ 3 loại khi lọc (ADMIN được xem cả CUSTOMER).

## 5. Quyết định của bạn đã áp dụng

1. Không tạo `PasswordService` — `AccountService` inject trực tiếp `PasswordEncoder`
   (BCrypt cost 10 từ `SecurityConfig`).
2. ADMIN mới: auto-gán role `ADMIN`, bỏ qua `roles[]` input.
3. `PUT status/roles` → 200 + `AccountResponse` (không 204).
4. Username chuẩn hoá trim + lowercase; regex SĐT VN + email cơ bản như mục 1.

## 6. Kết quả test thật (boot + Postgres qua pg-proxy 5433, 15/15 đúng)

| # | Case | Kỳ vọng | Thực tế |
|---|------|---------|---------|
| 1 | `POST /register` lần đầu | 201 + `accountType:CUSTOMER` | ✅ 201 `{"accountId":5,"username":"fin01@gmail.com","accountType":"CUSTOMER"}` |
| 2 | `POST /register` trùng username | 409 `USERNAME_EXISTS` | ✅ 409 `USERNAME_EXISTS` |
| 3 | `password` < 8 ký tự | 400 `VALIDATION_ERROR` + `details[]` | ✅ 400 `details:[{"field":"password","message":"Mat khau toi thieu 8 ky tu"}]` |
| 4 | `username` không phải email/SDT | 400 | ✅ 400 `Username phai la email hoac so dien thoai` |
| 5 | `username` (SDT) ≠ `email` khai báo | 400 | ✅ 400 `Username phai trung email khai bao` |
| 6 | `POST /accounts` không header | 401 | ✅ 401 `Request khong di qua Gateway` |
| 7 | `POST /accounts` header STAFF | 403 `FORBIDDEN` | ✅ 403 `Khong du quyen` |
| 8 | ADMIN tạo STAFF kèm `SALE` | 201 + `roles:["SALE"]` | ✅ 201 `roles:["SALE"]` |
| 9 | ADMIN tạo STAFF kèm role `ADMIN` | 422 `ROLE_NOT_ALLOWED` | ✅ 422 `Role khong duoc phep cho STAFF: ADMIN` |
| 10 | ADMIN tạo STAFF không kèm role | 400 | ✅ 400 `STAFF phai co it nhat 1 role` |
| 11 | ADMIN tạo ADMIN có `roles:["SALE"]` | 201 + `roles:["ADMIN"]` (auto-gán) | ✅ 201 `roles:["ADMIN"]` |
| 12 | `GET /accounts?accountType=CUSTOMER&active=true` | 200 + format trang | ✅ 200 `{content:[4 account],page:0,size:20,totalElements:4,totalPages:1}` |
| 13 | `PUT /{id}/status {active:false}` | 200 `active:false` | ✅ 200 `active:false` |
| 14 | `PUT /{id}/roles` trên STAFF | 200 `roles:["QUAN_LY","SALE"]` | ✅ 200 (lần đầu tôi gọi nhầm id CUSTOMER → 422 `Chi doi role cho STAFF`, đúng spec) |
| 15 | `PUT /{id}/status` id không tồn tại | 404 `NOT_FOUND` | ✅ 404 `NOT_FOUND` |

Ngoài ra đã kiểm tra dữ liệu thật trong DB: `register` sinh `accountType=CUSTOMER` + role
`CUSTOMER` trong `account_role`; `POST /accounts` gán đúng role; `GET /accounts` lọc được.

## 7. Hướng dẫn tự test

```powershell
# 1. Unit + web slice (khong can DB):
mvn -f auth-service\pom.xml clean verify   # ky vong: Tests run: 29, Failures: 0, BUILD SUCCESS
```

```powershell
# 2. Boot that: BAT BUOC kiem tra ha tang truoc (Postgres co the bi tat)
docker start rental-postgres; docker start pg-proxy
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force   # tranh khoa jar
$env:AUTH_DB_URL="jdbc:postgresql://localhost:5433/auth_db"
$env:AUTH_DB_USER="auth_user"
$env:AUTH_DB_PASSWORD="change_me_in_dot_env"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:EUREKA_URL="http://localhost:8761/eureka/"
$env:JWT_SECRET="local-dev-test-secret-min-32-chars-00"
java -jar F:\rental-system-backend\auth-service\target\auth-service.jar
```

```powershell
# 3. Test bang Invoke-RestMethod (PowerShell): dung curl.exe -d voi dau nhay "" se hong JSON
$body = @{ username = "khach01@gmail.com"; password = "matkhau123"; fullName = "A"; email = "khach01@gmail.com" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/register" -Method Post -ContentType "application/json" -Body $body
```

Header `X-User-Id/X-User-Type/X-User-Roles` tự gắn tay cho các API ADMIN vì `POST /login`
chưa có (Task C5); sau C5 sẽ dùng Bearer token thật do Gateway gắn.

## 8. Nợ sang task sau (đã định vị, chưa làm)

- **C5**: login/refresh/logout/me/password — `GET /me` hiện vẫn 404 (đã chuẩn bị sẵn principal
  `HeaderUser`); lockout 5 lần/423; refresh rotation.
- **C6**: móc `OutboxEventPublisher` vào `register()/createAccount()` (cùng `@Transactional`)
  để phát `AccountRegistered`; hiện 2 hàm này chưa ghi outbox (đúng thứ tự plan).
- **D1** (gateway): `api-gateway/` chưa tồn tại nên `mvn package` ở root vẫn báo
  `Child module api-gateway does not exist` — có từ trước, không do C4 (build từng module vẫn xanh).
