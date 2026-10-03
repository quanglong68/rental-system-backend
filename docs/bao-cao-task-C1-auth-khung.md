# Báo cáo hoàn thành – Task C1: Khung auth-service

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task C1) + đặc tả mục 4.1, 4.3, 5.3, 7, 12 + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, chạy thật với infra Docker nghiệm thu đủ tiêu chí Done.

## 1. File tạo mới (4 file) + 2 file sửa

| File | Nội dung |
|---|---|
| `auth-service/pom.xml` | web, validation, data-jpa, actuator, eureka-client, flyway-core + flyway-database-postgresql, postgres (runtime), security-crypto (BCrypt, chưa chain), jjwt api/impl/jackson, spring-kafka, mapstruct, common-lib, springdoc-openapi, starter-test + kafka-test + testcontainers (testcontainers/postgresql/kafka) |
| `.../auth/AuthServiceApplication.java` | `@SpringBootApplication` + `@EnableScheduling` (đón sẵn OutboxRelay `@Scheduled` Task C6) |
| `auth-service/src/main/resources/application.yml` | Port 8081, `auth-service`, datasource `auth_db` (env `AUTH_DB_*`), JPA `ddl-auto=validate` + `open-in-view=false` + UTC, flyway `db/migration`, kafka producer `acks=all` + `idempotence=true` (mục 5.3), eureka `${EUREKA_URL}` + `prefer-ip-address`, `jwt.*` (secret/issuer/access 900s) |
| `auth-service/Dockerfile` | Multi-stage root-context, `-pl auth-service -am`, EXPOSE 8081, UTC |
| `infra/docker-compose.yml` (sửa) | auth-service chuyển root-context build (như discovery) |
| `pom.xml` root (sửa) | `mvn -N install` để module build độc lập (xem §3) |

## 2. Kết quả verify (chạy thật)

```
docker compose up postgres kafka:
  7 DB (auth_db…asset_db) + postgis@There property_db + topic auth.events (3 partition, RF=1)
  → init script Task 0.2 chạy thật lần đầu, đúng hết
java -jar auth-service.jar (nối infra thật):
  /actuator/health      → UP
  /eureka/apps          → AUTH-SERVICE status UP
  /v3/api-docs          → openapi 3.0.1
  auth_db               → bảng flyway_schema_history đã có (sẵn đón migration C2)
```

## 3. Ba lệch nhỏ so với plan (đều có lý do, ghi rõ để review)

1. **Thêm `eureka-client`:** plan C1 liệt kê thiếu nhưng Done yêu cầu "đăng ký vào Eureka" (mục 4.1) → bắt buộc có.
2. **Chỉ `security-crypto`, chưa `starter-security`:** tránh default login-chain chặn `/actuator/health` khi chưa có `SecurityConfig` (Task C3 sẽ thêm chain + dep khi đó).
3. **`mvn -N install` root pom:** build `-f auth-service/pom.xml` độc lập cần parent + common-lib trong `.m2` (Dockerfile không ảnh hưởng vì dùng `-am` trong reactor). Lưu ý: reactor root chỉ build được khi đủ 4 module (api-gateway ở Task D1); trước đó dùng build từng module + `install`.

## 4. Vấn đề môi trường phát hiện (quan trọng, không phải lỗi code)

- **Host port 5432 đã bị `sam-postgres` (dự án khác) chiếm** → JDBC `localhost:5432` vào nhầm DB và báo sai password (đúng spec: rental-postgres không publish port). Đã verify bằng proxy tạm `alpine/socat` 5433→postgres và xóa sau test.
- **Cách chạy local sau này:** dừng `sam-postgres` khi làm việc, hoặc thêm publish port riêng (override file, không sửa compose chung). Chi tiết lệnh chạy đầy đủ sẽ chốt ở Epic E.

## 5. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task C2:** Flyway migration + Entity (schema mục 7.3: `account, role, account_role, refresh_token, outbox_event, processed_event` + seed 5 role `V2__`).
