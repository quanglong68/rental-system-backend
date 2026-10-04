# PLAN – Nhóm Hạ tầng + Auth (discovery-server, api-gateway, auth-service, common-lib)

> Nguồn: `docs/Đặc tả Hệ thống Microservices – Quản lý Cho thuê Phòng.docx` (Bản nháp v0.2, 03/10/2026) + `AIRule.md`.
> Phạm vi của bạn: **discovery-server (Eureka) – api-gateway (Routing, Xác thực JWT) – auth-service (Đăng ký, đăng nhập, cấp token) – common-lib (lỗi chuẩn, hằng header, envelope event, HeaderAuthenticationFilter)**.
> Nguyên tắc áp dụng: Database per service, Gateway xác thực / service phân quyền (mục 6), Transactional Outbox + Idempotent consumer (mục 5.3), không hardcode secret, mọi lib mới phải xin phép (AIRule §5).

---

## 0. Đã chốt với bạn (03/10/2026 – theo AIRule §1)

| # | Quyết định đã chốt |
|---|--------------------|
| Q1 | Máy đã có Java 21 + Maven + Docker. Làm đúng mono-repo Maven multi-module: `pom.xml` cha + module `common-lib, discovery-server, api-gateway, auth-service`. Stack: Java 21, Boot 3.3.x, Cloud 2023.0.x, jjwt 0.12, Postgres 16 + Flyway, Kafka KRaft 1 broker. |
| Q2 | `.env.example` để placeholder `JWT_SECRET=your_32_character_secret_here` (không commit `.env` thật). Dev tự tạo `.env` điền secret ≥32 ký tự. Refresh token lưu hash SHA-256 trong DB. Access 15', refresh 7 ngày, `iss=rental-auth`. |
| Q3 | Seed role bằng Flyway `V2__seed_roles.sql` (5 role: ADMIN, QUAN_LY, KY_THUAT, SALE, CUSTOMER). Clone về chạy DB là có sẵn, không lệnh phụ trợ. Không sửa migration đã merge. |
| Q4 | Bạn nhận làm `infra/docker-compose.yml` + `.env.example`. Compose dựng: postgres (tạo sẵn 7 DB qua `POSTGRES_MULTIPLE_DATABASES`), kafka (tạo sẵn topic `auth.events`, 3 partition) + kafka-ui, minio (bucket `report-images`), discovery, gateway, auth. |
| Q5 | Đồng ý Task A2 (lỗi chuẩn + Header filter + Envelope trong common-lib). **Bổ sung: bạn làm luôn `FeignConfig` trong common-lib** (Task A4 mới) để các team tái dùng: sao chép header + `SYSTEM` cho scheduler + timeout/circuit-breaker + ErrorDecoder. |

> Plan bên dưới đã cập nhật theo 5 chốt trên.

---

## 1. Cấu trúc repo đề xuất (tạo mới – hiện repo trống)

```
rental-system-backend/
├── pom.xml                          # NEW – parent pom, quản lý version Boot/Cloud/jjwt/mapstruct
├── .env.example                     # NEW – JWT_SECRET, DB_*, KAFKA_*, EUREKA_URL (không commit .env thật)
├── infra/
│   └── docker-compose.yml           # NEW – postgres, kafka, kafka-ui, minio, discovery, gateway, auth
├── common-lib/                      # NEW module
│   └── pom.xml
├── discovery-server/                # NEW module
│   └── pom.xml
├── api-gateway/                     # NEW module
│   └── pom.xml
├── auth-service/                    # NEW module
│   └── pom.xml
└── docs/
    └── PLAN-...md (file này)
```

> Tuân thủ AIRule §3: mọi file NEW/EDIT được liệt kê cụ thể trong từng Task bên dưới.

---

## 2. Chia nhỏ đầu việc (7 Epic, 23 Task – đã thêm A4 FeignConfig)

### EPIC 0 – Khởi tạo nền móng (làm trước tiên)

