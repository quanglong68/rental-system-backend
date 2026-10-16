package com.rental.contracttenant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@WebMvcTest(ContractController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ContractControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContractService contractService;

    @Test
    void internalCreateDraft_validPayload_returns201() throws Exception {
        when(contractService.createDraft(any())).thenReturn(response());

        mvc.perform(post("/internal/contracts/drafts").contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rentalRequestId").value(41))
                .andExpect(jsonPath("$.rentAmount").value(3500000));
    }

    @Test
    void internalCreateDraft_conflictingReplay_returns409() throws Exception {
        when(contractService.createDraft(any())).thenThrow(
                new BusinessException("RENTAL_REQUEST_CONFLICT", "Rental request da gan hop dong khac du lieu",
                        HttpStatus.CONFLICT));

        mvc.perform(post("/internal/contracts/drafts").contentType(MediaType.APPLICATION_JSON).content(payload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENTAL_REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void internalCreateDraft_missingOrZeroField_returns400ValidationError() throws Exception {
        mvc.perform(post("/internal/contracts/drafts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rentalRequestId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void internalCreateDraft_negativePrice_returns400ValidationError() throws Exception {
        String invalidPrice = "{\"rentalRequestId\":41,\"listingId\":17,\"roomId\":12,"
                + "\"buildingId\":3,\"customerId\":77,\"moveInDate\":\"2026-11-01\",\"price\":-1000}";
        mvc.perform(post("/internal/contracts/drafts").contentType(MediaType.APPLICATION_JSON).content(invalidPrice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void publicContractPath_withoutHeaders_returns401() throws Exception {
        mvc.perform(post("/api/v1/contracts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static String payload() {
        return "{\"rentalRequestId\":41,\"listingId\":17,\"roomId\":12,"
                + "\"buildingId\":3,\"customerId\":77,\"moveInDate\":\"2026-11-01\",\"price\":3500000}";
    }

    private static ContractResponse response() {
        return new ContractResponse(9L, 41L, 12L, 3L, 77L, ContractStatus.DRAFT, LocalDate.of(2026, 11, 1),
                null, new BigDecimal("3500000"), null, null, null);
    }
}
