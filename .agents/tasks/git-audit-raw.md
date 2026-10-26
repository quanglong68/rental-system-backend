# Git Audit Raw Output

## Backend Branch
```
feat/contract-tenant-service
```

## Backend git status --short
```
 M asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
 M auth-service/src/main/java/com/rental/auth/service/AccountService.java
 M auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
 M billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
 M contract-tenant-service/src/main/java/com/rental/contracttenant/ContractTenantServiceApplication.java
 M contract-tenant-service/src/main/java/com/rental/contracttenant/controller/ContractLifecycleController.java
 M contract-tenant-service/src/main/java/com/rental/contracttenant/dto/BillingContractData.java
 M contract-tenant-service/src/main/java/com/rental/contracttenant/entity/Contract.java
 M contract-tenant-service/src/main/java/com/rental/contracttenant/service/ContractService.java
 M contract-tenant-service/src/test/java/com/rental/contracttenant/controller/ContractLifecycleControllerWebTest.java
 M contract-tenant-service/src/test/java/com/rental/contracttenant/controller/InternalContractControllerWebTest.java
 M contract-tenant-service/src/test/java/com/rental/contracttenant/service/BuildingScopeTest.java
 M contract-tenant-service/src/test/java/com/rental/contracttenant/service/ContractServiceTest.java
 M docs/CONTRACT_TENANT_SERVICE_API_DEPENDENCIES.md
?? .agents/
?? contract-tenant-service/src/main/java/com/rental/contracttenant/client/CustomerClient.java
?? contract-tenant-service/src/main/java/com/rental/contracttenant/dto/CreateDraftManualRequest.java
?? contract-tenant-service/src/main/resources/db/migration/V8__nullable_rental_request_id.sql
?? user-notification-service/src/main/java/com/rental/user/controller/InternalCustomerController.java
```

## Backend git diff --name-only
```
asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
auth-service/src/main/java/com/rental/auth/service/AccountService.java
auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
contract-tenant-service/src/main/java/com/rental/contracttenant/ContractTenantServiceApplication.java
contract-tenant-service/src/main/java/com/rental/contracttenant/controller/ContractLifecycleController.java
contract-tenant-service/src/main/java/com/rental/contracttenant/dto/BillingContractData.java
contract-tenant-service/src/main/java/com/rental/contracttenant/entity/Contract.java
contract-tenant-service/src/main/java/com/rental/contracttenant/service/ContractService.java
contract-tenant-service/src/test/java/com/rental/contracttenant/controller/ContractLifecycleControllerWebTest.java
contract-tenant-service/src/test/java/com/rental/contracttenant/controller/InternalContractControllerWebTest.java
contract-tenant-service/src/test/java/com/rental/contracttenant/service/BuildingScopeTest.java
contract-tenant-service/src/test/java/com/rental/contracttenant/service/ContractServiceTest.java
docs/CONTRACT_TENANT_SERVICE_API_DEPENDENCIES.md
```

## Backend git diff --stat
```
 .../rental/asset/AssetControllerSecurityTest.java  |  21 ++-
 .../com/rental/auth/service/AccountService.java    |   6 +-
 .../rental/auth/service/AccountServiceTest.java    |  21 +++
 .../client/BillingDataResponse.java                |   2 +
 .../ContractTenantServiceApplication.java          |   2 +-
 .../controller/ContractLifecycleController.java    |   9 +
 .../contracttenant/dto/BillingContractData.java    |   1 +
 .../com/rental/contracttenant/entity/Contract.java |   3 +-
 .../contracttenant/service/ContractService.java    |  66 ++++++-
 .../ContractLifecycleControllerWebTest.java        | 204 +++++++++++++++++++++
 .../InternalContractControllerWebTest.java         |   3 +-
 .../contracttenant/service/BuildingScopeTest.java  |  16 ++
 .../service/ContractServiceTest.java               | 197 +++++++++++++++++++-
 docs/CONTRACT_TENANT_SERVICE_API_DEPENDENCIES.md   | 134 +++++++-------
 14 files changed, 609 insertions(+), 76 deletions(-)
```

## Backend Untracked Files
```
.agents/tasks/backend-verify.md
.agents/tasks/pre-submission-verify.md
contract-tenant-service/src/main/java/com/rental/contracttenant/client/CustomerClient.java
contract-tenant-service/src/main/java/com/rental/contracttenant/dto/CreateDraftManualRequest.java
contract-tenant-service/src/main/resources/db/migration/V8__nullable_rental_request_id.sql
user-notification-service/src/main/java/com/rental/user/controller/InternalCustomerController.java
```

## Backend git log (last 5)
```
3e6b179 (HEAD -> feat/contract-tenant-service) Merge pull request #22 from quanglong68/feat/asset-maintenance-service
8ac9202 Remove unused asset maintenance files
641b1d4 resovle merge from branch main
41ec05c Update(asset-maintenance-service): update structure of service
0ce5f77 (main) Merge pull request #20 from quanglong68/feat/billing-utility-service
```

## Frontend Branch
```
origin/feature/contract-maintenance-integration
```