#### Task 0.1 – Parent `pom.xml` + quy ước chung
- **File tạo mới:** `pom.xml` (root).
- **Logic:** `<packaging>pom</packaging>`, modules `common-lib, discovery-server, api-gateway, auth-service`; dependencyManagement: `spring-boot 3.3.x`, `spring-cloud 2023.0.x`, `jjwt 0.12.6`, `mapstruct`, `lombok`, `flyway`, `springdoc-openapi`, `resilience4j` (cho Feign sau này); properties `java.version=21`, charset UTF-8, giờ UTC.
- **Done:** `mvn validate` xanh.

#### Task 0.2 – `infra/docker-compose.yml` + `.env.example`
- **File tạo mới:** `infra/docker-compose.yml`, `.env.example`, `infra/postgres/init-multiple-dbs.sh` (dùng biến `POSTGRES_MULTIPLE_DATABASES=auth_db,user_db,property_db,listing_db,contract_db,billing_db,asset_db`).
- **Logic:** services `postgres (postgis/postgis:16-3.4, 5432)`, `kafka (KRaft, 9092, tạo sẵn topic `auth.events` 3 partition RF=1 qua biến `KAFKA_CREATE_TOPICS` hoặc script init)`, `kafka-ui (8090)`, `minio (9000 API + 9001 console, bucket `report-images`)`, `discovery-server (8761)`, `auth-service (8081)`, `api-gateway (8080)`. **Chỉ publish `8080` (gateway) + `8090` + `9001` ra host** (mục 4, 6.2, 12). Thứ tự start: postgres/kafka/minio → discovery → service → gateway (`depends_on` + healthcheck).
- **`.env.example` (placeholder, không giá trị thật):** `JWT_SECRET=your_32_character_secret_here`, `POSTGRES_MULTIPLE_DATABASES=...`, `AUTH_DB_URL/USER/PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`, `EUREKA_URL=http://discovery-server:8761/eureka/`, `MINIO_*`, `CORS_ALLOWED_ORIGINS=http://localhost:3000`. Dev tự copy thành `.env` và điền secret.
- **Done:** `docker compose up postgres kafka discovery` chạy được; Eureka dashboard mở được; Kafka UI thấy topic `auth.events`.

---

### EPIC A – `common-lib` (làm thứ 2, vì gateway + auth đều phụ thuộc)

#### Task A1 – Khung module + hằng số dùng chung
- **File tạo mới:**
  - `common-lib/pom.xml` (deps: `lombok`, `spring-security-core`, `spring-web`, `jakarta.validation`, `jackson-databind`)
  - `common-lib/src/main/java/com/rental/common/constant/Headers.java` (hằng `X-User-Id, X-User-Type, X-User-Roles, X-Correlation-Id`)
  - `common-lib/src/main/java/com/rental/common/constant/UserTypes.java` (`CUSTOMER, STAFF, ADMIN, SYSTEM`)
  - `common-lib/src/main/java/com/rental/common/event/EventEnvelope.java` (đúng envelope mục 5.3: `eventId, eventType, version, occurredAt, correlationId, payload`)
- **Logic:** hằng dùng `public static final String`; `EventEnvelope<T>` generic để mọi service tái dùng; `occurredAt` ISO-8601 UTC.
- **Done:** `mvn -pl common-lib install` xanh.

#### Task A2 – Lỗi chuẩn + handler toàn cục
- **File tạo mới:**
  - `.../common/error/ErrorCode.java` (enum: `VALIDATION_ERROR, UNAUTHENTICATED, TOKEN_EXPIRED, FORBIDDEN, NOT_FOUND, USERNAME_EXISTS, …, DEPENDENCY_UNAVAILABLE, INTERNAL_ERROR` – theo Table 8 + Table 7)
  - `.../common/error/ErrorResponse.java` (`code, message, status, traceId, details[]` – đúng mẫu mục 8, `traceId = correlationId`)
  - `.../common/error/BusinessException.java`, `NotFoundException.java`, `DependencyUnavailableException.java`
  - `.../common/error/GlobalExceptionHandler.java` (`@RestControllerAdvice`: map exception → đúng HTTP status Table 8)
  - `.../common/error/FieldViolation.java` (`field, message`)
