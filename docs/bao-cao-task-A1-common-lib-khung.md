# Báo cáo hoàn thành – Task A1: Khung `common-lib` + hằng số dùng chung

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task A1) + đặc tả mục 1, 5.3, 6 (Table 6) + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, đã verify build xanh.

## 1. File tạo mới (4 file)

| File | Nội dung |
|---|---|
| `common-lib/pom.xml` | Module con của parent pom (`com.rental:common-lib:1.0.0-SNAPSHOT`), không chứa logic nghiệp vụ |
| `.../common/constant/Headers.java` | Hằng `X-User-Id`, `X-User-Type`, `X-User-Roles`, `X-Correlation-Id` (đúng Table 6) |
| `.../common/constant/UserTypes.java` | Hằng `CUSTOMER`, `STAFF`, `ADMIN`, `SYSTEM` (SYSTEM cho scheduler gọi Feign nội bộ) |
| `.../common/event/EventEnvelope.java` | Envelope generic `<T>` đúng mẫu mục 5.3 |

## 2. Logic đã triển khai

1. **`pom.xml`:** deps đúng plan A1 — `lombok` (provided), `spring-security-core` (cho filter Task A3), `spring-web` (cho filter + exception handler Task A2/A3), `jakarta.validation-api`, `jackson-databind` + `jackson-datatype-jsr310` (serialize envelope), `spring-boot-starter-test` (test scope, chuẩn bị cho test Task A2/A3). Version ăn theo BOM cha, không khai báo version rời.
2. **`Headers`:** final class + private constructor, javadoc ghi rõ Gateway gắn sau verify JWT và Gateway xóa header giả mạo do client gửi (mục 6.1).
3. **`UserTypes`:** hằng String (so sánh trực tiếp với giá trị header, tránh lỗi convert enum).
4. **`EventEnvelope<T>`:** đủ 6 field `eventId (UUID), eventType, version, occurredAt (Instant, UTC ISO-8601), correlationId, payload`; `@JsonInclude(NON_NULL)`; factory `of(eventType, payload, correlationId)` tự sinh `eventId`, `version=1`, `occurredAt=now`. Javadoc nhắc quy tắc event bất biến/tương thích ngược (mục 1).
5. **Bảo mật (AIRule §5):** không secret, không lib ngoài đặc tả.

## 3. Kết quả verify

```
mvn -f common-lib/pom.xml verify  →  BUILD SUCCESS (10.8s)
- Compiling 3 source files with javac [debug release 21]
- Building jar: target/common-lib-1.0.0-SNAPSHOT.jar
- No tests to run (test viết ở Task A2/A3 theo plan)
```

## 4. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task A2:** lỗi chuẩn + handler toàn cục (`ErrorCode`, `ErrorResponse`, `BusinessException`, `NotFoundException`, `DependencyUnavailableException`, `GlobalExceptionHandler`, `FieldViolation` + unit test).
