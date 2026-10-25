package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.AttachServiceRequest;
import com.rental.billingutilityservice.dto.response.ContractServiceResponse;
import com.rental.billingutilityservice.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dich vu gan hop dong (docs muc 9.5): QUAN_LY gan/go, STAFF xem. */
@RestController
@RequestMapping("/api/v1/contract-services")
@RequiredArgsConstructor
public class ContractServiceController {

    private final CatalogService catalog;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public ContractServiceResponse attach(@Valid @RequestBody AttachServiceRequest request) {
        return catalog.attach(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public List<ContractServiceResponse> list(@RequestParam Long contractId) {
        return catalog.listByContract(contractId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public void detach(@PathVariable Long id) {
        catalog.detach(id);
    }
}
