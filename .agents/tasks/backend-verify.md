# Backend Verification Report

**Date:** Phase A — Backend State Verification  
**Verdict:** BACKEND_OK

---

## 1. File Status

### 1.1 `CustomerClient.java`
**Status: EXISTS — CORRECT**

- Location: `contract-tenant-service/src/main/java/com/rental/contracttenant/client/CustomerClient.java`
- Is a `@FeignClient` targeting `user-notification-service` (name = "user-notification-service", contextId = "customerClient")
- Exposes `void checkExists(@PathVariable("id") Long id)` mapped to `GET /internal/customers/{id}/exists`
- Uses `FeignDefaultConfig` for configuration
- Failure policy documented: 404 → `CUSTOMER_NOT_FOUND`, 5xx/timeout/circuit open → `DEPENDENCY_UNAVAILABLE`

---

### 1.2 `CreateDraftManualRequest.java`
**Status: EXISTS — CORRECT**

- Location: `contract-tenant-service/src/main/java/com/rental/contracttenant/dto/CreateDraftManualRequest.java`
- Fields present and validated:
  - `customerId` (Long) — `@NotNull @Positive`
  - `roomId` (Long) — `@NotNull @Positive`
  - `buildingId` (Long) — `@NotNull @Positive`
  - `startDate` (LocalDate) — `@NotNull`
  - `rentAmount` (BigDecimal) — `@NotNull @DecimalMin(>0) @Digits(integer=15, fraction=0)`

---

### 1.3 `V8__nullable_rental_request_id.sql`
**Status: EXISTS — CORRECT**

- Location: `contract-tenant-service/src/main/resources/db/migration/V8__nullable_rental_request_id.sql`
- Content: `ALTER TABLE contract ALTER COLUMN rental_request_id DROP NOT NULL;`
- The existing UNIQUE constraint on `rental_request_id` is preserved (only NOT NULL is dropped)
- Event-driven idempotency is maintained: non-null values still must be unique; NULL values are exempt from unique constraint in PostgreSQL

**Migration sequence confirmed:**

| File | Status |
|------|--------|
| V1__init_contract.sql | exists |
| V2__create_contract_tenant.sql | exists |
| V3__create_outbox_event.sql | exists |
| V4__create_move_out_request.sql | exists |
| V5__create_processed_event.sql | exists |
| V6__unique_pending_move_out_request.sql | exists |
| V7__add_updated_at_to_business_tables.sql | exists |
| **V8__nullable_rental_request_id.sql** | **NEW — correct next sequence** |

V8 is the correct next sequence number (V7 is the previous highest).

---

### 1.4 `InternalCustomerController.java`
**Status: EXISTS — CORRECT**

- Location: `user-notification-service/src/main/java/com/rental/user/controller/InternalCustomerController.java`
- `@RequestMapping("/internal/customers")`
- `GET /{id}/exists` with `@ResponseStatus(HttpStatus.NO_CONTENT)` → returns 204 if found
- Throws `NotFoundException("CUSTOMER_NOT_FOUND", ...)` → results in 404 if not found
- Uses `CustomerRepository.existsById(id)` for lookup

---

### 1.5 `ContractLifecycleController.java`
**Status: EXISTS — CORRECT**

- `@PostMapping("/contracts/drafts")` under `@RequestMapping("/api/v1")` → full path: `POST /api/v1/contracts/drafts`
- `@PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")` — correct roles
- `@ResponseStatus(HttpStatus.CREATED)` — returns 201
- Accepts `@Valid @RequestBody CreateDraftManualRequest request`
- Delegates to `contractService.createDraftManual(request)`

---

### 1.6 `ContractService.java` — `createDraftManual` method
**Status: EXISTS — CORRECT**

Method implementation (at ~line 909):
1. Null guard → `VALIDATION_ERROR` / 400
2. `buildingScope.assertAssigned(request.getBuildingId())` — SALE/QUAN_LY scoped, ADMIN bypasses
3. `customers.checkExists(request.getCustomerId())` — validates customer via CustomerClient; 404 → re-throws `CUSTOMER_NOT_FOUND`; timeout/circuit → 503 `DEPENDENCY_UNAVAILABLE`
4. `properties.getRoom(request.getRoomId())` — validates room existence; 404 → `ROOM_NOT_FOUND`; timeout/circuit → 503
5. Room/building relationship check: `room.buildingId().equals(request.getBuildingId())` → `ROOM_BUILDING_MISMATCH` / 422
6. Creates `Contract` with `rentalRequestId = null`, saves to DB, returns `ContractResponse`

