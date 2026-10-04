# Báo cáo Task C5 – Đăng nhập / Refresh rotation / Logout / Me / Đổi mật khẩu

> Nguồn: `docs/plan-infra-auth-team.md` Task C5 + `docs/Đặc tả…` mục 7.1, Table 7 + `AIRule.md`.
> Quyết định của bạn đã áp dụng: `/me` = header + username từ DB; sai lần 5 trả 423 ngay;
> logout token sai vẫn 204 (idempotent); không tách `PasswordService`.

## 1. File tạo mới (7)

- `dto/LoginRequest.java` (`username/password` bắt buộc), `dto/LoginResponse.java`
  (`accessToken, refreshToken, tokenType:"Bearer", expiresIn, account:{id,username,accountType,roles[]}`
  đúng Table 7), `dto/RefreshRequest.java`, `dto/ChangePasswordRequest.java`
  (`newPassword` min 8), `dto/MeResponse.java`.
- `service/AuthSessionService.java`: `login()` (sai user → 401 không leak; khóa mềm →
  403 + thu hồi token; đang `locked_until` → 423; sai pass → đếm, lần 5 → khóa 15 phút +
  423; đúng → reset đếm + cấp cặp token), `refresh()` (rotation: thu hồi cũ + cấp mới;
  dùng lại token đã thu hồi → **thu hồi toàn bộ phiên** + 401; hết hạn → 401),
  `logout()` (thu hồi 1 token, token sai vẫn 204), `me()` (id/roles từ `HeaderUser`,
  username từ DB), `changePassword()` (sai cũ → 400 `WRONG_OLD_PASSWORD`; xong → thu
  hồi mọi token), `revokeAll()`.
- `service/AccountRoleLookup.java`: đọc role code dùng chung cho `AccountService` và
  `AuthSessionService` (tách ra để không viết trùng logic — AIRule §2).
- `repository/RefreshTokenRepository.java`: thêm `revokeAllByAccountId` (`@Modifying`
  bulk update, chỉ chạm dòng `revoked_at is null`).
- Test: `service/AuthSessionServiceTest` (14 case Mockito, `JwtProvider` thật qua `@Spy`)
  + `controller/AuthSessionWebTest` (10 case web slice với `SecurityConfig` thật:
  login/refresh public, logout/me/password cần header, 423/401/400 đúng mã).

## 2. File sửa (4, tối thiểu)

- `controller/AuthController.java`: thêm `POST /login`, `POST /refresh`, `POST /logout`
  (→ 204), `GET /me`, `PUT /password` (→ 204). Principal lấy bằng
  `@AuthenticationPrincipal HeaderUser`.
- `service/AccountService.java`: `updateStatus(false)` giờ gọi `sessions.revokeAll(id)`
  (khóa tài khoản → mọi phiên hết hiệu lực, mục 7.1); `roleCodesOf()` chuyển sang dùng
  `AccountRoleLookup` (xóa code trùng).
- `service/AuthSessionService.java`: `@Transactional(noRollbackFor = BusinessException.class)`
  trên `login()` và `refresh()` — **fix quan trọng nhất, xem mục 4**.
- `controller/AccountControllerWebTest.java`: thêm `@MockBean AuthSessionService`
  (vì `AuthController` giờ cần bean này).

## 3. Kết quả verify

- `mvn clean verify` → **Tests run: 53, Failures: 0, Errors: 0, BUILD SUCCESS**
  (29 cũ + 14 service + 10 web).
- Test thật với Postgres (boot + DB thật, 18 case, xem mục 5): login/me/password/logout/
  lockout/rotation/replay **đúng toàn bộ**, kể cả 2 lỗi ở mục 4 sau khi fix.

## 4. Lỗi THẬT do test thật bắt được (mock che giấu): rollback nuốt trạng thái

- Hiện tượng: sai pass lần 5 vẫn **401** (phải 423); lần 6 pass đúng vẫn **200**
  (phải 423 vì đã khóa); replay token cũ → 401 đúng, nhưng token **mới** vẫn dùng được
  (phải 401 vì toàn bộ phiên đã bị thu hồi).