- **Logic:** service chỉ `throw new BusinessException("USERNAME_EXISTS", "...", 409)`; handler tự bọc thành JSON chuẩn. Không nuốt lỗi (mục 5.2).
- **Done:** unit test: throw `NotFoundException` → response đúng `{code,status:404,traceId}`.

#### Task A3 – `HeaderAuthenticationFilter` (xương sống phân quyền, mục 6.2)
- **File tạo mới:**
  - `.../common/security/HeaderAuthenticationFilter.java` (extends `OncePerRequestFilter`)
  - `.../common/security/HeaderUser.java` (principal: `userId, userType, roles`)
  - `common-lib/src/test/java/.../HeaderAuthenticationFilterTest.java`
- **Logic:**
  1. Đọc `X-User-Id, X-User-Type, X-User-Roles` (do Gateway gắn, mục 6.1); giữ `X-Correlation-Id` cho log (MDC).
  2. Không có header → `401 {code: UNAUTHENTICATED}` (request không qua Gateway).
  3. Có header → tạo `UsernamePasswordAuthenticationToken(principal=HeaderUser, authorities=ROLE_<role>)` bỏ vào `SecurityContext`.
  4. Bỏ qua đường `/internal/**`, `/actuator/**` (không kiểm quyền người dùng, mục 6.2).
- **Done:** test 3 case: đủ header → 200 + authority đúng; thiếu header → 401; `/internal/**` → pass.

#### Task A4 – `FeignConfig` dùng chung (bạn nhận làm thêm – mục 5.2)
- **File tạo mới:**
  - `.../common/feign/FeignHeaderInterceptor.java` (implements `RequestInterceptor`: copy `X-User-Id, X-User-Type, X-User-Roles, X-Correlation-Id` từ request hiện tại sang Feign; nếu không có request (scheduler) thì gửi `X-User-Type: SYSTEM` + tự sinh `X-Correlation-Id`)
  - `.../common/feign/FeignErrorDecoder.java` (implements `ErrorDecoder`: 404 → `NotFoundException`; 4xx khác → `BusinessException` giữ code bên kia; 5xx/timeout/mạch mở → `DependencyUnavailableException` → 503 `DEPENDENCY_UNAVAILABLE`; không nuốt lỗi, không trả dữ liệu giả)
  - `.../common/feign/FeignDefaultConfig.java` (`@Configuration`: khai báo 2 bean trên; timeout `connectTimeout=2000ms, readTimeout=3000ms`; GET retry tối đa 1 lần, POST/PUT/DELETE không retry; Resilience4j circuit breaker: window 10 cuộc gọi, failureRate 50%, open 10s)
  - `common-lib/src/test/java/.../FeignErrorDecoderTest.java`
- **File sửa:** `common-lib/pom.xml` (thêm `spring-cloud-starter-openfeign`, `spring-cloud-starter-loadbalancer`, `spring-cloud-starter-circuitbreaker-resilience4j`, `feign-micrometer` nếu cần).
- **Logic / cách team khác dùng:** mỗi service gọi Feign chỉ cần `@FeignClient(name="property-service", configuration=FeignDefaultConfig.class)` + interface trong package `client`, DTO response tự khai báo field mình cần (mục 5.2). Endpoint `/internal/**` chỉ gọi nội bộ.
- **Done:** test: 404 → `NotFoundException`; 503/timeout → `DependencyUnavailableException`; interceptor copy đủ 4 header; scheduler không header → có `SYSTEM`.

---

### EPIC B – `discovery-server` (Eureka, port 8761, mục 4.1)

#### Task B1 – Khung Eureka Server
- **File tạo mới:**
  - `discovery-server/pom.xml` (`spring-cloud-starter-netflix-eureka-server`, `spring-boot-starter-actuator`)
  - `discovery-server/src/main/java/com/rental/discovery/DiscoveryServerApplication.java` (`@EnableEurekaServer`)
  - `discovery-server/src/main/resources/application.yml`
  - `discovery-server/Dockerfile`
