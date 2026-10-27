package com.rental.listingservice.features.listing.api.controller;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.listingservice.features.listing.api.request.CreateListingRequest;
import com.rental.listingservice.features.listing.api.request.UpdateListingRequest;
import com.rental.listingservice.features.listing.api.response.ListingResponse;
import com.rental.listingservice.features.listing.application.ListingApplicationService;
import com.rental.listingservice.features.listing.mapper.ListingMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ListingApplicationService service;
    private final ListingMapper mapper;

    public ListingController(ListingApplicationService service, ListingMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<ListingResponse> search(
            @RequestParam(required = false) @Positive Long buildingId,
            @RequestParam(required = false) @Positive Long wardId,
            @RequestParam(required = false) @Positive Long districtId,
            @RequestParam(required = false) @Positive Long roomTypeId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @RequestParam(required = false) @Size(max = 255) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "minPrice must not exceed maxPrice");
        }
        var pageable = PageRequest.of(page, size, parseSort(sort));
        return service.searchActive(buildingId, wardId, districtId, roomTypeId,
                minPrice, maxPrice, keyword, pageable).map(mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable @Positive long id) {
        return mapper.toResponse(service.getActive(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'QUAN_LY', 'SALE')")
    public ResponseEntity<ListingResponse> create(@Valid @RequestBody CreateListingRequest request) {
        ListingResponse response = mapper.toResponse(service.create(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'QUAN_LY', 'SALE')")
    public ListingResponse update(@PathVariable @Positive long id,
            @Valid @RequestBody UpdateListingRequest request) {
        return mapper.toResponse(service.update(id, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'QUAN_LY', 'SALE')")
    @ResponseStatus(HttpStatus.OK)
    public ListingResponse close(@PathVariable @Positive long id) {
        return mapper.toResponse(service.close(id));
    }

    private static Sort parseSort(String rawSort) {
        String[] parts = rawSort.split(",", 2);
        String property = parts[0].trim();
        if (!property.equals("createdAt") && !property.equals("price") && !property.equals("title")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "sort must be createdAt, price, or title");
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 2 ? Sort.Direction.fromString(parts[1].trim()) : Sort.Direction.DESC;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sort direction must be asc or desc");
        }
        return Sort.by(direction, property);
    }
}
