# Báo cáo Task C3 – JWT + BCrypt (JwtProvider, RefreshTokenService, SecurityConfig)

> Nguồn: `docs/plan-infra-auth-team.md` Task C3 + `docs/Đặc tả…` mục 6.2, 7.1, Table 7 + `AIRule.md`.

## 1. Đã làm gì

- `auth-service/.../security/JwtProvider.java` (NEW): ký/parse access token HS256 (jjwt 0.12).
  Claims đúng mục 7.1: `sub=accountId, typ, roles, iss=rental-auth, iat, exp=+900s, jti=uuid`.
  Secret đọc từ `${jwt.secret}`, < 32 ký tự thì fail-fast `IllegalStateException` khi start.
  Hết hạn → `BusinessException(TOKEN_EXPIRED)`; sai chữ ký/token rác → `UNAUTHENTICATED`.
  Dung sai `clockSkewSeconds=30`.
- `auth-service/.../security/JwtClaims.java` (NEW): record `accountId, userType, roles, tokenId`.
- `auth-service/.../security/RefreshTokenService.java` (NEW): sinh chuỗi ngẫu nhiên 32 byte
  (Base64 URL-safe), TTL 7 ngày (`REFRESH_TOKEN_TTL`), DB chỉ lưu hash SHA-256 (hex),
  so sánh constant-time (`MessageDigest.isEqual`). Rotation để Task C5.
- `auth-service/.../config/SecurityConfig.java` (NEW): stateless, tắt CSRF/Basic/Form,
  `@EnableMethodSecurity` (để C4 dùng `@PreAuthorize`), `PasswordEncoder` BCrypt cost 10
  (đóng vai `PasswordService` trong tiêu đề task — không tạo class wrapper thừa, DRY).
  PermitAll: `POST /api/v1/auth/register|login|refresh` + `GET /actuator/health|info`,
  `/v3/api-docs/**`, `/swagger-ui/**`. Còn lại `authenticated`.
  Filter `AuthHeaderAuthenticationFilter` gắn trước `AuthorizationFilter`.
  EntryPoint 401 `UNAUTHENTICATED/Chua dang nhap`, handler 403 `FORBIDDEN/Khong du quyen`,
  đúng format `ErrorResponse` (viết thẳng JSON vì filter chạy trước ControllerAdvice).
- `auth-service/.../config/PublicPaths.java` (NEW): hằng POST/GET public dùng chung cho
  cả `SecurityConfig` (permitAll) và filter (bỏ qua kiểm tra header).
- `auth-service/.../config/AuthHeaderAuthenticationFilter.java` (NEW): kế thừa
  `HeaderAuthenticationFilter` của common-lib, override `isSkipped()` để bỏ qua đúng
  các đường public (register/login/refresh, swagger, actuator, internal).
- `common-lib/.../security/HeaderAuthenticationFilter.java` (EDIT): thêm log DEBUG
  (`Bo qua…/Thieu header…/Da xac thuc…`) để truy vết, bật bằng
  `LOGGING_LEVEL_COM_RENTAL=DEBUG`. Không đổi logic.
- `auth-service/.../config/SecurityConfig.java` (EDIT chốt C3): thêm
  `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` — xem mục 3.
- Test (NEW, 10 test): `JwtProviderTest` (5: đúng claims, hết hạn→TOKEN_EXPIRED,
  sai ký→UNAUTHENTICATED, token rác→UNAUTHENTICATED, secret ngắn→fail-fast),
  `RefreshTokenServiceTest` (3), `AuthHeaderAuthenticationFilterTest` (2: public bị bỏ qua,
  đường cần auth không bị bỏ qua).
- `application.yml`: đã có `jwt.secret=${JWT_SECRET:...}`, `jwt.issuer=rental-auth`,
  `jwt.access-expiration=900` (từ C1, dùng luôn, không hardcode secret).

## 2. Kết quả verify (không cần boot, không cần Eureka)

- `mvn -f common-lib/pom.xml install -q` → SUCCESS.
- `mvn -f auth-service/pom.xml verify` → **Tests run: 10, Failures: 0, Errors: 0**,
  **BUILD SUCCESS**, jar `auth-service/target/auth-service.jar` build mới.
- Không thêm lib mới (jjwt/security đã duyệt từ C1), không sửa migration, không secret trong git.

## 3. Nguyên nhân gốc vụ 401 `/api/v1/auth/me` kèm header (đã giải thích, đã fix hệ quả)