## Frontend git log -1
```
3612677 (HEAD -> origin/feature/contract-maintenance-integration) feat: SALE manual draft contract creation UI
```

## Frontend git status --short
```
?? .agents/tasks/final-report.md
```

## Frontend git diff HEAD --name-only
```
(no output - no modified files)
```

## Frontend Untracked Files
```
.agents/tasks/final-report.md
```

## Out-of-Scope File Diffs

### asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
```diff
diff --git a/asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java b/asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
index 3cf751f..0d47d9f 100644
--- a/asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
+++ b/asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
@@ -235,8 +235,8 @@ class AssetControllerSecurityTest {
     }
     @Test
-    @DisplayName("SALE: Được danh sách report trong scope, ngoài scope bị cấm")
-    void sale_listsReportsInScope_outOfScopeForbidden() throws Exception {
+    @DisplayName("SALE: Được danh sách report trong scope, 403 ngoài scope; mutation vẫn 403")
+    void sale_listsReports_inScope_onlyAndCannotMutate() throws Exception {
         when(users.staffBuildings(301L)).thenReturn(new UserClient.BuildingResponse(301L, List.of(10L)));
         when(reports.findByBuildingIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                 .thenReturn(new PageImpl<>(List.of()));
@@ -248,6 +248,23 @@ class AssetControllerSecurityTest {
         mvc.perform(get("/api/v1/reports?buildingId=99")
                         .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                 .andExpect(status().isForbidden());
+
+        String approvePayload = """
+                {
+                    "staffId": 201,
+                    "priority": "HIGH",
+                    "scheduledAt": "2026-11-01T09:00:00Z"
+                }
+                """;
+        mvc.perform(put("/api/v1/reports/5/approve")
+                        .contentType(MediaType.APPLICATION_JSON).content(approvePayload)
+                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
+                .andExpect(status().isForbidden());
+
+        mvc.perform(put("/api/v1/reports/5/reject")
+                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không hợp lệ\"}")
+                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
+                .andExpect(status().isForbidden());
     }
     @Test
```

### auth-service/src/main/java/com/rental/auth/service/AccountService.java
```diff
diff --git a/auth-service/src/main/java/com/rental/auth/service/AccountService.java b/auth-service/src/main/java/com/rental/auth/service/AccountService.java
index 7fcd1fc..9f0d6ae 100644
--- a/auth-service/src/main/java/com/rental/auth/service/AccountService.java
+++ b/auth-service/src/main/java/com/rental/auth/service/AccountService.java
@@ -118,7 +118,11 @@ public class AccountService {
         return response;
     }
-    /** Doi role cho STAFF (ADMIN) -> 200. Account khong phai STAFF thi 422. */
+    /**
+     * Doi role cho STAFF (ADMIN) -> 200. Account khong phai STAFF thi 422.
+     * STAFF roles la cumulative: QUAN_LY + SALE hop le va duoc giu ca hai; khong tu dong
+     * them/bot role hoac suy ra quyen tu accountType.
+     */
     @Transactional
     public AccountResponse updateRoles(Long id, List<String> roleCodes) {
         Account account = accounts.findById(id)
```

### auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
```diff
diff --git a/auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java b/auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
index 91cfbac..df4306e 100644
--- a/auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
+++ b/auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
@@ -169,6 +169,27 @@ class AccountServiceTest {
                 .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_ALLOWED"));
     }
+    /**
+     * STAFF roles là cumulative, không loại trừ nhau. Account có cả SALE + QUAN_LY phải giữ
+     * nguyên cả hai trong DB/response; quyền ghi hợp đồng đến từ QUAN_LY thật, không phải
+     * do frontend tự suy ra từ role ưu tiên.
+     */
+    @Test
+    void updateRoles_saleVaQuanLy_giuNguyenCaHaiRole() {
+        Account staff = account(8L, "staff@mail.com", AccountType.STAFF);
+        when(accounts.findById(8L)).thenReturn(Optional.of(staff));
+        when(roles.existsByCode("SALE")).thenReturn(true);
+        when(roles.existsByCode("QUAN_LY")).thenReturn(true);
+        when(roles.findByCode("SALE")).thenReturn(Optional.of(role(4, "SALE")));
+        when(roles.findByCode("QUAN_LY")).thenReturn(Optional.of(role(2, "QUAN_LY")));
+
+        AccountResponse res = service.updateRoles(8L, List.of("SALE", "QUAN_LY"));
+
+        assertThat(res.getRoles()).containsExactly("SALE", "QUAN_LY");
+        verify(accountRoles).deleteByAccountId(8L);
+        verify(accountRoles, org.mockito.Mockito.times(2)).save(any(AccountRole.class));
+    }
+
     @Test
     void updateStatus_khongTonTai_404() {
         when(accounts.findById(404L)).thenReturn(Optional.empty());
```

### billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
```diff
diff --git a/billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java b/billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
index e286f86..3b971f6 100644
--- a/billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
+++ b/billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
@@ -1,5 +1,6 @@
 package com.rental.billingutilityservice.client;
+import com.fasterxml.jackson.annotation.JsonProperty;
 import lombok.Data;
 import java.math.BigDecimal;
@@ -28,6 +29,7 @@ public class BillingDataResponse {
         private Long customerId;
         private LocalDate joinedAt;
         private LocalDate leftAt;
+        @JsonProperty("isRepresentative")
         private Boolean representative;
     }
 }
```

