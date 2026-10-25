package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.UtilityTypeRequest;
import com.rental.billingutilityservice.dto.response.UtilityTypeResponse;
import com.rental.billingutilityservice.service.UtilityTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Loai tien ich (docs muc 9.5): STAFF xem, ADMIN tao/sua. */
@RestController
@RequestMapping("/api/v1/utility-types")
@RequiredArgsConstructor
public class UtilityTypeController {

    private final UtilityTypeService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public List<UtilityTypeResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UtilityTypeResponse create(@Valid @RequestBody UtilityTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UtilityTypeResponse update(@PathVariable Long id, @Valid @RequestBody UtilityTypeRequest request) {
        return service.update(id, request);
    }
}