- Chuỗi chứng cứ từ log security DEBUG (`DefaultSecurityFilterChain`,
  `AnonymousAuthenticationFilter`, `FilterChainProxy Securing/Secured /error`):
  request kèm header **đã xác thực thành công** (filter set `Authentication`,
  `AuthorizationFilter` cho qua) → `DispatcherServlet` không tìm thấy controller `/me`
  (đó là Task C5, chưa viết) → 404 → container forward sang `/error`.
- Trên dispatch `/error`: `OncePerRequestFilter` thấy attribute `*.FILTERED` đã có nên
  **bỏ qua** filter của ta, còn `AnonymousAuthenticationFilter` (không phải OncePerRequest)
  chạy lại và gắn anonymous → `AuthorizationFilter` từ chối → entryPoint trả 401
  `UNAUTHENTICATED/Chua dang nhap` gây hiểu lầm.
- Kết luận: **không phải lỗi xác thực**; filter chain hoạt động đúng.
- Fix: `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` trong `SecurityConfig`
  để dispatch lỗi hiển thị đúng mã gốc (404 khi chưa có controller) thay vì 401 giả.

## 4. Hướng dẫn tự test (cho bạn)

### 4.1. Test nhanh không cần DB/Eureka (khuyên dùng)

```powershell
mvn -f common-lib\pom.xml install -q
mvn -f auth-service\pom.xml verify
```

Kỳ vọng: `Tests run: 10, Failures: 0, Errors: 0` + `BUILD SUCCESS`.

### 4.2. Boot thật auth-service (cần Postgres qua pg-proxy :5433, KHÔNG cần Eureka)

```powershell
$env:AUTH_DB_URL="jdbc:postgresql://localhost:5433/auth_db"
$env:AUTH_DB_USER="auth_user"
$env:AUTH_DB_PASSWORD="change_me_in_dot_env"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:EUREKA_URL="http://localhost:8761/eureka/"
$env:JWT_SECRET="local-dev-test-secret-min-32-chars-00"
$env:LOGGING_LEVEL_COM_RENTAL="DEBUG"
java -jar F:\rental-system-backend\auth-service\target\auth-service.jar
```

(Lỗi Eureka `Cannot execute request on any known server` khi chưa chạy discovery là
vô hại — client tự retry; service vẫn UP. Kafka chưa chạy cũng không chặn start.)

### 4.3. Ma trận curl kỳ vọng (sau fix ERROR-dispatch)

```powershell
# 1. Health: 200 UP
curl.exe -s http://localhost:8081/actuator/health
# 2. Swagger: 200 (đường public, filter bỏ qua)
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8081/v3/api-docs
# 3. /me KHÔNG header: 401 + {"code":"UNAUTHENTICATED","message":"Request khong di qua Gateway",...}
curl.exe -s http://localhost:8081/api/v1/auth/me
# 4. /me CÓ header: 404 (chưa có controller — Task C5), KHÔNG còn 401 gây hiểu lầm
curl.exe -s -w "`n%{http_code}`n" -H "X-User-Id: 7" -H "X-User-Type: CUSTOMER" -H "X-User-Roles: CUSTOMER" http://localhost:8081/api/v1/auth/me
# 5. /register KHÔNG header: 404 (chưa có controller — Task C4), chứng tỏ filter đã bỏ qua
curl.exe -s -o NUL -w "%{http_code}`n" -X POST http://localhost:8081/api/v1/auth/register
```

### 4.4. Kiểm tra JWT/refresh bằng test (chưa có API login — Task C5)

Viết test tạm hoặc dùng `JwtProviderTest` làm mẫu: `generateAccessToken(7L, "CUSTOMER",
List.of("CUSTOMER"))` → `parseAndValidate` trả đúng `JwtClaims`. Secret < 32 ký tự →
`IllegalStateException` ngay khi khởi tạo bean (fail-fast khi start app nếu `.env` sai).

## 5. Quyết định cần bạn chốt (theo AIRule §1, §5)

1. `PasswordEncoder` BCrypt(10) trong `SecurityConfig` đã đủ vai `PasswordService`
   (C4 `AccountService` inject trực tiếp) — bạn có muốn tách class `PasswordService`
   riêng không? Đề xuất: không (tránh wrapper thừa).
2. Task tiếp theo là **C4** (Register + AdminAccountController + AccountService).
   Khi bạn đồng ý, tôi xuất plan (file NEW/EDIT + logic) để bạn duyệt trước code (AIRule §3).