## InternalCustomerController Status

### git status output
```
On branch feat/contract-tenant-service
Your branch is ahead of 'origin/origin/feat/contract-tenant-service' by 36 commits.
  (use "git push" to publish your local commits)
Untracked files:
  (use "git add <file>..." to include in what will be committed)
	user-notification-service/src/main/java/com/rental/user/controller/InternalCustomerController.java
nothing added to commit but untracked files present (use "git add" to track)
```

### git log output
```
(empty - file has no history)
```

### File content
```java
package com.rental.user.controller;

import com.rental.common.error.NotFoundException;
import com.rental.user.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for customer existence checks. Gateway blocks /internal/** from public access.
 * Feign call #8 (closed list): Contract → User, {@code GET /internal/customers/{id}/exists}.
 */
@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerRepository customers;

    @GetMapping("/{id}/exists")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkExists(@PathVariable Long id) {
        if (!customers.existsById(id)) {
            throw new NotFoundException("CUSTOMER_NOT_FOUND", "Khong tim thay khach hang " + id);
        }
    }
}
```

## File Categorization

### IN-SCOPE (allowed modifications)

**Backend: contract-tenant-service/ (modified)**
- contract-tenant-service/src/main/java/com/rental/contracttenant/ContractTenantServiceApplication.java
- contract-tenant-service/src/main/java/com/rental/contracttenant/controller/ContractLifecycleController.java
- contract-tenant-service/src/main/java/com/rental/contracttenant/dto/BillingContractData.java
- contract-tenant-service/src/main/java/com/rental/contracttenant/entity/Contract.java
- contract-tenant-service/src/main/java/com/rental/contracttenant/service/ContractService.java
- contract-tenant-service/src/test/java/com/rental/contracttenant/controller/ContractLifecycleControllerWebTest.java
- contract-tenant-service/src/test/java/com/rental/contracttenant/controller/InternalContractControllerWebTest.java
- contract-tenant-service/src/test/java/com/rental/contracttenant/service/BuildingScopeTest.java
- contract-tenant-service/src/test/java/com/rental/contracttenant/service/ContractServiceTest.java

**Backend: contract-tenant-service/ (untracked new files)**
- contract-tenant-service/src/main/java/com/rental/contracttenant/client/CustomerClient.java
- contract-tenant-service/src/main/java/com/rental/contracttenant/dto/CreateDraftManualRequest.java
- contract-tenant-service/src/main/resources/db/migration/V8__nullable_rental_request_id.sql

**Backend: documentation**
- docs/CONTRACT_TENANT_SERVICE_API_DEPENDENCIES.md

**Backend: task files (.agents/)**
- .agents/tasks/backend-verify.md
- .agents/tasks/pre-submission-verify.md

**Frontend: task files (.agents/)**
- .agents/tasks/final-report.md

### OUT-OF-SCOPE (violations)

**Backend: asset-maintenance-service/ (MODIFIED)**
- asset-maintenance-service/src/test/java/com/rental/asset/AssetControllerSecurityTest.java
  - Status: Modified (M)
  - Changes: Added mutation tests (approve/reject) to SALE role security test
  - Lines changed: +21 additions

**Backend: auth-service/ (MODIFIED)**
- auth-service/src/main/java/com/rental/auth/service/AccountService.java
  - Status: Modified (M)
  - Changes: Updated javadoc comment to clarify cumulative role behavior
  - Lines changed: +6 additions, -2 deletions

- auth-service/src/test/java/com/rental/auth/service/AccountServiceTest.java
  - Status: Modified (M)
  - Changes: Added new test case `updateRoles_saleVaQuanLy_giuNguyenCaHaiRole()`
  - Lines changed: +21 additions

**Backend: billing-utility-service/ (MODIFIED)**
- billing-utility-service/src/main/java/com/rental/billingutilityservice/client/BillingDataResponse.java
  - Status: Modified (M)
  - Changes: Added @JsonProperty("isRepresentative") annotation
  - Lines changed: +2 additions

**Backend: user-notification-service/ (NEW UNTRACKED FILE)**
- user-notification-service/src/main/java/com/rental/user/controller/InternalCustomerController.java
  - Status: Untracked (not yet added to git)
  - Type: New file creation
  - Purpose: Internal API endpoint for customer existence checks
  - Lines: 32 lines of new code

## Summary

### Total Files in Backend Repository
- **14 modified files** (staged changes)
- **6 untracked files** (not yet added)

### Violations Breakdown
- **4 modified out-of-scope files** in other services
- **1 untracked out-of-scope file** (new file creation in user-notification-service)
- **5 total violations**

### Violation Services
1. asset-maintenance-service (1 file)
2. auth-service (2 files)
3. billing-utility-service (1 file)
4. user-notification-service (1 file)

### Frontend Repository
- **No violations** (only task documentation file untracked)
- Clean state: no modified source files
