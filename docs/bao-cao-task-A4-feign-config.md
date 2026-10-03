# Báo cáo hoàn thành – Task A4: `FeignConfig` dùng chung

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task A4) + đặc tả mục 5.2 (Feign: timeout, retry, CB, interceptor, ErrorDecoder) + Q5 đã duyệt lib + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, `mvn verify` xanh, 27/27 test pass (12 cũ + 15 mới).

## 1. File tạo mới (8 file) + 2 file sửa

| File | Nội dung |
|---|---|
| `.../common/feign/FeignHeaderInterceptor.java` | `RequestInterceptor` copy 4 header từ request đang xử lý; scheduler (không request) gửi `X-User-Type: SYSTEM` + tự sinh correlationId; không ghi đè header có sẵn |
| `.../common/feign/FeignErrorDecoder.java` | 404 → `NotFoundException`; 4xx → `BusinessException` **giữ nguyên code bên kia** (parse `ErrorResponse` chung trong body, fallback `UPSTREAM_ERROR` giữ đúng status); 5xx → `DependencyUnavailableException` (503) |
| `.../common/feign/GetOnlyRetryer.java` | Chỉ retry GET tối đa 1 lần (backoff 100ms, giữ interrupt flag); POST/PUT/DELETE ném ngay; `clone()` cho mỗi request |
| `.../common/feign/FeignDefaultConfig.java` | `@Configuration` mẫu: timeout 2000/3000ms, retryer, decoder, CB mặc định (10 calls – 50% – mở 10s); javadoc hướng dẫn `@FeignClient(name=..., configuration=...)` |
| 4 test mới | `FeignHeaderInterceptorTest` (3), `FeignErrorDecoderTest` (4), `GetOnlyRetryerTest` (3), `FeignDefaultConfigTest` (3) |
| `GlobalExceptionHandler.java` (sửa) | Thêm 2 handler (xem §2 — decoder không bắt được 2 ca này) |
| `GlobalExceptionHandlerTest.java` (sửa) | Thêm 2 test timeout + mạch mở → 503 |
| `common-lib/pom.xml` (sửa) | Thêm 3 starter Cloud (openfeign, loadbalancer, circuitbreaker-resilience4j), version từ BOM — đúng Q5, không lib lạ |

## 2. Quyết định kỹ thuật (nghĩ kỹ hệ lụy, AIRule §1)

- **Decoder không đủ cho "timeout, mạch mở → 503":** `ErrorDecoder.decode()` chỉ chạy khi đã nhận HTTP response; timeout (`RetryableException`) và mạch mở (`CallNotPermittedException`) ném thẳng khỏi proxy Feign → bổ sung 2 handler trong `GlobalExceptionHandler` (mọi `FeignException` còn sót → 503, vì decoder đã chuyển hết 4xx).
- **Retry an toàn:** `GetOnlyRetryer` kiểm tra `httpMethod == GET` nên POST/PUT/DELETE không bao giờ retry (tránh double-charge оплате / tạo trùng).
- **Không leak CB config:** `defaultCircuitBreakerConfig()` package-private để test assert đúng 10 – 50% – 10s mà không public API thừa.

## 3. Kết quả verify

```
mvn -f common-lib/pom.xml verify  →  BUILD SUCCESS
Tests run: 27, Failures: 0, Errors: 0
```

## 4. Lỗi gặp và cách sửa

1. **API feign 13/resilience4j 2 khác tài liệu cũ:** `RetryableException` có 2 ctor (`Long`/`Date`) gây ambiguous với `null` → cast `(Date) null`; `CircuitBreakerConfig` 2.x bỏ `getWaitDurationInOpenState()` → assert qua `getWaitIntervalFunctionInOpenState().apply(1)` (trả millis `10_000L`).
2. Xóa 2 dòng thừa trong test (assert/placeholder sót khi soạn).

## 5. common-lib HOÀN TẤT (Epic A xong)

Đủ 4 nhóm: hằng + envelope (A1), lỗi chuẩn (A2), filter (A3), feign (A4). Task tiếp theo là **Task B1** (discovery-server Eureka 8761). Bạn duyệt thì tôi làm tiếp.
