package com.rental.contracttenant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rental.common.error.BusinessException;
import com.rental.common.error.GlobalExceptionHandler;
import com.rental.contracttenant.config.SecurityConfig;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.ContractTenantResponse;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.service.ContractService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContractLifecycleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ContractLifecycleControllerWebTest {

    private static final String JSON_PAYLOAD =
            "{\"startDate\":\"2026-12-01\",\"endDate\":\"2027-11-30\",\"rentAmount\":4000000,"
                    + "\"depositAmount\":1500000}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContractService contractService;

    @Test
    void quanLyUpdateDraft_returns200WithUpdatedTerms() throws Exception {
        when(contractService.updateDraftDetails(eq(9L), any())).thenReturn(response());

        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rentAmount").value(4000000))
                .andExpect(jsonPath("$.depositAmount").value(1500000))
                .andExpect(jsonPath("$.startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.endDate").value("2027-11-30"));
    }

    @Test
    void missingRequiredField_returns400ValidationError() throws Exception {
        // startDate is @NotNull on the request DTO; bean validation runs before the service.
        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2027-11-30\",\"rentAmount\":4000000}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void negativeRentAmount_returns400ValidationError() throws Exception {
        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-12-01\",\"rentAmount\":-1000}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateOnMissingContract_returns404() throws Exception {
        when(contractService.updateDraftDetails(eq(9L), any())).thenThrow(new BusinessException(
                "CONTRACT_NOT_FOUND", "Khong tim thay hop dong 9", HttpStatus.NOT_FOUND));

        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateOnNonDraftContract_returns422InvalidStatus() throws Exception {
        when(contractService.updateDraftDetails(eq(9L), any())).thenThrow(new BusinessException(
                "INVALID_STATUS", "Chi sua hop dong o trang thai DRAFT", HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void noGatewayHeaders_returns401() throws Exception {
        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void customerRole_returns403Forbidden() throws Exception {
        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static ContractResponse response() {
        return new ContractResponse(9L, 41L, 12L, 3L, 77L, ContractStatus.DRAFT, LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 11, 30), new BigDecimal("4000000"), new BigDecimal("1500000"), null, null);
    }

    // ---- sign ----

    @Test
    void quanLySign_returns200WithActiveContract() throws Exception {
        when(contractService.sign(9L)).thenReturn(activeResponse());

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customerId").value(77))
                .andExpect(jsonPath("$.startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.endDate").value("2027-11-30"));
    }

    @Test
    void sign_onMissingContract_returns404() throws Exception {
        when(contractService.sign(9L)).thenThrow(new BusinessException(
                "CONTRACT_NOT_FOUND", "Khong tim thay hop dong 9", HttpStatus.NOT_FOUND));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    @Test
    void sign_onNonDraftContract_returns422InvalidStatus() throws Exception {
        when(contractService.sign(9L)).thenThrow(new BusinessException(
                "INVALID_STATUS", "Chi ky hop dong o trang thai DRAFT", HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void sign_onIncompleteContract_returns422ContractIncomplete() throws Exception {
        when(contractService.sign(9L)).thenThrow(new BusinessException(
                "CONTRACT_INCOMPLETE", "Hop dong thieu ngay ket thuc", HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_INCOMPLETE"));
    }

    @Test
    void sign_whenRoomAlreadyContracted_returns409() throws Exception {
        when(contractService.sign(9L)).thenThrow(new BusinessException(
                "ROOM_ALREADY_CONTRACTED", "Phong da co hop dong ACTIVE trung khoang thoi gian",
                HttpStatus.CONFLICT));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_CONTRACTED"));
    }

    @Test
    void sign_customerAlreadyInRoom_returns409() throws Exception {
        when(contractService.sign(9L)).thenThrow(new BusinessException(
                "CUSTOMER_ALREADY_IN_ROOM", "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_IN_ROOM"));
    }

    @Test
    void sign_withoutGatewayHeaders_returns401() throws Exception {
        mvc.perform(post("/api/v1/contracts/9/sign"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void sign_asCustomer_returns403Forbidden() throws Exception {
        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static ContractResponse activeResponse() {
        return new ContractResponse(9L, 41L, 12L, 3L, 77L, ContractStatus.ACTIVE, LocalDate.of(2026, 12, 1),
                LocalDate.of(2027, 11, 30), new BigDecimal("4000000"), new BigDecimal("1500000"), null, null);
    }

    // ---- Task 5B: add tenant ----

    private static final String ADD_TENANT_PAYLOAD = "{\"customerId\":88,\"joinedAt\":\"2026-12-01\"}";

    @Test
    void quanLyAddTenant_returns201WithCreatedTenant() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenReturn(tenantResponse());

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(45))
                .andExpect(jsonPath("$.contractId").value(9))
                .andExpect(jsonPath("$.customerId").value(88))
                .andExpect(jsonPath("$.joinedAt").value("2026-12-01"))
                .andExpect(jsonPath("$.leftAt").doesNotExist())
                .andExpect(jsonPath("$.isRepresentative").value(false));
    }

    @Test
    void adminAddTenant_returns201() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenReturn(tenantResponse());

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "900").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isCreated());
    }

    @Test
    void customerAddTenant_returns403() throws Exception {
        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "600").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void addTenant_withoutGatewayHeaders_returns401() throws Exception {
        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void addTenant_missingField_returns400ValidationError() throws Exception {
        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":88}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addTenant_onMissingContract_returns404() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenThrow(
                new BusinessException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong 9", HttpStatus.NOT_FOUND));

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    @Test
    void addTenant_toNonActiveContract_returns422InvalidStatus() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenThrow(new BusinessException(
                "INVALID_STATUS", "Chi them nguoi o vao hop dong ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void addTenant_overCapacity_returns422RoomCapacityExceeded() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenThrow(new BusinessException(
                "ROOM_CAPACITY_EXCEEDED", "Phong da du so nguoi toi da", HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROOM_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void addTenant_customerAlreadyInRoom_returns409() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenThrow(new BusinessException(
                "CUSTOMER_ALREADY_IN_ROOM", "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT));

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_IN_ROOM"));
    }

    @Test
    void addTenant_propertyUnavailable_returns503() throws Exception {
        when(contractService.addTenant(eq(9L), any())).thenThrow(new BusinessException(
                "DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc suc chua phong",
                HttpStatus.SERVICE_UNAVAILABLE));

        mvc.perform(post("/api/v1/contracts/9/tenants")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    private static ContractTenantResponse tenantResponse() {
        return new ContractTenantResponse(45L, 9L, 88L, LocalDate.of(2026, 12, 1), null, false, null);
    }

    // ---- Task 5A: change representative ----

    @Test
    void quanLyChangeRepresentative_returns204() throws Exception {
        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminChangeRepresentative_returns204() throws Exception {
        // Spec 8: "Endpoint của staff luôn cho phép thêm ADMIN".
        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "900").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void customerChangeRepresentative_returns403() throws Exception {
        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "600").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void changeRepresentative_withoutGatewayHeaders_returns401() throws Exception {
        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void changeRepresentativeOnDepartedTenant_returns422TenantNotActive() throws Exception {
        doThrow(new BusinessException("TENANT_NOT_ACTIVE", "Nguoi o da roi khoi hop dong",
                HttpStatus.UNPROCESSABLE_ENTITY))
                .when(contractService).changeRepresentative(9L, 32L);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void changeRepresentativeOnMissingContract_returns404() throws Exception {
        doThrow(new BusinessException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong 9", HttpStatus.NOT_FOUND))
                .when(contractService).changeRepresentative(9L, 32L);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    @Test
    void changeRepresentativeOnTenantOfAnotherContract_returns404TenantNotFound() throws Exception {
        doThrow(new BusinessException("TENANT_NOT_FOUND", "Khong tim thay nguoi o 32", HttpStatus.NOT_FOUND))
                .when(contractService).changeRepresentative(9L, 32L);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void adminCanUpdateDraftAndSign() throws Exception {
        // Regression for the role widening: spec 8 allows ADMIN on every staff endpoint, and the two
        // earlier contract endpoints were QUAN_LY-only before Task 5A.
        when(contractService.updateDraftDetails(eq(9L), any())).thenReturn(response());
        when(contractService.sign(9L)).thenReturn(activeResponse());

        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "900").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "900").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ---- Building Scope Web-layer mappings (Task 5D) ----

    @Test
    void updateDraft_unassignedBuilding_returns403() throws Exception {
        when(contractService.updateDraftDetails(eq(9L), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Staff chua duoc phan cong"));

        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void updateDraft_buildingScopeDependencyFails_returns503() throws Exception {
        when(contractService.updateDraftDetails(eq(9L), any()))
                .thenThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "user down", HttpStatus.SERVICE_UNAVAILABLE));

        mvc.perform(put("/api/v1/contracts/9")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void sign_unassignedBuilding_returns403() throws Exception {
        when(contractService.sign(9L))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Staff chua duoc phan cong"));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void sign_buildingScopeDependencyFails_returns503() throws Exception {
        when(contractService.sign(9L))
                .thenThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "user down", HttpStatus.SERVICE_UNAVAILABLE));

        mvc.perform(post("/api/v1/contracts/9/sign")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void changeRepresentative_unassignedBuilding_returns403() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("Staff chua duoc phan cong"))
                .when(contractService).changeRepresentative(9L, 32L);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void changeRepresentative_buildingScopeDependencyFails_returns503() throws Exception {
        doThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "user down", HttpStatus.SERVICE_UNAVAILABLE))
                .when(contractService).changeRepresentative(9L, 32L);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/representative")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    // ---- Task 5C: leave tenant web tests ----

    private static final String LEAVE_TENANT_PAYLOAD = "{\"leftAt\":\"2026-12-01\"}";

    @Test
    void quanLyLeaveTenant_assignedBuilding_returns200() throws Exception {
        ContractTenantResponse resp = new ContractTenantResponse(32L, 9L, 88L,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 1), false, null);
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenReturn(resp);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.contractId").value(9))
                .andExpect(jsonPath("$.customerId").value(88))
                .andExpect(jsonPath("$.leftAt").value("2026-12-01"));
    }

    @Test
    void adminLeaveTenant_returns200() throws Exception {
        ContractTenantResponse resp = new ContractTenantResponse(32L, 9L, 88L,
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 1), false, null);
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenReturn(resp);

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "900").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void customerLeaveTenant_returns403() throws Exception {
        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "600").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void leaveTenant_withoutGatewayHeaders_returns401() throws Exception {
        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void leaveTenant_missingLeftAt_returns400ValidationError() throws Exception {
        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void leaveTenant_missingContract_returns404() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenThrow(
                new BusinessException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong 9", HttpStatus.NOT_FOUND));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    @Test
    void leaveTenant_tenantNotBelongToContract_returns404() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenThrow(
                new BusinessException("TENANT_NOT_FOUND", "Khong tim thay nguoi o 32", HttpStatus.NOT_FOUND));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void leaveTenant_cannotRemoveRepresentative_returns422() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenThrow(new BusinessException(
                "CANNOT_REMOVE_REPRESENTATIVE", "Khong the roi hop dong khi dang la nguoi dai dien",
                HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_REPRESENTATIVE"));
    }

    @Test
    void leaveTenant_lastTenant_returns422() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenThrow(new BusinessException(
                "LAST_TENANT", "Khong the roi vi la nguoi o cuoi cung trong hop dong ACTIVE",
                HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LAST_TENANT"));
    }

    @Test
    void leaveTenant_invalidDate_returns422() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenThrow(new BusinessException(
                "INVALID_DATE", "Ngay roi di truoc ngay bat dau o",
                HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE"));
    }

    @Test
    void leaveTenant_tenantNotActive_returns422() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any())).thenThrow(new BusinessException(
                "TENANT_NOT_ACTIVE", "Nguoi o da roi khoi hop dong",
                HttpStatus.UNPROCESSABLE_ENTITY));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_ACTIVE"));
    }

    @Test
    void leaveTenant_unassignedBuilding_returns403() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Staff chua duoc phan cong"));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void leaveTenant_buildingScopeDependencyFails_returns503() throws Exception {
        when(contractService.leaveTenant(eq(9L), eq(32L), any()))
                .thenThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "user down", HttpStatus.SERVICE_UNAVAILABLE));

        mvc.perform(put("/api/v1/contracts/9/tenants/32/leave")
                        .contentType(MediaType.APPLICATION_JSON).content(LEAVE_TENANT_PAYLOAD)
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void renewContract_active_returns200() throws Exception {
        when(contractService.renewContract(eq(9L), any())).thenReturn(response());

        mvc.perform(post("/api/v1/contracts/9/renew")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newEndDate\":\"2027-12-31\"}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk());
    }

    @Test
    void terminateContract_active_returns200() throws Exception {
        when(contractService.terminateContract(eq(9L), any())).thenReturn(response());

        mvc.perform(post("/api/v1/contracts/9/terminate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"terminationDate\":\"2027-05-01\",\"reason\":\"Tra nha som\"}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk());
    }

    @Test
    void createMoveOutRequest_customer_returns201() throws Exception {
        com.rental.contracttenant.dto.MoveOutRequestResponse resp = new com.rental.contracttenant.dto.MoveOutRequestResponse(
                1L, 9L, 600L, LocalDate.now(), LocalDate.now().plusDays(10), "Chuyen cong tac",
                com.rental.contracttenant.entity.MoveOutStatus.PENDING, null
        );
        when(contractService.createMoveOutRequest(any())).thenReturn(resp);

        mvc.perform(post("/api/v1/move-out-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":9,\"expectedMoveOutDate\":\"" + LocalDate.now().plusDays(10) + "\",\"reason\":\"Chuyen cong tac\"}")
                        .header("X-User-Id", "600").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /** Spec 9.4: {@code reason?} is optional, so omitting it must not trip bean validation. */
    @Test
    void createMoveOutRequest_withoutReason_returns201() throws Exception {
        com.rental.contracttenant.dto.MoveOutRequestResponse resp = new com.rental.contracttenant.dto.MoveOutRequestResponse(
                1L, 9L, 600L, LocalDate.now(), LocalDate.now().plusDays(10), "",
                com.rental.contracttenant.entity.MoveOutStatus.PENDING, null
        );
        when(contractService.createMoveOutRequest(any())).thenReturn(resp);

        mvc.perform(post("/api/v1/move-out-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractId\":9,\"expectedMoveOutDate\":\"" + LocalDate.now().plusDays(10) + "\"}")
                        .header("X-User-Id", "600").header("X-User-Type", "CUSTOMER")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void approveMoveOutRequest_quanLy_returns200() throws Exception {
        com.rental.contracttenant.dto.MoveOutRequestResponse resp = new com.rental.contracttenant.dto.MoveOutRequestResponse(
                1L, 9L, 600L, LocalDate.now(), LocalDate.now().plusDays(10), "Chuyen cong tac",
                com.rental.contracttenant.entity.MoveOutStatus.APPROVED, null
        );
        when(contractService.approveMoveOutRequest(1L)).thenReturn(resp);

        mvc.perform(put("/api/v1/move-out-requests/1/approve")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectMoveOutRequest_quanLy_returns200() throws Exception {
        com.rental.contracttenant.dto.MoveOutRequestResponse resp = new com.rental.contracttenant.dto.MoveOutRequestResponse(
                1L, 9L, 600L, LocalDate.now(), LocalDate.now().plusDays(10), "Chuyen cong tac",
                com.rental.contracttenant.entity.MoveOutStatus.REJECTED, null
        );
        when(contractService.rejectMoveOutRequest(eq(1L), any())).thenReturn(resp);

        mvc.perform(put("/api/v1/move-out-requests/1/reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong hop le\"}")
                        .header("X-User-Id", "501").header("X-User-Type", "STAFF")
                        .header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
