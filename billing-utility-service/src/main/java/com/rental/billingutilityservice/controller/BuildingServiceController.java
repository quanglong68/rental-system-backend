package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.BuildingServiceRequest;
import com.rental.billingutilityservice.dto.response.BuildingServiceResponse;
import com.rental.billingutilityservice.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dich vu toa nha (docs muc 9.5): QUAN_LY tao/sua, STAFF xem. */
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class BuildingServiceController {

    private final CatalogService catalog;

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public List<BuildingServiceResponse> list(@RequestParam(required = false) Long buildingId) {
        return catalog.listServices(buildingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public BuildingServiceResponse create(@Valid @RequestBody BuildingServiceRequest request) {
        return catalog.createService(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public BuildingServiceResponse update(@PathVariable Long id,
            @Valid @RequestBody BuildingServiceRequest request) {
        return catalog.updateService(id, request);
    }
}
