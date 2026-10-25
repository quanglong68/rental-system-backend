package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.CreateMeterRequest;
import com.rental.billingutilityservice.dto.response.MeterResponse;
import com.rental.billingutilityservice.service.MeterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dong ho (docs muc 9.5): QUAN_LY dang ky, STAFF xem. */
@RestController
@RequestMapping("/api/v1/meters")
@RequiredArgsConstructor
public class MeterController {

    private final MeterService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    public MeterResponse create(@Valid @RequestBody CreateMeterRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    public List<MeterResponse> list(@RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long buildingId) {
        return service.list(roomId, buildingId);
    }
}
