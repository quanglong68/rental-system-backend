package com.rental.contracttenant.controller;

import com.rental.common.dto.PageResponse;
import com.rental.contracttenant.dto.AddTenantRequest;
import com.rental.contracttenant.dto.ContractDetailResponse;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.ContractTenantResponse;
import com.rental.contracttenant.dto.CreateDraftManualRequest;
import com.rental.contracttenant.dto.CreateMoveOutRequest;
import com.rental.contracttenant.dto.DeclineSignatureRequest;
import com.rental.contracttenant.dto.MoveOutRequestResponse;
import com.rental.contracttenant.dto.RejectMoveOutRequest;
import com.rental.contracttenant.dto.RejectRenewalRequest;
import com.rental.contracttenant.dto.RenewContractRequest;
import com.rental.contracttenant.dto.RenewalRequestResponse;
import com.rental.contracttenant.dto.TenantRenewalRequest;
import com.rental.contracttenant.dto.SettleMoveOutRefundRequest;
import com.rental.contracttenant.dto.SignContractRequest;
import com.rental.contracttenant.dto.TenantLeaveRequest;
import com.rental.contracttenant.dto.TerminateContractRequest;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.entity.RenewalRequestStatus;
import com.rental.contracttenant.service.ContractDocumentStorage;
import com.rental.contracttenant.service.ContractService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContractLifecycleController {

    private final ContractService contractService;

    @GetMapping("/contracts")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public PageResponse<ContractResponse> searchContracts(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) ContractStatus status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return PageResponse.of(contractService.searchContracts(buildingId, roomId, status, capped));
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

    /** Streams the latest contract PDF through a permission-checked endpoint; MinIO stays private. */
    @GetMapping("/contracts/{id}/document")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'ADMIN')")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean addendum) {
        ContractDocumentStorage.Download document = contractService.openDocument(id, addendum);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.filename())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.size())
                .body(new InputStreamResource(document.stream()));
    }

    @PostMapping(path = "/contracts/{id}/signature-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'ADMIN')")
    public Map<String, String> uploadSignatureImage(@PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return Map.of("key", contractService.uploadSignatureImage(id, file));
    }

    @PostMapping("/contracts/{id}/signatures")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'ADMIN')")
    public ContractResponse signContract(@PathVariable Long id,
            @Valid @RequestBody SignContractRequest request,
            HttpServletRequest httpRequest) {
        return contractService.signContract(id, request, new ContractService.SignatureContext(
                clientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/contracts/{id}/decline-signature")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ContractResponse declineSignature(@PathVariable Long id,
            @Valid @RequestBody(required = false) DeclineSignatureRequest request) {
        return contractService.declineSignature(id, request);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/contracts/{id}/renew")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse renewContract(@PathVariable Long id,
            @Valid @RequestBody RenewContractRequest request) {
        return contractService.renewContract(id, request);
    }

    /** Spec 13.5: sends a renewal offer (AWAITING_TENANT + PDF + 15-day deadline) to the tenant. */
    @PostMapping("/contracts/{id}/renewal")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse offerRenewal(@PathVariable Long id,
            @Valid @RequestBody RenewContractRequest request) {
        return contractService.offerRenewal(id, request);
    }

    /** Tenant requests contract renewal (6 or 12 months) when within 30 days of end date. */
    @PostMapping("/contracts/{id}/renewal-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RenewalRequestResponse requestRenewal(@PathVariable Long id,
            @Valid @RequestBody TenantRenewalRequest request) {
        return contractService.requestRenewal(id, request);
    }

    /** CUSTOMER: renewal requests they sent, newest first. */
    @GetMapping("/renewal-requests/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<RenewalRequestResponse> getMyRenewalRequests() {
        return contractService.findMyRenewalRequests();
    }

    /** CUSTOMER: cancel their own request while it is still PENDING. */
    @PutMapping("/renewal-requests/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RenewalRequestResponse cancelRenewalRequest(@PathVariable Long id) {
        return contractService.cancelRenewalRequest(id);
    }

    /** QUAN_LY/ADMIN: renewal requests of the assigned buildings. */
    @GetMapping("/renewal-requests")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public PageResponse<RenewalRequestResponse> searchRenewalRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) RenewalRequestStatus status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return PageResponse.of(contractService.searchRenewalRequests(buildingId, status, capped));
    }

    @PutMapping("/renewal-requests/{id}/approve")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public RenewalRequestResponse approveRenewalRequest(@PathVariable Long id) {
        return contractService.approveRenewalRequest(id);
    }

    @PutMapping("/renewal-requests/{id}/reject")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public RenewalRequestResponse rejectRenewalRequest(@PathVariable Long id,
            @Valid @RequestBody RejectRenewalRequest request) {
        return contractService.rejectRenewalRequest(id, request);
    }

    /** "Soạn hợp đồng": builds the editable renewal draft from an approved request. */
    @PostMapping("/renewal-requests/{id}/draft")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse createRenewalDraft(@PathVariable Long id) {
        return contractService.createRenewalDraftFromRequest(id);
    }

    /** "Gửi khách ký": opens the tenant e-signature step for a complete renewal draft. */
    @PostMapping("/contracts/{id}/renewal/send")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse sendRenewalDraft(@PathVariable Long id) {
        return contractService.sendRenewalDraft(id);
    }

    /** "Gửi khách ký" for any complete DRAFT that has not opened e-signature yet. */
    @PostMapping("/contracts/{id}/send")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public ContractResponse sendDraftForSignature(@PathVariable Long id) {
        return contractService.sendDraftForSignature(id);
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
    public PageResponse<MoveOutRequestResponse> searchMoveOutRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) MoveOutStatus status,
            @RequestParam(defaultValue = "false") boolean unread,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return PageResponse.of(contractService.searchMoveOutRequests(buildingId, status, unread, capped));
    }

    @PutMapping("/move-out-requests/{id}/read")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public MoveOutRequestResponse markMoveOutRead(@PathVariable Long id) {
        return contractService.markMoveOutRead(id);
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

    @PostMapping(value = "/move-out-requests/{id}/refund-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public Map<String, String> uploadMoveOutRefundReceipt(@PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return Map.of("key", contractService.uploadMoveOutRefundReceipt(id, file));
    }

    @PutMapping("/move-out-requests/{id}/refund")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public MoveOutRequestResponse settleMoveOutRefund(@PathVariable Long id,
            @RequestBody SettleMoveOutRefundRequest request) {
        return contractService.settleMoveOutRefund(id, request);
    }

    @GetMapping("/move-out-requests/{id}/refund-receipt")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadRefundReceipt(@PathVariable Long id) {
        ContractDocumentStorage.Download download = contractService.openMoveOutRefundReceipt(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.filename()).build().toString())
                .body(new org.springframework.core.io.InputStreamResource(download.stream()));
    }
}
