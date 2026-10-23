package com.rental.contracttenant.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rental.common.error.GlobalExceptionHandler;
import com.rental.contracttenant.config.SecurityConfig;
import com.rental.contracttenant.dto.BillingContractData;
import com.rental.contracttenant.dto.RoomTenantCheckResponse;
import com.rental.contracttenant.service.ContractService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalContractController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InternalContractControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContractService contractService;

    @Test
    void getBillingData_returns200() throws Exception {
        BillingContractData.BillingTenantData tenantData = new BillingContractData.BillingTenantData(
                101L, LocalDate.of(2026, 10, 1), null, true
        );
        BillingContractData contractData = new BillingContractData(
                1L, 10L, 100L, 101L, "ACTIVE",
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1),
                new BigDecimal("5000000"), List.of(tenantData)
        );

        when(contractService.getBillingData(10, 2026)).thenReturn(List.of(contractData));

        mvc.perform(get("/internal/contracts/billing-data?month=10&year=2026")
                        .header("X-User-Id", "999").header("X-User-Type", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractId").value(1))
                .andExpect(jsonPath("$[0].rentAmount").value(5000000))
                .andExpect(jsonPath("$[0].tenants[0].customerId").value(101))
                .andExpect(jsonPath("$[0].tenants[0].isRepresentative").value(true));
    }

    @Test
    void checkRoomTenant_returns200() throws Exception {
        when(contractService.checkRoomTenant(10L, 101L))
                .thenReturn(new RoomTenantCheckResponse(true, 1L, 100L));

        mvc.perform(get("/internal/rooms/10/tenants/101")
                        .header("X-User-Id", "999").header("X-User-Type", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isTenant").value(true))
                .andExpect(jsonPath("$.contractId").value(1))
                .andExpect(jsonPath("$.buildingId").value(100));
    }
}