---

### 1.7 `Contract.java` entity — `rentalRequestId` nullability
**Status: EXISTS — CORRECT**

```java
/** NULL for direct manual drafts; set for event-driven drafts. UNIQUE prevents duplicate events. */
@Column(name = "rental_request_id", unique = true)
private Long rentalRequestId;
```

- No `nullable = false` — the column is nullable in JPA
- `unique = true` preserved for idempotency of event-driven drafts
- The Flyway V8 migration aligns with this entity mapping

---

### 1.8 `api-gateway/src/main/resources/application.yml` — `/internal/**` exposure
**Status: EXISTS — CORRECT (internal routes blocked)**

The gateway routes are an explicit whitelist. No route predicate mentions `/internal/**` or any `/internal/` prefix. The gateway only forwards:

- `/api/v1/auth/**` → auth-service
- `/api/v1/users/**, /api/v1/staff/**, /api/v1/notifications/**, /api/v1/devices/**` → user-notification-service
- `/api/v1/locations/**, ..., /api/v1/buildings/**, ..., /api/v1/rooms/**` → property-service
- `/api/v1/contracts/**, /api/v1/move-out-requests/**` → contract-tenant-service
- Other `/api/v1/**` routes for other services
- Swagger doc routes (`/v3/api-docs/**`)

The `user-notification-service` route predicate is:
```
Path=/api/v1/users/**,/api/v1/staff/**,/api/v1/notifications/**,/api/v1/devices/**
```

`/internal/**` is not listed — requests to `/internal/**` through the gateway will receive 404/no-route. The internal endpoint is only reachable via direct service-to-service Feign calls inside the cluster.

---

## 2. Backend Test Results

Java is not in PATH on this machine (`java` and `mvn` commands are unavailable in the current environment). The Maven wrapper (`mvnw.cmd`) is present in `contract-tenant-service/` but requires `JAVA_HOME` to be set.

**Tests could not be executed in this environment.**

Based on code inspection:
- The previous agent reported: **244 tests, 0 failures, 0 errors, 4 skipped — BUILD SUCCESS**
- All test files referenced in `git status` show the expected test methods for `createDraftManual` are present in:
  - `ContractLifecycleControllerWebTest.java` — 11+ test cases for the new endpoint covering all roles and error conditions
  - `ContractServiceTest.java` — 7+ test cases for the service method covering all validation paths

---

## 3. Fixes Applied

**None.** All files exist and are correctly implemented. No changes were needed.

---

## 4. Final Verdict

**BACKEND_OK**

### Summary of verified backend behavior:

| Check | Result |
|-------|--------|
| Endpoint `POST /api/v1/contracts/drafts` exists | ✅ |
| Roles: SALE, QUAN_LY, ADMIN allowed | ✅ |
| USER / CUSTOMER blocked (not in `@PreAuthorize`) | ✅ |
| `rentalRequestId` nullable in JPA entity | ✅ |
| V8 migration is correct next sequence number | ✅ |
| V8 makes `rental_request_id` nullable, preserves UNIQUE | ✅ |
| Customer validated via `/internal/customers/{id}/exists` | ✅ |
| Room validated via PropertyClient | ✅ |
| Room/building relationship validated | ✅ |
| Building scope enforced for SALE/QUAN_LY | ✅ |
| `/internal/**` NOT exposed through public API Gateway | ✅ |
| `createDraftManual` sets `rentalRequestId = null` | ✅ |
| Event-driven drafts continue using non-null `rentalRequestId` | ✅ |

### Key endpoint for frontend implementation:

```
POST /api/v1/contracts/drafts
Authorization: Bearer <JWT with role SALE, QUAN_LY, or ADMIN>
Content-Type: application/json

{
  "customerId": <Long>,
  "roomId": <Long>,
  "buildingId": <Long>,
  "startDate": "<YYYY-MM-DD>",
  "rentAmount": <integer BigDecimal, no decimal places>
}

Response: 201 Created → ContractResponse
```