- Nguyên nhân: `login()`/`refresh()` ghi trạng thái (`failed_login_count`,
  `locked_until`, `revokeAll`) **rồi mới throw `BusinessException`** → `@Transactional`
  mặc định rollback toàn bộ → DB không giữ gì cả. Unit test không thấy vì mock không
  rollback.
- Fix: `@Transactional(noRollbackFor = BusinessException.class)` trên 2 hàm này.
  An toàn vì mọi điểm throw trong 2 hàm đều đi kèm ghi nhận cần giữ (đếm sai, khóa,
  thu hồi); các hàm khác (`register`, `createAccount`, `changePassword`, `updateRoles`)
  đều throw **trước** khi ghi nên rollback mặc định vẫn đúng, không cần đổi.
- Bài học cho team: **mọi logic "ghi rồi báo lỗi" phải có integration test với DB thật**
  (dự kiến Task E1); mock không bắt được lỗi transaction.

## 5. Ma trận test thật (sau fix, DB thật)

| # | Case | Kết quả |
|---|------|---------|
| 1–2 | login đúng → 200 (`Bearer`, `expiresIn:900`, claims `sub/typ/roles/iss/jti` trong JWT); `/me` → 200 đủ 4 field | ✅ |
| 3–4 | `PUT /password` sai cũ → 400 `WRONG_OLD_PASSWORD`; đúng → 204; login pass cũ → 401; pass mới → 200 | ✅ |
| 5 | refresh token trước khi đổi pass (đã bị thu hồi) → 401 `REFRESH_TOKEN_INVALID` | ✅ |
| 6–8 | rotation ra cặp mới 200; dùng lại token cũ → 401; token mới cũng 401 (toàn bộ phiên bị thu hồi) | ✅ |
| 9–10 | login lại → logout → 204; refresh sau logout → 401 | ✅ |
| 11–13 | sai pass 4 lần → 401; lần 5 → **423** `ACCOUNT_LOCKED` (khóa 15 phút); pass đúng khi đang khóa → **423** | ✅ |

## 6. Hướng dẫn tự test (lưu ý 3 bẫy Windows đã gặp ở C4)

```powershell
docker start rental-postgres; docker start pg-proxy   # BAT BUOC: kiem tra 5433 OPEN truoc
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force  # tranh khoa jar
$env:AUTH_DB_URL="jdbc:postgresql://localhost:5433/auth_db"
$env:AUTH_DB_USER="auth_user"; $env:AUTH_DB_PASSWORD="change_me_in_dot_env"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"; $env:EUREKA_URL="http://localhost:8761/eureka/"
$env:JWT_SECRET="local-dev-test-secret-min-32-chars-00"
java -jar F:\rental-system-backend\auth-service\target\auth-service.jar
```

```powershell
# DUNG Invoke-RestMethod (dung curl.exe -d voi dau "" se hong JSON tren PowerShell):
$login = Invoke-RestMethod http://localhost:8081/api/v1/auth/login -Method Post `
  -ContentType "application/json" -Body (@{username="x@gmail.com"; password="matkhau123"} | ConvertTo-Json)
$R = $login.refreshToken
Invoke-RestMethod http://localhost:8081/api/v1/auth/refresh -Method Post `
  -ContentType "application/json" -Body (@{refreshToken=$R} | ConvertTo-Json)
```

Header ADMIN/STAFF tự gắn tay (`X-User-Id/Type/Roles`) vì Gateway (D2) chưa có;
sau D2 sẽ dùng Bearer token thật.

## 7. Nợ sang task sau

- **C6**: móc `OutboxEventPublisher` vào `register()/createAccount()` (cùng
  `@Transactional`) phát `AccountRegistered`; thêm `OutboxRelay` (`@Scheduled` 1s,
  batch 100, `acks=all` + idempotence đã cấu hình sẵn).
- **D1–D3** (gateway): route + `JwtAuthenticationFilter` + whitelist + chặn `/internal/**`.
- **E1**: integration test DB thật (Testcontainers, image `postgres:16-alpine` đã cache
  sẵn) — mục 4 chứng minh không thể thiếu.
