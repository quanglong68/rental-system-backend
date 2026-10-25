package com.rental.contracttenant.controller;

import com.rental.contracttenant.dto.BillingContractData;
import com.rental.contracttenant.dto.RoomTenantCheckResponse;
import com.rental.contracttenant.service.ContractService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalContractController {

    private final ContractService contractService;

    @GetMapping("/contracts/billing-data")
    public List<BillingContractData> getBillingData(
            @RequestParam int month,
            @RequestParam int year) {
        return contractService.getBillingData(month, year);
    }

    @GetMapping("/rooms/{roomId}/tenants/{customerId}")
    public RoomTenantCheckResponse checkRoomTenant(
            @PathVariable Long roomId,
            @PathVariable Long customerId) {
        return contractService.checkRoomTenant(roomId, customerId);
    }
}
