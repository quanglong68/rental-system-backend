# Báo cáo hoàn thành – Task C2: Migration + Entity auth_db

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task C2) + đặc tả mục 1, 5.3, 7.3, 9, 12 + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, boot thật migrate + validate xanh.

## 1. File tạo mới (16 file) + 1 file sửa

| File | Nội dung |
|---|---|
| `db/migration/V1__init_auth.sql` | 6 bảng đúng mục 7.3 + `processed_event` (mục 5.3/9): `IDENTITY` PK, `username`/`token_hash` unique, `account_role` PK kép + FK nội DB, `payload JSONB`, `TIMESTAMPTZ`, index `refresh_token(account_id)` + partial index outbox chưa publish |
| `db/migration/V2__seed_roles.sql` | 5 role id cố định 1–5, `ON CONFLICT DO NOTHING` (seed lại an toàn; không sửa migration đã merge) |
| `entity/` (7) | `AccountType` enum (STRING), `Account` (BCrypt hash, active/lock count), `Role` (id tự gán), `AccountRole` + `AccountRoleId` (`@IdClass`), `RefreshToken` (hash SHA-256, revokedAt), `OutboxEvent` (UUID tự sinh, JSONB qua `@JdbcTypeCode`), `ProcessedEvent` |
| `repository/` (6) | `Account` (find/exists by username), `Role` (by code), `AccountRole` (by account, delete by account), `RefreshToken` (by hash/account), `OutboxEvent` (`findTop100...PublishedAtIsNull`, đón Task C6), `ProcessedEvent` |
| `auth-service/pom.xml` (sửa) | Thêm `lombok` (provided) — processor path ở parent không đủ, compile classpath module phải khai |

## 2. Quyết định kỹ thuật

- `payload JSONB` ánh xạ bằng Hibernate 6 native `@JdbcTypeCode(SqlTypes.JSON)` trên `String` — đúng spec "payload json", không thêm lib (AIRule §5).
- `TIMESTAMPTZ` cho mọi mốc thời gian (UTC, mục 1); `@CreationTimestamp/@UpdateTimestamp` thay vì auditing config.
- Không viết `@DataJpaTest`/Testcontainers ở task này: boot thật với `ddl-auto=validate` đã chứng minh mapping entity↔bảng (fail là sập app), test IT để dành Epic E.

## 3. Kết quả verify (chạy thật với postgres compose)

```
mvn package                       → BUILD SUCCESS
boot nối DB thật: /health UP (Flyway migrate trước, validate sau)
pg_tables@auth_db                 → account, account_role, flyway_schema_history,
                                    outbox_event, processed_event, refresh_token, role (đủ)
role                              → 5 dòng ADMIN/QUAN_LY/KY_THUAT/SALE/CUSTOMER
flyway_schema_history             → V1+V2 success
```

## 4. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task C3:** JWT + BCrypt (`JwtProvider` claims sub/typ/roles/iss/iat/exp/jti, `RefreshTokenService` SHA-256, `SecurityConfig` + `JwtProviderTest`).
