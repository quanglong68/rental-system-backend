package com.rental.listingbooking.controller;

import com.rental.common.security.HeaderUser;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.RejectRentalRequestDto;
import com.rental.listingbooking.dto.RentalRequestCreateDto;
import com.rental.listingbooking.dto.RentalRequestResponse;
import com.rental.listingbooking.service.IdentityFileStorage;
import com.rental.listingbooking.service.RentalRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/rental-requests")
@RequiredArgsConstructor
public class RentalRequestController {

    private final RentalRequestService requestService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RentalRequestResponse createRequest(
            @Valid @RequestBody RentalRequestCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return requestService.createRequest(dto, customerId);
    }

    @GetMapping({"/my", "/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<RentalRequestResponse> getMyRequests(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal HeaderUser user,
            @PageableDefault(page = 0, size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long customerId = Long.parseLong(user.getUserId());
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return requestService.getMyRequests(customerId, status, capped);
    }

    @GetMapping({"", "/manage"})
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public PageResponse<RentalRequestResponse> getManageRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long listingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean unread,
            @PageableDefault(page = 0, size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return requestService.getManageRequests(buildingId, listingId, status, unread, capped);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    public RentalRequestResponse getRequestById(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        return requestService.getRequestById(id, Long.parseLong(user.getUserId()), user.getUserType());
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        requestService.cancelRequest(id, Long.parseLong(user.getUserId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public RentalRequestResponse verifyRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        return requestService.verifyRequest(id, Long.parseLong(user.getUserId()));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    public RentalRequestResponse acceptRequest(@PathVariable Long id) {
        return requestService.acceptRequest(id);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public RentalRequestResponse rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectRentalRequestDto dto) {
        return requestService.rejectRequest(id, dto);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public RentalRequestResponse markRead(@PathVariable Long id) {
        return requestService.markRead(id);
    }

    @PostMapping("/identity-files")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RentalRequestService.IdentityUpload uploadIdentityFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("side") String side,
            @AuthenticationPrincipal HeaderUser user) {
        return requestService.uploadIdentityFile(file, side, Long.parseLong(user.getUserId()));
    }

    @GetMapping("/{id}/identity-files/{side}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'ADMIN')")
    public ResponseEntity<InputStreamResource> downloadIdentityFile(
            @PathVariable Long id,
            @PathVariable String side,
            @AuthenticationPrincipal HeaderUser user) {
        IdentityFileStorage.Download download = requestService.openIdentityFile(
                id, side, Long.parseLong(user.getUserId()), user.getUserType());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.filename()).build().toString())
                .body(new InputStreamResource(download.stream()));
    }
}
