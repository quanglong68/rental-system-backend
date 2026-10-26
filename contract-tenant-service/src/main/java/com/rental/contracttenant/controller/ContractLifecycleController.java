package com.rental.contracttenant.controller;

import com.rental.contracttenant.dto.AddTenantRequest;
import com.rental.contracttenant.dto.ContractDetailResponse;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.ContractTenantResponse;
import com.rental.contracttenant.dto.CreateDraftManualRequest;
import com.rental.contracttenant.dto.CreateMoveOutRequest;
import com.rental.contracttenant.dto.MoveOutRequestResponse;
import com.rental.contracttenant.dto.RejectMoveOutRequest;
import com.rental.contracttenant.dto.RenewContractRequest;
import com.rental.contracttenant.dto.TenantLeaveRequest;
import com.rental.contracttenant.dto.TerminateContractRequest;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.service.ContractService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContractLifecycleController {

    private final ContractService contractService;

    @GetMapping("/contracts")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public Page<ContractResponse> searchContracts(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) ContractStatus status,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return contractService.searchContracts(buildingId, roomId, status, capped);
    }

    /** Direct DRAFT creation for SALE/QUAN_LY/ADMIN. Does not require rental request. */
    @PostMapping("/contracts/drafts")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse createDraftManual(@Valid @RequestBody CreateDraftManualRequest request) {
        return contractService.createDraftManual(request);
    }

    @GetMapping("/contracts/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ContractResponse> getMyContracts() {
        return contractService.findMyContracts();
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'ADMIN')")
    public ContractDetailResponse getContractById(@PathVariable Long id) {
        return contractService.getContractById(id);
    }

    @PutMapping("/contracts/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse updateDraft(@PathVariable Long id,
            @Valid @RequestBody UpdateDraftContractRequest request) {
        return contractService.updateDraftDetails(id, request);
    }

    @PostMapping("/contracts/{id}/sign")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse sign(@PathVariable Long id) {
        return contractService.sign(id);
    }

    @PostMapping("/contracts/{id}/renew")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse renewContract(@PathVariable Long id,
            @Valid @RequestBody RenewContractRequest request) {
        return contractService.renewContract(id, request);
    }

    @PostMapping("/contracts/{id}/terminate")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse terminateContract(@PathVariable Long id,
            @Valid @RequestBody TerminateContractRequest request) {
        return contractService.terminateContract(id, request);
    }

    @PostMapping("/contracts/{id}/tenants")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractTenantResponse addTenant(@PathVariable Long id,
            @Valid @RequestBody AddTenantRequest request) {
        return contractService.addTenant(id, request);
    }

    @PutMapping("/contracts/{id}/tenants/{tenantId}/representative")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRepresentative(@PathVariable Long id, @PathVariable Long tenantId) {
        contractService.changeRepresentative(id, tenantId);
    }

    @PutMapping("/contracts/{id}/tenants/{tenantId}/leave")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractTenantResponse leaveTenant(@PathVariable Long id, @PathVariable Long tenantId,
            @Valid @RequestBody TenantLeaveRequest request) {
        return contractService.leaveTenant(id, tenantId, request);
    }

    @PostMapping("/move-out-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MoveOutRequestResponse createMoveOutRequest(@Valid @RequestBody CreateMoveOutRequest request) {
        return contractService.createMoveOutRequest(request);
    }

    @GetMapping("/move-out-requests")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public Page<MoveOutRequestResponse> searchMoveOutRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) MoveOutStatus status,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return contractService.searchMoveOutRequests(buildingId, status, capped);
    }

    @GetMapping("/move-out-requests/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<MoveOutRequestResponse> getMyMoveOutRequests() {
        return contractService.findMyMoveOutRequests();
    }

    @PutMapping("/move-out-requests/{id}/approve")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public MoveOutRequestResponse approveMoveOutRequest(@PathVariable Long id) {
        return contractService.approveMoveOutRequest(id);
    }

    @PutMapping("/move-out-requests/{id}/reject")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public MoveOutRequestResponse rejectMoveOutRequest(@PathVariable Long id,
            @RequestBody(required = false) RejectMoveOutRequest request) {
        return contractService.rejectMoveOutRequest(id, request);
    }
}
