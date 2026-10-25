package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.CreateReadingRequest;
import com.rental.billingutilityservice.dto.request.UpdateReadingRequest;
import com.rental.billingutilityservice.dto.response.MeterReadingResponse;
import com.rental.billingutilityservice.dto.response.PageResponse;
import com.rental.billingutilityservice.service.MeterReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Chi so dien nuoc (docs muc 9.5): KY_THUAT/QUAN_LY nhap/sua, STAFF xem. */
@RestController
@RequestMapping("/api/v1/meter-readings")
@RequiredArgsConstructor
public class MeterReadingController {

    private final MeterReadingService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('KY_THUAT','QUAN_LY','ADMIN')")
    public MeterReadingResponse create(@Valid @RequestBody CreateReadingRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('KY_THUAT','QUAN_LY','ADMIN')")
    public MeterReadingResponse update(@PathVariable Long id, @Valid @RequestBody UpdateReadingRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public PageResponse<MeterReadingResponse> search(@RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long meterId, @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"));
        return service.search(meterId, roomId, buildingId, month, year, pageable);
    }
}
