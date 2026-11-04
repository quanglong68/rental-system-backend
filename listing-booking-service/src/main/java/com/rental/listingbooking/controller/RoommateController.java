package com.rental.listingbooking.controller;

import com.rental.common.security.HeaderUser;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.RoommateApplicationCreateDto;
import com.rental.listingbooking.dto.RoommateApplicationResponse;
import com.rental.listingbooking.dto.RoommatePostCreateDto;
import com.rental.listingbooking.dto.RoommatePostResponse;
import com.rental.listingbooking.service.RoommateService;
import com.rental.listingbooking.service.IdentityFileStorage;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoommateController {

    private final RoommateService roommateService;

    @PostMapping("/api/v1/roommate-posts")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoommatePostResponse createPost(
            @Valid @RequestBody RoommatePostCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.createPost(dto, customerId);
    }

    @GetMapping("/api/v1/roommate-posts")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RoommatePostResponse> searchPosts(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) BigDecimal maxBudget,
            @RequestParam(required = false) String keyword,
            @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return roommateService.searchPosts(buildingId, maxBudget, keyword, capped);
    }

    @GetMapping({"/api/v1/roommate-posts/my", "/api/v1/roommate-posts/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<RoommatePostResponse> getMyPosts(@AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.getMyPosts(customerId);
    }

    @GetMapping("/api/v1/roommate-posts/{id}")
    @PreAuthorize("isAuthenticated()")
    public RoommatePostResponse getPostById(@PathVariable Long id) {
        return roommateService.getPostById(id);
    }

    @PutMapping("/api/v1/roommate-posts/{id}/close")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> closePost(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        roommateService.closePost(id, Long.parseLong(user.getUserId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/api/v1/roommate-posts/{id}/apply", "/api/v1/roommate-posts/{id}/applications"})
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoommateApplicationResponse applyToPost(
            @PathVariable Long id,
            @Valid @RequestBody RoommateApplicationCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.applyToPost(id, dto, customerId);
    }

    @GetMapping("/api/v1/roommate-posts/{id}/applications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<RoommateApplicationResponse> getPostApplications(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal HeaderUser user,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long customerId = Long.parseLong(user.getUserId());
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return roommateService.getPostApplications(id, customerId, status, capped);
    }

    @GetMapping({"/api/v1/roommate-applications/my", "/api/v1/roommate-applications/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<RoommateApplicationResponse> getMyApplications(@AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.getMyApplications(customerId);
    }

    @PutMapping("/api/v1/roommate-applications/{id}/accept")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RoommateApplicationResponse acceptApplication(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.acceptApplication(id, customerId);
    }

    @PutMapping("/api/v1/roommate-applications/{id}/reject")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RoommateApplicationResponse rejectApplication(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.rejectApplication(id, customerId);
    }

    @GetMapping("/api/v1/roommate-applications/{id}/identity-files/{side}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<InputStreamResource> downloadApplicationIdentityFile(
            @PathVariable Long id,
            @PathVariable String side,
            @AuthenticationPrincipal HeaderUser user) {
        IdentityFileStorage.Download download = roommateService.openApplicationIdentityFile(
                id, side, Long.parseLong(user.getUserId()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.filename()).build().toString())
                .body(new InputStreamResource(download.stream()));
    }

    @PutMapping("/api/v1/roommate-applications/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    public RoommateApplicationResponse markApplicationRead(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.markApplicationRead(id, customerId);
    }
}
