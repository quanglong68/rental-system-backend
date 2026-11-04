package com.rental.propertiesservice.features.building.application;

import com.rental.common.error.NotFoundException;
import com.rental.propertiesservice.common.id.IdGenerator;
import com.rental.propertiesservice.features.building.application.exception.BuildingHasRoomsException;
import com.rental.propertiesservice.features.building.application.exception.BuildingNotFoundException;
import com.rental.propertiesservice.features.building.application.exception.WardNotFoundException;
import com.rental.propertiesservice.features.building.application.command.BuildingCommand;
import com.rental.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import com.rental.propertiesservice.features.building.application.port.out.BuildingRepository;
import com.rental.propertiesservice.features.building.application.port.out.BuildingSearchRepository;
import com.rental.propertiesservice.features.building.application.model.BuildingSearchItem;
import com.rental.propertiesservice.features.building.domain.Building;
import com.rental.propertiesservice.features.events.PropertyEventPublisher;
import com.rental.propertiesservice.config.BuildingAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.rental.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.List;
import com.rental.propertiesservice.features.building.domain.BuildingStatus;

import java.time.Instant;

@Service
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class BuildingService {

    BuildingRepository buildingRepository;

    BuildingReferenceRepository referenceRepository;

    IdGenerator idGenerator;

    BuildingSearchRepository searchRepository;

    PropertyEventPublisher events;

    BuildingAccessGuard access;

    double maxSearchRadiusMeters;

    @Autowired
    public BuildingService(BuildingRepository buildingRepository,
                           BuildingReferenceRepository referenceRepository,
                           IdGenerator idGenerator,
                           BuildingSearchRepository searchRepository,
                           PropertyEventPublisher events,
                           BuildingAccessGuard access,
                           @Value("${building.search.max-radius-meters:50000}") double maxSearchRadiusMeters) {
        this.buildingRepository = buildingRepository;
        this.referenceRepository = referenceRepository;
        this.idGenerator = idGenerator;
        this.searchRepository = searchRepository;
        this.events = events;
        this.access = access;
        this.maxSearchRadiusMeters = maxSearchRadiusMeters;
    }

    @Transactional
    public Building create(BuildingCommand request) {

        validateWard(request.wardId());
        validateProvince(request.provinceId());
        validateWardProvince(request.wardId(), request.provinceId());
        Instant now = Instant.now();
        Building building = new Building(
                idGenerator.nextId(), request.name().trim(), request.address().trim(),
                request.wardId(), request.provinceId(), request.latitude(), request.longitude(),
                request.description(), now
        );
        building.updateLessor(request.lessorName(), request.lessorDateOfBirth(), request.lessorIdNumber(),
                request.lessorIdIssueDate(), request.lessorIdIssuePlace(), request.lessorPhone(),
                request.lessorAddress(), now);
        return buildingRepository.save(building);
    }

    @Transactional(readOnly = true)
    public Building getById(long buildingId) {
        return findActive(buildingId);
    }

    @Transactional(readOnly = true)
    public Page<Building> list(Long wardId, Pageable pageable) {
        List<Long> allowed = access.allowedBuildingIdsOrNull();
        if (allowed == null) {
            return wardId == null
                    ? buildingRepository.findActive(pageable)
                    : buildingRepository.findActiveByWardId(wardId, pageable);
        }
        if (allowed.isEmpty()) {
            return Page.empty(pageable);
        }
        return wardId == null
                ? buildingRepository.findActiveIn(allowed, pageable)
                : buildingRepository.findActiveByWardIdIn(wardId, allowed, pageable);
    }

    @Transactional
    public Building update(long buildingId, BuildingCommand request) {
        referenceRepository.lockBuilding(buildingId);
        Building building = findActive(buildingId);
        validateWard(request.wardId());
        validateProvince(request.provinceId());
        validateWardProvince(request.wardId(), request.provinceId());
        if (request.buildingStatus() != null) {
            BuildingStatus status;
            try {
                status = BuildingStatus.valueOf(request.buildingStatus());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("INVALID_BUILDING_STATUS", "Trạng thái tòa nhà không được hỗ trợ", HttpStatus.BAD_REQUEST);
            }
            if (BuildingStatus.CLOSED.getValue().equals(status.getValue())
                    && (referenceRepository.hasResidents(buildingId) || referenceRepository.hasOccupiedRooms(buildingId))) {
                throw new BusinessException("BUILDING_HAS_RESIDENTS", "Không thể đóng tòa nhà khi vẫn còn phòng có người ở", HttpStatus.CONFLICT);
            }
            building.changeStatus(status, Instant.now());
        }
        building.update(
                request.name().trim(), request.address().trim(), request.wardId(), request.provinceId(),
                request.latitude(), request.longitude(), request.description(), Instant.now()
        );
        // Thông tin bên A là nhóm trường tùy chọn: chỉ ghi đè khi client thực sự gửi lên,
        // tránh xóa dữ liệu khi cập nhật các trường khác.
        if (request.lessorName() != null) {
            building.updateLessor(request.lessorName(), request.lessorDateOfBirth(), request.lessorIdNumber(),
                    request.lessorIdIssueDate(), request.lessorIdIssuePlace(), request.lessorPhone(),
                    request.lessorAddress(), Instant.now());
        }
        Building saved = buildingRepository.save(building);
        events.buildingUpdated(saved);
        return saved;
    }

    @Transactional
    public void delete(long buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BuildingNotFoundException(buildingId));
        if (building.isDeleted()) {
            return;
        }
        if (referenceRepository.hasRooms(buildingId)) {
            throw new BuildingHasRoomsException(buildingId);
        }
        building.softDelete(Instant.now());
        buildingRepository.save(building);
    }

    @Transactional
    public void refreshOccupancyStatus(long buildingId) {
        referenceRepository.lockBuilding(buildingId);
        var optional = buildingRepository.findActiveById(buildingId);
        if (optional.isEmpty()) return;
        Building building = optional.get();
        // Keep new buildings ACTIVE until they have rooms; preserve manual closure/maintenance.
        if (BuildingStatus.ACTIVE.getValue().equals(building.getBuildingStatus())
                && referenceRepository.hasRooms(buildingId) && !referenceRepository.hasResidents(buildingId)) {
            building.changeStatus(BuildingStatus.INACTIVE, Instant.now());
            buildingRepository.save(building);
            events.buildingUpdated(building);
        }
    }

    private Building findActive(long buildingId) {
        return buildingRepository.findActiveById(buildingId)
                .orElseThrow(() -> new BuildingNotFoundException(buildingId));
    }

    private void validateWard(Long wardId) {
        if (!referenceRepository.existWardById(wardId)) {
            throw new WardNotFoundException(wardId);
        }
    }

    private void validateProvince(Long provinceId) {
        if (!referenceRepository.existProvinceById(provinceId)) {
            throw new NotFoundException("PROVINCE_NOT_FOUND", "Không tìm thấy tỉnh/thành %d".formatted(provinceId));
        }
    }

    @Transactional(readOnly = true)
    public Page<BuildingSearchItem> search(String keyword, Long provinceId, Long wardId, Pageable pageable) {
        if (provinceId != null) validateProvince(provinceId);
        if (wardId != null) validateWard(wardId);
        if (wardId != null && provinceId != null) validateWardProvince(wardId, provinceId);
        return searchRepository.search(keyword, provinceId, wardId, access.allowedBuildingIdsOrNull(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<BuildingSearchItem> nearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters, Pageable pageable) {
        if (latitude == null || latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0)
            throw new BusinessException("INVALID_LATITUDE", "latitude phải trong khoảng -90..90", HttpStatus.BAD_REQUEST);
        if (longitude == null || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0)
            throw new BusinessException("INVALID_LONGITUDE", "longitude phải trong khoảng -180..180", HttpStatus.BAD_REQUEST);
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > maxSearchRadiusMeters)
            throw new BusinessException("INVALID_RADIUS", "radiusMeters phải lớn hơn 0 và tối đa " + maxSearchRadiusMeters, HttpStatus.BAD_REQUEST);
        return searchRepository.nearby(latitude, longitude, radiusMeters, pageable);
    }

    private void validateWardProvince(long wardId, long provinceId) {
        if (!referenceRepository.wardBelongsToProvince(wardId, provinceId)) {
            throw new BusinessException("WARD_PROVINCE_MISMATCH", "Phường/xã không thuộc tỉnh/thành đã chọn", HttpStatus.BAD_REQUEST);
        }
    }
}
