package com.rental.billingutilityservice.controller;

import com.rental.billingutilityservice.dto.request.AttachServiceRequest;
import com.rental.billingutilityservice.dto.response.ContractServiceResponse;
import com.rental.billingutilityservice.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Dich vu gan hop dong (docs muc 9.5): QUAN_LY gan/go, STAFF xem. */
@RestController
@RequestMapping("/api/v1/contract-services")
@Tag(name = "Dịch vụ theo hợp đồng", description = "Gắn và gỡ dịch vụ định kỳ áp dụng cho từng hợp đồng")
@RequiredArgsConstructor
public class ContractServiceController {

    private final CatalogService catalog;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Gắn dịch vụ vào hợp đồng", description = "Đăng ký dịch vụ định kỳ và số lượng áp dụng cho một hợp đồng thuê.")
    public ContractServiceResponse attach(@Valid @RequestBody AttachServiceRequest request) {
        return catalog.attach(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY','KY_THUAT','SALE','ADMIN')")
    @Operation(summary = "Danh sách dịch vụ theo hợp đồng", description = "Liệt kê các dịch vụ đang gắn vào contractId.")
    public List<ContractServiceResponse> list(@RequestParam Long contractId) {
        return catalog.listByContract(contractId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")
    @Operation(summary = "Gỡ dịch vụ khỏi hợp đồng", description = "Xóa dịch vụ đã gắn khỏi hợp đồng theo ID.")
    public void detach(@PathVariable Long id) {
        catalog.detach(id);
    }
}
