package com.rental.contracttenant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rental.common.error.BusinessException;
import com.rental.common.error.GlobalExceptionHandler;
import com.rental.contracttenant.config.SecurityConfig;
import com.rental.contracttenant.dto.ContractResponse;
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
}