- **File sửa:** `pom.xml` root (thêm `<module>discovery-server</module>`), `infra/docker-compose.yml` (thêm service `discovery-server`).
- **Logic (`application.yml`):** `server.port=8761`; `spring.application.name=discovery-server`; `eureka.client.register-with-eureka=false; fetch-registry=false`; `eureka.server.enable-self-preservation=true` (dev có thể tắt); expose `actuator/health`.
- **Done:** chạy lên, mở `http://localhost:8761` thấy dashboard; log không báo lỗi peer.

---

### EPIC C – `auth-service` (port 8081, mục 7)

#### Task C1 – Khung service + cấu hình nền
- **File tạo mới:**
  - `auth-service/pom.xml` (web, validation, data-jpa, flyway, postgres, security-crypto (BCrypt), `jjwt-api/impl/jackson`, `spring-kafka`, `common-lib`, `springdoc-openapi`, `actuator`, `mapstruct`, testcontainers)
  - `.../auth/AuthServiceApplication.java`
  - `auth-service/src/main/resources/application.yml` (`server.port=8081`, `spring.application.name=auth-service`, datasource `auth_db` qua env `AUTH_DB_URL`, `eureka.client.service-url.defaultZone=${EUREKA_URL}`, kafka `bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}`, `jwt.secret=${JWT_SECRET}`, `jwt.access-expiration=900`, `jwt.issuer=rental-auth`)
  - `auth-service/Dockerfile`
- **File sửa:** root `pom.xml`, `infra/docker-compose.yml`.
- **Logic:** Eureka client (`@EnableDiscoveryClient` nếu cần); JPA `ddl-auto=validate` (chỉ Flyway tạo bảng); múi giờ UTC; CORS **không** mở ở đây (Gateway làm).
- **Done:** service đăng ký vào Eureka với tên `auth-service`; `/actuator/health` UP; Swagger mở được.

#### Task C2 – Database + Flyway migration (schema mục 7.3)
- **File tạo mới:**
  - `auth-service/src/main/resources/db/migration/V1__init_auth.sql` (tạo `account, role, account_role, refresh_token, outbox_event, processed_event`; `created_at/updated_at` mọi bảng – mục 1; `username unique`, `token_hash unique`, `account_role` PK kép)
  - `V2__seed_roles.sql` (insert 5 role)
  - Entity: `Account.java`, `Role.java`, `AccountRole.java` (hoặc `@ManyToMany`), `RefreshToken.java`, `OutboxEvent.java`, `ProcessedEvent.java`
  - Repository tương ứng trong `.../auth/repository/`
- **Logic:** `password_hash` lưu BCrypt cost 10; `is_active default true`; `failed_login_count default 0`; `outbox_event(id uuid pk, topic, event_key, event_type, payload jsonb/text, created_at, published_at)`; tiền/mật khẩu **không** hardcode.
- **Done:** `mvn flyway:migrate` (hoặc boot lần đầu) tạo đủ 6 bảng; không sửa migration đã merge (mục 12).

#### Task C3 – JWT + BCrypt (`JwtProvider`, `PasswordService`)
- **File tạo mới:**
  - `.../auth/security/JwtProvider.java` (dùng jjwt 0.12, HS256)
  - `.../auth/security/RefreshTokenService.java` (sinh chuỗi ngẫu nhiên 32+ byte, lưu **hash SHA-256**, so sánh constant-time)
  - `.../auth/config/SecurityConfig.java` (stateless, permitAll `/api/v1/auth/register, /login, /refresh`; còn lại authenticated – Gateway đã chặn trước nhưng service vẫn giữ; dùng `HeaderAuthenticationFilter` từ common-lib)
  - `auth-service/src/test/java/.../JwtProviderTest.java`
- **Logic claims (mục 7.1):** `sub=accountId, typ=CUSTOMER/STAFF/ADMIN, roles=[...], iss=rental-auth, iat, exp=+15ph, jti=uuid`. Sai chữ ký → `UNAUTHENTICATED`; hết hạn → `TOKEN_EXPIRED`. Refresh sống 7 ngày, rotation triệt để (xem C5).
- **Done:** test: ký → parse đúng claims; token hết hạn bị từ chối; secret < 32 ký tự thì fail-fast khi start.

