# Báo cáo Task D1 + D2 + D3 + E1 – API Gateway & Nghiệm thu Giai đoạn 0

> Nguồn: `docs/plan-infra-auth-team.md` Epic D + E1 + `docs/Đặc tả…` mục 4.2, 6.1, 8,
> Table 2 (routes), Table 6 (headers), Table 19 (nghiệm thu) + `AIRule.md`.
> Quyết định của bạn: IT chỉ auth+Kafka (gateway mô phỏng header); dùng failsafe;
> CORS origins theo env + methods/headers `*`.

## 1. D1 – Khung gateway (port 8080)

- `api-gateway/pom.xml` (NEW): `spring-cloud-starter-gateway` (WebFlux),
  `spring-cloud-starter-netflix-eureka-client`, `jjwt-api/impl/jackson`,
  `common-lib` (chỉ dùng hằng `Headers`, enum `ErrorCode`, DTO `ErrorResponse` —
  KHÔNG dùng advice MVC vì gateway reactive), `actuator`, `starter-test`.
  Version do BOM Boot/Cloud ở root pom quản lý, không hardcode.
- `GatewayApplication.java` (NEW): `@SpringBootApplication` thuần (không logic).
- `application.yml` (NEW): 7 route **đúng Table 2 spec** (`auth`, `user-notification`,
  `property`, `listing-booking`, `contract-tenant`, `billing-utility`,
  `asset-maintenance` → `lb://...`), **không** StripPrefix (downstream giữ `/api/v1`),
  `discovery.locator.enabled=false`; `jwt.secret/issuer` cùng auth-service;
  `management` expose `health,info` (compose healthcheck dùng).
- `Dockerfile` (NEW): mẫu C1/B1 (multi-stage, build từ repo root).
- `infra/docker-compose.yml` (EDIT): section gateway đang `build: ../api-gateway`
  (context sai — không thấy parent pom) → `context: .., dockerfile:
  api-gateway/Dockerfile`, giống discovery/auth. Root pom đã có module ✓ không sửa.

## 2. D2 – Xác thực JWT tại gateway (đúng thứ tự mục 6.1)

- `filter/CorrelationIdFilter.java` (NEW, order -101, chạy đầu): giữ
  `X-Correlation-Id` client gửi, thiếu thì sinh UUID; log mọi request kèm id (D3).
- `filter/JwtAuthenticationFilter.java` (NEW, order -100):
  1. `/internal` hoặc `/internal/**` từ ngoài → **404** `NOT_FOUND` (kênh nội bộ).
  2. Xóa `X-User-Id/Type/Roles` client tự gửi (chống giả mạo) — dòng đầu tiên.
  3. Whitelist đúng plan: `POST /api/v1/auth/register|login|refresh`;
     `GET /api/v1/locations/**`, `/api/v1/buildings/nearby`, `/api/v1/listings`,
     `/api/v1/listings/*` (1 đoạn).
  4. Còn lại: thiếu/sai `Authorization: Bearer` → 401 `UNAUTHENTICATED`;
     verify HS256 + `iss=rental-auth` + `exp` (dung sai 30s): hết hạn → 401
     `TOKEN_EXPIRED`, sai chữ ký → 401.
  5. Hợp lệ → gắn `X-User-Id=sub`, `X-User-Type=typ`, `X-User-Roles=roles` (join ",")
     rồi forward. Secret < 32 ký tự fail-fast khi start (giống `JwtProvider`).
- `filter/GatewayWhitelist.java` + `config/GatewaySecurityConfig.java` (NEW): whitelist
  tách thành bean riêng để filter tái dùng và dễ test (CORS đã khai trong yml nên
  config class không lặp lại).
- `JwtAuthenticationFilterTest` (9 case, Mock exchange, không cần Netty/Eureka):
  register public 201-passthrough; `/me` không token → 401; token hết hạn →
  `TOKEN_EXPIRED`; sai chữ ký → `UNAUTHENTICATED`; header giả `X-User-Id: 999` bị xóa
  và thay bằng id thật; nhiều role nối phẩy; `/internal/**` → 404; listings public;
  secret ngắn fail-fast.

## 3. D3 – CORS + lỗi chuẩn + log

- `application.yml` (EDIT trong D1): `globalcors` — origins từ
  `${CORS_ALLOWED_ORIGINS:http://localhost:3000}`, methods/headers `*`,
  `allowCredentials: true`, `maxAge: 3600`.
- `handler/GatewayErrorHandler.java` (NEW, `ErrorWebExceptionHandler` order -2, ghi đè
  mặc định của Boot): mọi lỗi filter không bắt (no-route 404 → `NOT_FOUND`,
  LB không thấy service 503 → `DEPENDENCY_UNAVAILABLE`, còn lại `INTERNAL_ERROR`)
  đều trả đúng format `{code,message,status,traceId}` (mục 8, `traceId=correlationId`).
- `GatewayErrorHandlerTest` (4 case): 404/503/500 đúng mã + `traceId` lấy từ
  `X-Correlation-Id`.
- Log truy vết: `CorrelationIdFilter` log `METHOD path [cid]` khi vào và
  `METHOD path -> status [cid]` khi xong.

## 4. E1 – Nghiệm thu Giai đoạn 0

