package fptu.semester8.mss301.propertiesservice.features.building.api;

import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingRequest;
import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingResponse;
import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingSearchResponse;
import fptu.semester8.mss301.propertiesservice.features.building.application.BuildingService;
import fptu.semester8.mss301.propertiesservice.features.building.mapper.BuildingMapper;
import fptu.semester8.mss301.propertiesservice.config.PropertyScope;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    static final int MAX_PAGE_SIZE = 100;
    /** 1 km = 1000 m; dùng để chuyển đổi radiusKm → radiusMeters cho PostGIS. */
    static final double METERS_PER_KM = 1000.0;

    BuildingService buildingService;
    BuildingMapper buildingMapper;
    PropertyScope scope;

    /**
     * Đặc tả mục 9: GET /api/v1/buildings?wardId&keyword&page&size
     * Hỗ trợ thêm provinceId và sort để giữ tính năng hiện có.
     */
    @GetMapping
    @Operation(summary = "Liệt kê / tìm kiếm tòa nhà",
            description = "Liệt kê tòa nhà với lọc tùy chọn theo wardId, provinceId và keyword. " +
                    "page bắt đầu từ 0; size từ 1 đến 100; sort hỗ trợ createdAt, updatedAt, name. " +
                    "Mặc định page=0, size=20, sort=createdAt,desc.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Phân trang hoặc sort không hợp lệ")
    })
    public Page<BuildingResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
            @RequestParam(required = false) @Positive Long wardId,
            @RequestParam(required = false) String keyword,
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
            @RequestParam(required = false) @Positive Long provinceId,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        // Nếu có keyword hoặc provinceId → dùng search engine; ngược lại → list đơn giản
        if (keyword != null || provinceId != null) {
            var results = scope.isAdmin()
                    ? buildingService.search(keyword, provinceId, wardId, pageable)
                    : buildingService.searchScoped(keyword, provinceId, wardId, scope.assignedBuildings(), pageable);
            return results.map(buildingMapper::toSearchResponse).map(s ->
                    new BuildingResponse(s.buildingId(), s.name(), s.address(),
                            s.wardId(), s.provinceId(), s.latitude(), s.longitude(),
                            s.description(), s.buildingStatus(), s.createdAt(), s.updatedAt()));
        }
        var results = scope.isAdmin()
                ? buildingService.list(wardId, pageable)
                : buildingService.listScoped(wardId, scope.assignedBuildings(), pageable);
        return results.map(buildingMapper::toResponse);
    }

    /**
     * Đặc tả mục 9: GET /api/v1/buildings/nearby?lat&lng&radiusKm
     * Chuyển đổi radiusKm → radiusMeters trước khi gọi service PostGIS.
     */
    @GetMapping("/nearby")
    @Operation(summary = "Tìm tòa nhà lân cận",
            description = "Tìm các tòa nhà trong bán kính tính bằng kilômét quanh tọa độ đầu vào. " +
                    "Bán kính tối đa cấu hình qua building.search.max-radius-meters (mặc định 50 km). " +
                    "page mặc định 0, size mặc định 20 và tối đa 100.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Truy vấn thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tọa độ, bán kính hoặc phân trang không hợp lệ")
    })
    public Page<BuildingSearchResponse> nearby(
            @Parameter(description = "Vĩ độ, từ -90 đến 90")
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @Parameter(description = "Kinh độ, từ -180 đến 180")
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @Parameter(description = "Bán kính tìm kiếm tính bằng kilômét; tối đa được cấu hình bởi building.search.max-radius-meters")
            @RequestParam @Positive double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "distanceMeters"));
        double radiusMeters = radiusKm * METERS_PER_KM;
        return buildingService.nearby(lat, lng, radiusMeters, pageable)
                .map(buildingMapper::toSearchResponse);
    }

    @PostMapping
    @Operation(summary = "Tạo tòa nhà",
            description = "Tạo tòa nhà mới từ tên, địa chỉ, phường, tỉnh, tọa độ và mô tả. ID được tạo bởi hệ thống.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công; Location chứa URI tài nguyên mới"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy phường hoặc tỉnh tham chiếu")
    })
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
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tìm thấy tòa nhà"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tòa nhà")
    })
    public BuildingResponse getById(
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
            @PathVariable @Positive long buildingId) {
        return buildingMapper.toResponse(buildingService.getById(buildingId));
    }

    @PutMapping("/{buildingId}")
    @Operation(summary = "Cập nhật tòa nhà", description = "Thay thế các trường có thể cập nhật bằng dữ liệu request.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tòa nhà hoặc phường/tỉnh tham chiếu")
    })
    public BuildingResponse update(
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
            @PathVariable @Positive long buildingId,
            @Valid @RequestBody BuildingRequest request) {
        return buildingMapper.toResponse(buildingService.update(buildingId, buildingMapper.toCommand(request)));
    }

    @DeleteMapping("/{buildingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa tòa nhà", description = "Đánh dấu tòa nhà đã xóa (soft delete).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Xóa thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy tòa nhà"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Không thể xóa tòa nhà đang có phòng")
    })
    public void delete(
            @Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
            @PathVariable @Positive long buildingId) {
        buildingService.delete(buildingId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String property = parts[0].trim();
        if (!property.equals("createdAt") && !property.equals("updatedAt") && !property.equals("name")) {
            throw new IllegalArgumentException("sort must be one of createdAt, updatedAt, or name");
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 2
                    ? Sort.Direction.fromString(parts[1].trim())
                    : Sort.Direction.DESC;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sort direction must be asc or desc");
        }
        return Sort.by(direction, property);
    }
}