#### Task C4 – API Đăng ký + Tạo tài khoản STAFF/ADMIN (Table 7)
- **File tạo mới:**
  - `.../auth/dto/RegisterRequest.java` (`username, password(min 8), fullName, email?, phone?` + `@Valid`), `RegisterResponse.java` (`accountId, username, accountType`)
  - `.../auth/dto/CreateAccountRequest.java` (`username, password, fullName, email?, phone?, accountType: STAFF|ADMIN, roles[]`), tương tự response
  - `.../auth/service/AccountService.java` (validate `username` là email hoặc SĐT và trùng `email/phone` khai báo – mục 7.1; check `USERNAME_EXISTS` → 409; BCrypt cost 10; gán role CUSTOMER mặc định khi register)
  - `.../auth/controller/AuthController.java` (`POST /api/v1/auth/register` PUBLIC → 201)
  - `.../auth/controller/AdminAccountController.java` (`POST /api/v1/auth/accounts` ADMIN + `@PreAuthorize("hasRole('ADMIN')")`; `GET /accounts?accountType&active&page&size`; `PUT /accounts/{id}/status`; `PUT /accounts/{id}/roles` – chỉ STAFF, sai role → 422 `ROLE_NOT_ALLOWED`)
- **Logic chung:** mọi response lỗi dùng `ErrorResponse` của common-lib; phân trang đúng `{content,page,size,totalElements,totalPages}` (mục 8); `STAFF` luôn kèm `ADMIN` (mục 8: endpoint staff cho phép thêm ADMIN – ở auth là các API ADMIN-only nên giữ nguyên).
- **Done:** test: register trùng username → 409; ADMIN tạo STAFF thiếu role → 400; CUSTOMER gọi `POST /accounts` → 403.

#### Task C5 – Đăng nhập / Refresh (rotation) / Logout / Me / Đổi mật khẩu (Table 7)
- **File tạo mới:**
  - `.../auth/dto/LoginRequest.java`, `LoginResponse.java` (`accessToken, refreshToken, tokenType:"Bearer", expiresIn:900, account:{id,username,accountType,roles[]}`)
  - `.../auth/dto/RefreshRequest.java`, `.../auth/service/AuthSessionService.java`
  - Các endpoint trong `AuthController`: `POST /login` (PUBLIC), `POST /refresh` (PUBLIC), `POST /logout` (AUTH, body `{refreshToken}` → 204), `GET /me` (AUTH), `PUT /password` (`{oldPassword,newPassword}` → 204, sai old → 400 `WRONG_OLD_PASSWORD`)
- **Logic bắt buộc (mục 7.1):**
  1. Login sai 5 lần liên tiếp → khóa 15 phút (`locked_until`), trả `423 ACCOUNT_LOCKED`; ADMIN khóa (`isActive=false`) → `403 ACCOUNT_DISABLED` + thu hồi mọi refresh token.
  2. Refresh rotation: cấp cặp mới + `revoked_at` token cũ; **dùng lại token đã thu hồi → thu hồi toàn bộ phiên của account** (chống replay).
  3. Đổi mật khẩu / khóa tài khoản → thu hồi mọi refresh token (`revoked_at=now`).
  4. `GET /me` đọc từ `HeaderUser` (Gateway đã gắn), không verify JWT lại.
- **Done:** test: login sai 5 lần → lần 6 bị 423; refresh 2 lần bằng cùng token cũ → lần 2 bị `401 REFRESH_TOKEN_INVALID` + mọi token khác cũng失效.

#### Task C6 – Outbox + phát `AccountRegistered` (mục 5.3, 7.1, Table 16)
- **File tạo mới:**
  - `.../auth/outbox/OutboxEventPublisher.java` (ghi dòng outbox **cùng giao dịch** tạo account – `@Transactional`)
  - `.../auth/outbox/OutboxRelay.java` (`@Scheduled(fixedDelay=1000)`, đọc tối đa 100 dòng `published_at IS NULL` order `created_at`, gửi Kafka topic `auth.events` key=`accountId`, thành công → set `published_at`; producer `acks=all, enable.idempotence=true`, value `StringSerializer` JSON envelope)
  - Payload `AccountRegistered {accountId, accountType, username, fullName, email, phone}` (Table 16), `eventType=AccountRegistered, version=1`