- `auth-service/.../AuthFlowIT.java` (NEW, failsafe `*IT`): Testcontainers
  `postgres:16-alpine` + `confluentinc/cp-kafka:7.6.1` (cả 2 image đã cache),
  `@DynamicPropertySource` cờ `AUTH_DB_URL/USER/PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`
  (không thêm dep mới): register → login → `/me` (header mô phỏng gateway) →
  refresh rotation → dùng lại token cũ → 401 + toàn bộ phiên bị thu hồi → login lại →
  logout → refresh → 401 → consume `auth.events` tối đa 30s, assert envelope
  `AccountRegistered` v1 đúng `accountId/username`. `@SpringBootTest(classes=...)`
  tường minh.
- `auth-service/pom.xml` (EDIT): `maven-failsafe-plugin` chạy `integration-test` +
  `verify`; `<classesDirectory>target/classes` (BAT BUOC — jar repackage BOOT-INF
  không đọc được trên classpath, xem mục 6.2); `api.version=1.44` cho Docker Engine
  mới (mục 6.1).
- `pom.xml` root (EDIT): import `testcontainers-bom` **trước** Boot BOM + property
  `testcontainers.version=1.21.3` (vượt 1.19.8 của Boot; import lồng nhau không
  override được bằng property — import trực tiếp mới thắng).
- `auth-service/pom.xml` (EDIT): thêm `testcontainers:junit-jupiter` (module còn thiếu
  cho `@Testcontainers/@Container`; cùng họ lib đã duyệt C1, test-scope).
- `.github/workflows/ci.yml` (NEW): push/PR → Java 21 (temurin) → `mvn -B clean verify`
  (runner ubuntu có sẵn Docker cho Testcontainers).
- Swagger: auth-service đã có springdoc từ C1 — verify live `/v3/api-docs` 200,
  openapi 3.0.1 đủ 10 endpoint auth + schema (mục 7 báo cáo). Discovery/gateway không
  thêm Swagger (ngoài scope Giai đoạn 0).

## 5. Kết quả verify

- Unit: gateway **13/13** (9 filter + 4 handler), auth-service **58/58** —
  `mvn clean verify` từng module BUILD SUCCESS.
- `AuthFlowIT`: **1/1 pass (36.7s)** — full flow trên Postgres + Kafka thật trong
  container riêng.
- **Live test qua `:8080`** (Eureka + auth + gateway cùng chạy, JWT_SECRET chung):

| # | Case | Kết quả |
|---|------|---------|
| 1 | `POST /api/v1/auth/register` | ✅ 201 `{"accountId":11,...,"accountType":"CUSTOMER"}` |
| 2 | `POST /api/v1/auth/login` | ✅ 200 `accessToken(264 ký tự) + refreshToken + Bearer/900` |
| 3 | `GET /me` không token | ✅ 401 `UNAUTHENTICATED/Chua dang nhap` |
| 4 | `GET /me` kèm Bearer | ✅ 200 đúng user |
| 5 | Bearer + `X-User-Id: 999/ADMIN` giả | ✅ 200 trả **id thật 11** (header giả bị xóa và thay) |
| 6 | `GET /internal/staff/1/buildings` kèm Bearer | ✅ 404 `NOT_FOUND/Khong tim thay` |
| 7 | `GET /me` token rác | ✅ 401 `UNAUTHENTICATED/Token khong hop le` |
| 8 | `GET /actuator/health` gateway | ✅ 200 `{"status":"UP"}` |
| 9 | Auth Swagger `/v3/api-docs` | ✅ 200 openapi 3.0.1 đủ endpoint/schema |

## 6. Ba sự cố môi trường/test (đã fix, ghi lại để team tránh)

1. **Docker Engine ≥ 29 chỉ chấp nhận API ≥ 1.40, Testcontainers ≤ 1.21 gửi 1.32**
   (`client version 1.32 is too old`): nâng `testcontainers-bom` 1.19.8 → 1.21.3
   (phải import trực tiếp trước Boot BOM) + `-Dapi.version=1.44` cho fork failsafe
   (docker-java đọc key này; Testcontainers chỉ ép 1.32 khi version UNKNOWN —
   đã xác minh bằng bytecode).
2. **Failsafe dùng `target/auth-service.jar` (BOOT-INF) thay vì `target/classes`** →
   Spring không thấy `@SpringBootConfiguration` (`Unable to find...`, rồi
   `Failed to find merged annotation for @BootstrapWith`). Fix chuẩn:
   `<classesDirectory>${project.build.outputDirectory}</classesDirectory>`.
   Surefire không bị vì chạy trước phase `package`.
3. **KafkaContainer 1.21.3 không hỗ trợ image `apache/kafka`** (bắt
   `asCompatibleSubstituteFor`, mà khai báo thì sai wait-strategy → exit 127):
   dùng `confluentinc/cp-kafka:7.6.1` (đã cache) chạy native KRaft OK.
   Phụ: diagnostic `TempIT` đã xóa sau khi xong việc; unit `mvn test` không cần Docker.

## 7. Checklist Table 19 Giai đoạn 0 (phía team infra+auth)

- [x] `POST /register` qua `:8080` → 201; trùng → 409 (C4).
- [x] `POST /login` → cặp token; `GET /me` Bearer → 200 (mục 5).
- [x] Sai pass 5 lần → 423 khóa 15 phút (C5 live test).
- [x] Refresh rotation + replay → 401 + thu hồi toàn bộ (C5 + E1 IT).
- [x] `auth.events` có `AccountRegistered` đúng envelope (C6 + E1 IT).
- [x] `/internal/**` từ ngoài → 404; `X-User-Id` giả → bị thay bằng id thật (mục 5).
- [x] Mọi lỗi đúng `{code,message,status,traceId}`; không secret trong git.
- [ ] Vế User Service (tạo hồ sơ tự động từ event) — team bạn kia (ngoài scope file này).
