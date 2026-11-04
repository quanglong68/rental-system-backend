package com.rental.propertiesservice.features.location.api;

import com.rental.propertiesservice.features.province.api.dto.ProvinceResponse;
import com.rental.propertiesservice.features.province.application.ProvinceService;
import com.rental.propertiesservice.features.province.mapper.ProvinceMapper;
import com.rental.propertiesservice.features.ward.api.dto.WardResponse;
import com.rental.propertiesservice.features.ward.application.WardService;
import com.rental.propertiesservice.features.ward.mapper.WardMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only location catalogue (spec 6.4). The spec describes province → district → ward,
 * but V6 replaced that hierarchy with the two-tier province → ward model, so wards are listed
 * directly under their province. Admin CRUD remains on /api/v1/provinces and /api/v1/wards.
 */
@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Địa danh", description = "Tra cứu tỉnh/thành và phường/xã")
@RequiredArgsConstructor
public class LocationController {

    private final ProvinceService provinces;
    private final ProvinceMapper provinceMapper;
    private final WardService wards;
    private final WardMapper wardMapper;

    @GetMapping("/provinces")
    @Operation(summary = "Liệt kê tỉnh", description = "Trả về danh sách tất cả tỉnh/thành.")
    public List<ProvinceResponse> listProvinces() {
        return provinces.list().stream().map(provinceMapper::toResponse).toList();
    }

    @GetMapping("/provinces/{provinceId}/wards")
    @Operation(summary = "Liệt kê phường theo tỉnh",
            description = "Trả về danh sách phường/xã thuộc tỉnh chỉ định (mô hình 2 cấp sau V6).")
    public List<WardResponse> listWards(
            @Parameter(schema = @Schema(type = "string", example = "898785309339156480"))
            @PathVariable @Positive long provinceId) {
        return wards.getWardsByProvinceId(provinceId).stream().map(wardMapper::toResponse).toList();
    }
}
