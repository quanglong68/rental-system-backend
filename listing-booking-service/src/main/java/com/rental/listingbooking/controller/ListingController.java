package com.rental.listingbooking.controller;

import com.rental.listingbooking.dto.ListingResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateListingRequest;
import com.rental.listingbooking.dto.UpdateListingStatusRequest;
import com.rental.listingbooking.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
@Tag(name = "Tin đăng cho thuê", description = "Tìm kiếm công khai, xem chi tiết và quản lý tin đăng phòng")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @GetMapping
    @Operation(summary = "Tìm kiếm tin đăng phòng công khai", description = "Lọc tin đăng đang mở theo phường, tòa nhà, loại phòng, khoảng giá, từ khóa hoặc tọa độ bán kính.")
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
            @ParameterObject @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return listingService.search(
                wardId, districtId, buildingId, roomTypeId, minPrice, maxPrice, keyword,
                latitude, longitude, radiusKm, capped);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết tin đăng theo ID", description = "Xem chi tiết công khai một tin đăng phòng cho thuê.")
    public ListingResponse getListingById(@PathVariable Long id) {
        return listingService.getById(id);
    }

    @GetMapping("/room/{roomId}")
    @Operation(summary = "Lấy tin đăng theo mã phòng", description = "Tra cứu tin đăng tương ứng với roomId.")
    public ListingResponse getListingByRoomId(@PathVariable Long roomId) {
        return listingService.getByRoomId(roomId);
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Quản lý danh sách tin đăng", description = "Nhân sự/Admin xem tất cả tin đăng (gồm cả tin ẩn) theo buildingId và trạng thái.")
    public PageResponse<ListingResponse> manageListings(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return listingService.manageListings(buildingId, status, capped);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Cập nhật nội dung tin đăng", description = "Chỉnh sửa tiêu đề, mô tả, giá hiển thị và tiện nghi của tin đăng.")
    public ListingResponse updateListing(
            @PathVariable Long id,
            @Valid @RequestBody UpdateListingRequest req) {
        return listingService.updateListing(id, req);
    }

    @RequestMapping(path = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Đổi trạng thái tin đăng", description = "Chuyển trạng thái hiển thị của tin đăng (AVAILABLE, HIDDEN, RENTED...).")
    public ListingResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateListingStatusRequest req) {
        return listingService.updateStatus(id, req);
    }
}
