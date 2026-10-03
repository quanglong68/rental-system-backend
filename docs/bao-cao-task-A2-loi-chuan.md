# Báo cáo hoàn thành – Task A2: Lỗi chuẩn + handler toàn cục

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task A2) + đặc tả mục 8 (format lỗi), Table 7 (mã lỗi auth), Table 8 (map HTTP) + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, `mvn verify` xanh, 8/8 test pass.

## 1. File tạo mới (7 file) + 1 file sửa

| File | Nội dung |
|---|---|
| `.../common/error/ErrorCode.java` | Enum 14 mã dùng chung (Table 8 + Table 7): `VALIDATION_ERROR, WRONG_OLD_PASSWORD, UNAUTHENTICATED, TOKEN_EXPIRED, INVALID_CREDENTIALS, REFRESH_TOKEN_INVALID, FORBIDDEN, ACCOUNT_DISABLED, NOT_FOUND, ACCOUNT_LOCKED (423), USERNAME_EXISTS (409), ROLE_NOT_ALLOWED (422), DEPENDENCY_UNAVAILABLE (503), INTERNAL_ERROR` — mỗi mã gắn sẵn `HttpStatus` |
| `.../common/error/FieldViolation.java` | Record `(field, message)` cho `details[]` |
| `.../common/error/ErrorResponse.java` | Đúng format mục 8: `{code, message, status, traceId, details}` (`NON_NULL`, không bao `data`) |
| `.../common/error/BusinessException.java` | Nhận cả `ErrorCode` lẫn `(String code, message, HttpStatus)` tự do — service có mã riêng (vd `ROOM_CAPACITY_EXCEEDED`) không cần sửa lib |
| `.../common/error/NotFoundException.java` | Mặc định 404, cho phép code riêng (vd `PRICE_NOT_FOUND`) |
| `.../common/error/DependencyUnavailableException.java` | Mặc định 503 `DEPENDENCY_UNAVAILABLE`, có ctor kèm `cause` (mục 5.2, không nuốt lỗi) |
| `.../common/error/GlobalExceptionHandler.java` | `@RestControllerAdvice`, xem §2 |
| `.../common/error/GlobalExceptionHandlerTest.java` | 8 unit test, xem §3 |
| `common-lib/pom.xml` (sửa) | Thêm `jakarta.servlet-api` scope `provided` (xem §4) |

## 2. Logic handler (đối chiếu Table 8)

- `BusinessException` → giữ nguyên code + status của bên throw (Feign giữ code bên kia, mục 5.2).
- `MethodArgumentNotValidException` / `ConstraintViolationException` / thiếu-sai param / body JSON hỏng → 400 `VALIDATION_ERROR` + `details` từng field.
- Spring Security `AccessDeniedException` → 403 `FORBIDDEN`; `AuthenticationException` → 401 `UNAUTHENTICATED`.
- `Exception` → 500 `INTERNAL_ERROR` với message chung chung, không lộ nội bộ.
- `traceId` = header `X-Correlation-Id` (Gateway chuyển vào); thiếu context thì sinh UUID, không bao giờ để response thiếu `traceId`.
- Service kích hoạt bằng `@Import(GlobalExceptionHandler.class)` (ghi trong javadoc; không dùng auto-config magic).

## 3. Kết quả verify

```
mvn -f common-lib/pom.xml verify  →  BUILD SUCCESS
Tests run: 8, Failures: 0, Errors: 0 (GlobalExceptionHandlerTest)
```

8 case: custom code giữ 404 + traceId từ header; mã service riêng giữ 422; 503 dependency; `@Valid` → 400 + chi tiết field; sai kiểu param → 400; 403; 500 giấu message gốc; thiếu request context vẫn có traceId.

## 4. Lỗi build gặp và cách sửa (quan trọng, team cần biết)

- **Hiện tượng:** lần build đầu báo ~15 lỗi, gồm 2 lỗi thật `cannot access jakarta.servlet.*` (do `ServletRequestAttributes` cần Servlet API mà `spring-web` không mang theo) **kèm** hàng loạt lỗi giả `cannot find symbol getHttpStatus()/builder()/getCode()` của Lombok.
- **Chẩn đoán (không đoán mò):** kiểm tra effective-pom (processorpath đúng), `javac` thủ công + Lombok (chạy tốt, cả với `--release 21`), kiểm tra jar `.m2` (nguyên vẹn) → Lombok không có vấn đề; lỗi Lombok là **cascade giả** khi javac mất cây kế thừa do thiếu class.
- **Đã sửa:** thêm `jakarta.servlet:jakarta.servlet-api` scope `provided` (version từ BOM Boot; runtime Tomcat của service tự có nên lib không đóng gói theo) → toàn bộ lỗi biến mất, build xanh. Bài học: trong lib thuần (không phải Boot app), dùng type nào của `spring-web` chạm tới servlet thì phải khai `provided` rõ ràng.

## 5. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task A3:** `HeaderAuthenticationFilter` + `HeaderUser` + test (xương sống phân quyền, mục 6.2).
