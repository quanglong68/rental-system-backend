# Báo cáo hoàn thành – Task A3: `HeaderAuthenticationFilter`

- **Nguồn:** `docs/plan-infra-auth-team.md` (Task A3) + đặc tả mục 6.2 (service phân quyền, không verify JWT) + `AIRule.md`.
- **Ngày:** 03/10/2026. **Trạng thái:** XONG, `mvn verify` xanh, 12/12 test pass (8 cũ + 4 mới).

## 1. File tạo mới (3 file) + 1 file sửa

| File | Nội dung |
|---|---|
| `.../common/security/HeaderUser.java` | Principal bất biến (`@Value`): `userId` (claim sub), `userType`, `roles` (đã tách dấu phẩy) |
| `.../common/security/HeaderAuthenticationFilter.java` | `OncePerRequestFilter` đọc 3 header Gateway, xem §2 |
| `.../common/security/HeaderAuthenticationFilterTest.java` | 4 test đúng plan (§3) |
| `common-lib/pom.xml` (sửa) | Thêm `slf4j-api` (MDC log correlationId, docs mục 2; version từ BOM, không lib lạ) |

## 2. Logic đã triển khai (đối chiếu mục 6.2)

1. Đọc `X-User-Id` / `X-User-Type` / `X-User-Roles`; đủ header → `UsernamePasswordAuthenticationToken(principal=HeaderUser, authorities=ROLE_<role>)` vào `SecurityContext` (roles trim, bỏ rỗng, thiếu roles vẫn authenticated với quyền rỗng).
2. Thiếu `X-User-Id` hoặc `X-User-Type` (request không qua Gateway) → 401 `{code: UNAUTHENTICATED, traceId}` đúng format `ErrorResponse`, tái dùng `ObjectMapper` + class lỗi Task A2 (DRY, AIRule §2).
3. Bỏ qua `/internal/**` và `/actuator/**` (không kiểm quyền người dùng) — qua `isSkipped()` dạng `protected` để service override mở rộng khi cần.
4. MDC `correlationId` (+ `userId` khi đã auth) cho log truy vết, `finally` xóa để không rò sang request khác; `traceId` thiếu thì sinh UUID.
5. Không thêm `spring-security-web` (chỉ dùng `security-core` đã có) nên không cần xin phép lib mới (AIRule §5).

## 3. Kết quả verify

```
mvn -f common-lib/pom.xml verify  →  BUILD SUCCESS
Tests run: 12, Failures: 0, Errors: 0
```

4 case filter: đủ header → auth đúng + `ROLE_QUAN_LY/ROLE_SALE` + MDC trong chain; thiếu header → 401 `UNAUTHENTICATED` + chain không chạy; `/internal/**` không header vẫn qua, không auth; thiếu roles → authenticated quyền rỗng.

## 4. Lỗi gặp và cách sửa

1. `package org.slf4j does not exist`: `spring-core` chỉ mang `spring-jcl`, không có `slf4j-api` → khai thêm dep trực tiếp.
2. Test trộn raw value với `any()` (Mockito `InvalidUseOfMatchersException`) → sửa `verify(chain).doFilter(request, response)` tường minh.

## 5. Task tiếp theo (chưa làm, chờ bạn duyệt)

- **Task A4:** `FeignConfig` dùng chung (`FeignHeaderInterceptor`, `FeignErrorDecoder`, `FeignDefaultConfig` + test) — bạn đã duyệt lib Feign ở Q5.