- **Logic:** **cấm** `kafkaTemplate.send` trực tiếp trong `AccountService`; consumer User Service idempotent nên gửi trùng chấp nhận được.
- **Done:** testcontainers Kafka: tạo account → có 1 dòng outbox → sau ≤2s topic nhận đúng envelope; kill Kafka rồi restart → dòng cũ được gửi lại.

---

### EPIC D – `api-gateway` (port 8080, mục 4.2 + 6.1)

#### Task D1 – Khung Gateway + định tuyến theo prefix (Table 2)
- **File tạo mới:**
  - `api-gateway/pom.xml` (`spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `jjwt-api/impl/jackson`, `spring-boot-starter-actuator`)
  - `.../gateway/GatewayApplication.java`
  - `api-gateway/src/main/resources/application.yml`
  - `api-gateway/Dockerfile`
- **File sửa:** root `pom.xml`, `infra/docker-compose.yml`.
- **Logic (`application.yml`):** `server.port=8080`; `spring.application.name=api-gateway`; routes `lb://auth-service` cho `/api/v1/auth/**`; khai báo sẵn 6 route còn lại trỏ `lb://user-notification-service, property-service, listing-booking-service, …` theo Table 2 (dù service chưa có, Gateway vẫn start được nhờ `lb` + Eureka); `spring.cloud.gateway.discovery.locator.enabled=false` (route tường minh).
- **Done:** Gateway đăng ký Eureka; `POST /api/v1/auth/register` đi qua Gateway tới auth-service.

#### Task D2 – `JwtAuthenticationFilter` + whitelist + header chuyển tiếp (mục 6.1)
- **File tạo mới:**
  - `.../gateway/filter/JwtAuthenticationFilter.java` (implements `GlobalFilter, Ordered`)
  - `.../gateway/config/GatewaySecurityConfig.java` (CORS cho web/app; chặn `/internal/**` → 404)
  - `.../gateway/config/CorrelationIdFilter.java` (sinh UUID nếu thiếu `X-Correlation-Id`)
  - `api-gateway/src/test/java/.../JwtAuthenticationFilterTest.java`
- **Logic theo đúng thứ tự mục 6.1:**
  1. Xóa mọi `X-User-Id, X-User-Type, X-User-Roles` client tự gửi (chống giả mạo).
  2. Whitelist cho qua không cần token: `POST /api/v1/auth/register, /login, /refresh`; `GET /api/v1/locations/**`, `GET /api/v1/buildings/nearby`, `GET /api/v1/listings`, `GET /api/v1/listings/{id}` (mục 4.2).
  3. Còn lại: thiếu/sai `Authorization: Bearer <token>` → `401 UNAUTHENTICATED`; verify HS256 bằng `JWT_SECRET`, `iss=rental-auth`, `exp`: hết hạn → `401 TOKEN_EXPIRED`.
  4. Hợp lệ → gắn `X-User-Id=sub, X-User-Type=typ, X-User-Roles=roles(join ",")`, giữ `X-Correlation-Id` rồi forward.
  5. Chặn mọi `/internal/**` từ ngoài → 404 (mục 4.2); Gateway **không** gom dữ liệu, không logic nghiệp vụ.
- **Done:** test: không token gọi `GET /me` → 401; token hết hạn → `TOKEN_EXPIRED`; client gửi `X-User-Id: 1` giả → bị xóa và thay bằng id thật; `/internal/staff/1/buildings` từ ngoài → 404.

#### Task D3 – CORS + lỗi chuẩn Gateway + log truy vết
- **File tạo mới/sửa:** `application.yml` (CORS `allowedOrigins` qua env `CORS_ALLOWED_ORIGINS`), `.../gateway/handler/GatewayErrorHandler.java` (mọi lỗi auth trả đúng `{code,message,status,traceId}` mục 8, `traceId=correlationId`).
- **Logic:** log mọi request có `correlationId`; timeout route mặc định; `actuator/health` public.
- **Done:** FE gọi preflight OPTIONS thành công; response lỗi từ Gateway giống hệt format common-lib.

---

### EPIC E – Tích hợp & nghiệm thu Giai đoạn 0 (Table 19)

#### Task E1 – Test end-to-end + Swagger + CI
- **File tạo mới:**
  - `auth-service/src/test/java/.../AuthFlowIT.java` (Testcontainers Postgres+Kafka: register → login → `GET /me` qua Gateway → refresh rotation → logout)
  - Mỗi service: `springdoc-openapi` bật Swagger UI; `actuator/health` có `correlationId` trong log.
  - `.github/workflows/ci.yml` (build + test mỗi PR – mục 12).
- **Tiêu chí xong Giai đoạn 0 (Table 19):** *Đăng ký, đăng nhập, gọi API qua Gateway với JWT, tạo hồ sơ tự động qua Kafka* (vế User Service do team bạn kia làm, phía bạn chỉ cần event `AccountRegistered` đã lên topic đúng envelope).
- **File sửa (nếu phát sinh event/API mới):** cập nhật `docs/` trước khi merge (mục 1 – nguyên tắc team).

---

## 3. Thứ tự làm (đề xuất, tránh chờ nhau)

```
0.1 parent pom → 0.2 compose/.env → A1 → A2 → A3 → A4 (common-lib xong, gồm FeignConfig)
→ B1 (discovery, song song được từ sau 0.2)


→ C1 → C2 → C3 → C4 → C5 → C6 (auth)
→ D1 → D2 → D3 (gateway, cần JWT_SECRET + common-lib error format)
→ E1 (E2E qua Gateway + Kafka)
```

- B1 độc lập sau 0.2, có thể làm song song với A.
- D2 phụ thuộc C3 (cùng `JWT_SECRET`, cùng `iss`, cùng claims `sub/typ/roles`).
- C6 (outbox) nên làm ngay sau C4 để mọi đường tạo account đều phát event.

## 4. Rủi ro & lưu ý kỹ thuật

1. **Secret lệch giữa auth và gateway** → toàn bộ 401. Phòng tránh: chỉ đọc từ env, có test ` JwtProviderTest` + `JwtAuthenticationFilterTest` dùng chung vector token.
2. **Quên xóa header giả mạo** → lỗ hổng auth. Phòng tránh: filter xóa header là dòng đầu tiên, có test giả mạo.
3. **Gọi `kafkaTemplate.send` trực tiếp** → vi phạm mục 5.3, mất event khi crash. Phòng tránh: review bắt buộc `OutboxEventPublisher` trong cùng `@Transactional`.
4. **Sửa migration đã merge** → vỡ DB team. Phòng tránh: chỉ thêm `V3__...` mới (mục 12).
5. **Thêm lib mới** → phải hỏi trước (AIRule §5). Trong plan này lib Feign (`openfeign, loadbalancer, resilience4j`) đã được bạn duyệt ở Q5 (Task A4); mọi lib ngoài danh sách này đều phải xin phép.

---

## 5. Checklist nghiệm thu nhanh (dán vào PR)

- [ ] `docker compose up` → Eureka (8761) thấy `auth-service`, `api-gateway`.
- [ ] `POST /api/v1/auth/register` qua `:8080` → 201; trùng username → 409 `USERNAME_EXISTS`.
- [ ] `POST /login` → nhận `accessToken (15')` + `refreshToken`; `GET /me` kèm Bearer → 200.
- [ ] Sai pass 5 lần → 423 `ACCOUNT_LOCKED` 15 phút.
- [ ] `POST /refresh` rotation: dùng lại token cũ → 401 + mọi phiên bị thu hồi.
- [ ] Topic `auth.events` có `AccountRegistered` đúng envelope (kiểm tra bằng Kafka UI :8090).
- [ ] Gọi `/internal/**` từ ngoài → 404; gửi `X-User-Id` giả → bị thay bằng id thật.
- [ ] Mọi lỗi trả đúng `{code,message,status,traceId,details?}`; không có secret nào trong git (`git grep JWT_SECRET` chỉ thấy `${...}`).
