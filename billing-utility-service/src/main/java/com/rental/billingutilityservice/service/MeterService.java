package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.PropertyClient;
import com.rental.billingutilityservice.dto.request.CreateMeterRequest;
import com.rental.billingutilityservice.dto.response.MeterResponse;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.UtilityType;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.repository.UtilityTypeRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dong ho (docs muc 9.5): QUAN_LY dang ky theo phong + loai; moi phong mot dong ho active
 * cho moi loai (409 METER_EXISTS). buildingId lay tu Property qua Feign #6 (503 khi loi).
 */
@Service
@RequiredArgsConstructor
public class MeterService {

    private final MeterRepository meters;
    private final UtilityTypeRepository utilityTypes;
    private final PropertyClient propertyClient;
    private final AuthSupport auth;

    @Transactional
    public MeterResponse create(CreateMeterRequest request) {
        UtilityType utilityType = utilityTypes.findById(request.getUtilityTypeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay loai tien ich"));
        PropertyClient.RoomInternal room = propertyClient.getRoom(request.getRoomId());
        if (room == null || room.getBuildingId() == null) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Khong lay duoc thong tin phong");
        }
        auth.requireBuildingScope(room.getBuildingId());
        meters.findByRoomIdAndUtilityType_IdAndIsActiveTrue(request.getRoomId(), request.getUtilityTypeId())
                .ifPresent(existing -> {
                    throw new BusinessException("METER_EXISTS", "Phong da co dong ho loai nay",
                            HttpStatus.CONFLICT);
                });
        Meter meter = new Meter();
        meter.setRoomId(request.getRoomId());
        meter.setBuildingId(room.getBuildingId());
        meter.setUtilityType(utilityType);
        meter.setMeterCode(request.getMeterCode());
        meter.setInitialIndex(request.getInitialIndex() == null ? BigDecimal.ZERO : request.getInitialIndex());
        meter.setActive(true);
        return toResponse(meters.save(meter));
    }

    @Transactional(readOnly = true)
    public List<MeterResponse> list(Long roomId, Long buildingId) {
        if (roomId != null) {
            List<Meter> result = meters.findByRoomId(roomId);
            if (!result.isEmpty() && !auth.isAdmin()) {
                // STAFF chi xem toa minh phu trach (docs 6.2): suy building tu du lieu, khong tin param.
                auth.requireBuildingScope(result.get(0).getBuildingId());
                List<Long> allowed = allowedBuildings();
                result = result.stream().filter(m -> allowed.contains(m.getBuildingId())).toList();
            }
            return result.stream().map(this::toResponse).toList();
        }
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
            return meters.findByBuildingId(buildingId).stream().map(this::toResponse).toList();
        }
        if (auth.isAdmin()) {
            return meters.findAll().stream().map(this::toResponse).toList();
        }
        // Khong filter ma doi full list -> chi tra toa duoc phan cong.
        List<Long> allowed = allowedBuildings();
        return meters.findAll().stream().filter(m -> allowed.contains(m.getBuildingId())).map(this::toResponse)
                .toList();
    }

    private List<Long> allowedBuildings() {
        try {
            return auth.staffBuildingIds(auth.currentUserId());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private MeterResponse toResponse(Meter meter) {
        return MeterResponse.builder().id(meter.getId()).roomId(meter.getRoomId())
                .buildingId(meter.getBuildingId()).utilityTypeId(meter.getUtilityType().getId())
                .utilityTypeName(meter.getUtilityType().getName()).meterCode(meter.getMeterCode())
                .initialIndex(meter.getInitialIndex()).active(meter.isActive()).build();
    }
}
