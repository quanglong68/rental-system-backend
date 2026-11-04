package com.rental.propertiesservice.features.building.api;

import com.rental.common.dto.PageResponse;
import com.rental.common.error.BusinessException;
import com.rental.propertiesservice.features.building.api.dto.BuildingRequest;
import com.rental.propertiesservice.features.building.api.dto.BuildingResponse;
import com.rental.propertiesservice.features.building.api.dto.BuildingSearchResponse;
import com.rental.propertiesservice.features.building.application.BuildingService;
import com.rental.propertiesservice.features.building.mapper.BuildingMapper;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/buildings")
@Tag(name = "Tòa nhà", description = "Tạo, tìm kiếm và quản lý tòa nhà")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class BuildingController {

    static int MAX_PAGE_SIZE = 100;
    BuildingService buildingService;

    BuildingMapper buildingMapper;

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE','KY_THUAT')")
    @Operation(summary = "Tìm kiếm tòa nhà", description = "Tìm theo từ khóa trong tên/địa chỉ và lọc tùy chọn theo tỉnh, phường. Mặc định page=0, size=20, sort=createdAt,desc; size tối đa 100. sort hỗ trợ createdAt, updatedAt, name và hướng asc/desc.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm kiếm thành công"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tham số phân trang hoặc sort không hợp lệ")})
    public PageResponse<BuildingSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")) @RequestParam(required = false) @Positive Long provinceId,
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")) @RequestParam(required = false) @Positive Long wardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Pageable pageable = searchPageable(page, size, sort, false);
        return PageResponse.of(buildingService.search(keyword, provinceId, wardId, pageable)
                .map(buildingMapper::toSearchResponse));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Tìm tòa nhà lân cận", description = "Tìm các tòa nhà trong bán kính tính bằng mét hoặc km quanh tọa độ đầu vào.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Truy vấn thành công"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tọa độ, bán kính hoặc phân trang không hợp lệ")})
    public PageResponse<BuildingSearchResponse> nearby(
            @Parameter(description = "Latitude from -90 to 90")
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @Parameter(description = "Longitude from -180 to 180")
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Parameter(description = "Search radius in meters")
            @RequestParam(required = false) Double radiusMeters,
            @Parameter(description = "Search radius in kilometers")
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        double effectiveRadius = radiusMeters != null ? radiusMeters : (radiusKm != null ? radiusKm * 1000.0 : 5000.0);
        Pageable pageable = searchPageable(page, size, "distanceMeters,asc", true);
        return PageResponse.of(buildingService.nearby(latitude, longitude, effectiveRadius, pageable)
                .map(buildingMapper::toSearchResponse));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo tòa nhà", description = "Tạo tòa nhà mới từ tên, địa chỉ, phường, tỉnh, tọa độ và mô tả. ID được tạo bởi hệ thống.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công; Location chứa URI tài nguyên mới"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy phường hoặc tỉnh tham chiếu")})
    public ResponseEntity<BuildingResponse> create(@Valid @RequestBody BuildingRequest request) {
        BuildingResponse response = buildingMapper.toResponse(buildingService.create(buildingMapper.toCommand(request)));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{buildingId}")
                .buildAndExpand(response.buildingId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{buildingId}")
    @Operation(summary = "Lấy tòa nhà theo ID", description = "Trả về thông tin chi tiết một tòa nhà.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm thấy tòa nhà"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tòa nhà")})
    public BuildingResponse getById(@Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")) @PathVariable @Positive long buildingId) {
        return buildingMapper.toResponse(buildingService.getById(buildingId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','QUAN_LY','SALE','KY_THUAT')")
    @Operation(summary = "Liệt kê tòa nhà", description = "Trả về danh sách phân trang, có thể lọc theo wardId. page bắt đầu từ 0; size từ 1 đến 100; sort hỗ trợ createdAt, updatedAt, name theo định dạng thuộc_tính,hướng (asc/desc). Mặc định page=0, size=20, sort=createdAt,desc.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Phân trang hoặc sort không hợp lệ")})
    public PageResponse<BuildingResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")) @RequestParam(required = false) @Positive Long wardId,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidPage();
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        return PageResponse.of(buildingService.list(wardId, pageable).map(buildingMapper::toResponse));
    }

    @PutMapping("/{buildingId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật tòa nhà", description = "Thay thế các trường có thể cập nhật bằng dữ liệu request.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tòa nhà hoặc phường/tỉnh tham chiếu")})
    public BuildingResponse update(@Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")) @PathVariable @Positive long buildingId,
                                                @Valid @RequestBody BuildingRequest request) {
        return buildingMapper.toResponse(buildingService.update(buildingId, buildingMapper.toCommand(request)));
    }

    @DeleteMapping("/{buildingId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa tòa nhà", description = "Đánh dấu tòa nhà đã xóa (soft delete).")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Xóa thành công"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tòa nhà"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Không thể xóa tòa nhà đang có phòng")})
    public void delete(@Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")) @PathVariable @Positive long buildingId) {
        buildingService.delete(buildingId);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!property.equals("createdAt") && !property.equals("updatedAt") && !property.equals("name")) {
            throw new BusinessException("INVALID_SORT", "sort phải là một trong createdAt, updatedAt hoặc name",
                    HttpStatus.BAD_REQUEST);
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 2
                    ? Sort.Direction.fromString(parts[1].trim())
                    : Sort.Direction.ASC;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_SORT_DIRECTION", "hướng sắp xếp phải là asc hoặc desc",
                    HttpStatus.BAD_REQUEST);
        }
        return Sort.by(direction, property);
    }

    private Pageable searchPageable(int page, int size, String sort, boolean nearby) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidPage();
        }
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!property.equals("createdAt") && !property.equals("updatedAt") && !property.equals("name")
                && !(nearby && property.equals("distanceMeters"))) {
            throw new BusinessException("INVALID_SORT",
                    "sort must be createdAt, updatedAt, name" + (nearby ? " or distanceMeters" : ""),
                    HttpStatus.BAD_REQUEST);
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 2 ? Sort.Direction.fromString(parts[1].trim()) : Sort.Direction.DESC;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_SORT_DIRECTION", "hướng sắp xếp phải là asc hoặc desc",
                    HttpStatus.BAD_REQUEST);
        }
        if (nearby && parts.length == 1) direction = Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private static BusinessException invalidPage() {
        return new BusinessException("INVALID_PAGE", "page phải >= 0 và size trong khoảng 1..100",
                HttpStatus.BAD_REQUEST);
    }
}
