package com.rental.contracttenant.controller;

import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public contract API surface (spec 9.4 API contract, via Gateway prefix
 * {@code /api/v1/contracts}). Header {@code X-User-*} from the Gateway becomes
 * {@code ROLE_QUAN_LY} authority, so {@code @PreAuthorize} here compares against it.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContractLifecycleController {

    private final ContractService contractService;

    /**
     * QUAN_LY repairs the negotiable terms of one DRAFT contract (spec 9.4 "Chốt giá thuê").
     * Errors per spec row: 200 Contract; 422 INVALID_STATUS (only DRAFT); 400 VALIDATION_ERROR;
     * 404 CONTRACT_NOT_FOUND. Date ordering of an optional endDate against startDate is validated
     * in the service as 422 INVALID_DATE (code borrowed from the spec's renew/terminate rows; the
     * spec's PUT row names no code for it — documented as a resolved-by-us choice in the DTO).
     */
    @PutMapping("/contracts/{id}")
    @PreAuthorize("hasRole('QUAN_LY')")
    public ContractResponse updateDraft(@PathVariable Long id,
            @Valid @RequestBody UpdateDraftContractRequest request) {
        return contractService.updateDraftDetails(id, request);
    }

    /**
     * QUAN_LY signs a DRAFT contract (spec 9.4 "Ký hợp đồng"). No request body: everything signed is
     * already on the contract. Errors per spec row: 200 Contract (ACTIVE); 404 CONTRACT_NOT_FOUND;
     * 422 CONTRACT_INCOMPLETE (missing/blank term or rent); 422 INVALID_STATUS (not DRAFT);
     * 409 ROOM_ALREADY_CONTRACTED; 409 CUSTOMER_ALREADY_IN_ROOM (code reused from the tenant rule,
     * which the spec's sign row leaves unnamed); 422 INVALID_DATE for an inverted term.
     *
     * <p>{@code POST} per the spec, so a retry after a timeout is the client's choice; the second
     * call is rejected by the DRAFT-only rule rather than duplicating the tenant or the event.
     */
    @PostMapping("/contracts/{id}/sign")
    @PreAuthorize("hasRole('QUAN_LY')")
    public ContractResponse sign(@PathVariable Long id) {
        return contractService.sign(id);
    }
}
