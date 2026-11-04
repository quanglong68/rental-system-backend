package com.rental.listingbooking.controller;

import com.rental.listingbooking.dto.ListingResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateListingRequest;
import com.rental.listingbooking.dto.UpdateListingStatusRequest;
import com.rental.listingbooking.service.ListingService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @GetMapping
    public PageResponse<ListingResponse> searchListings(
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return listingService.search(
                wardId, districtId, buildingId, roomTypeId, minPrice, maxPrice, keyword,
                latitude, longitude, radiusKm, capped);
    }

    @GetMapping("/{id}")
    public ListingResponse getListingById(@PathVariable Long id) {
        return listingService.getById(id);
    }

    @GetMapping("/room/{roomId}")
    public ListingResponse getListingByRoomId(@PathVariable Long roomId) {
        return listingService.getByRoomId(roomId);
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public PageResponse<ListingResponse> manageListings(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return listingService.manageListings(buildingId, status, capped);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public ListingResponse updateListing(
            @PathVariable Long id,
            @Valid @RequestBody UpdateListingRequest req) {
        return listingService.updateListing(id, req);
    }

    @RequestMapping(path = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    public ListingResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateListingStatusRequest req) {
        return listingService.updateStatus(id, req);
    }
}
