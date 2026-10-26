package fptu.semester8.mss301.propertiesservice.features.building.application;

import fptu.exe202.signify.apiresponse.exception.ResourceNotFoundException;
import fptu.semester8.mss301.propertiesservice.common.id.IdGenerator;
import fptu.semester8.mss301.propertiesservice.features.building.application.exception.BuildingHasRoomsException;
import fptu.semester8.mss301.propertiesservice.features.building.application.exception.BuildingNotFoundException;
import fptu.semester8.mss301.propertiesservice.features.building.application.exception.WardNotFoundException;
import fptu.semester8.mss301.propertiesservice.features.building.application.command.BuildingCommand;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingReferenceRepository;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingRepository;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingSearchRepository;
import fptu.semester8.mss301.propertiesservice.features.building.application.model.BuildingSearchItem;
import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import fptu.semester8.mss301.propertiesservice.features.events.PropertyEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import java.math.BigDecimal;
import fptu.semester8.mss301.propertiesservice.features.building.domain.BuildingStatus;
import fptu.exe202.signify.apiresponse.exception.ConflictException;

import java.time.Instant;

@Service
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class BuildingService {

    BuildingRepository buildingRepository;

    BuildingReferenceRepository referenceRepository;

    IdGenerator idGenerator;

    BuildingSearchRepository searchRepository;

    PropertyEventPublisher events;

    double maxSearchRadiusMeters;

    @Autowired
    public BuildingService(BuildingRepository buildingRepository,
                           BuildingReferenceRepository referenceRepository,
                           IdGenerator idGenerator,
                           BuildingSearchRepository searchRepository,
                           PropertyEventPublisher events,
                           @Value("${building.search.max-radius-meters:50000}") double maxSearchRadiusMeters) {
        this.buildingRepository = buildingRepository;
        this.referenceRepository = referenceRepository;
        this.idGenerator = idGenerator;
        this.searchRepository = searchRepository;
        this.events = events;
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
        return buildingRepository.save(building);
    }

    @Transactional(readOnly = true)
    public Building getById(long buildingId) {
        return findActive(buildingId);
    }

    @Transactional(readOnly = true)
    public Page<Building> list(Long wardId, Pageable pageable) {
        return wardId == null
                ? buildingRepository.findActive(pageable)
                : buildingRepository.findActiveByWardId(wardId, pageable);
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
                throw new BadRequestException("Unsupported building status", "INVALID_BUILDING_STATUS");
            }
            if (BuildingStatus.CLOSED.getValue().equals(status.getValue())
                    && (referenceRepository.hasResidents(buildingId) || referenceRepository.hasOccupiedRooms(buildingId))) {
                throw new ConflictException("Cannot close a building while rooms still have residents", "BUILDING_HAS_RESIDENTS");
            }
            building.changeStatus(status, Instant.now());
        }
        building.update(
                request.name().trim(), request.address().trim(), request.wardId(), request.provinceId(),
                request.latitude(), request.longitude(), request.description(), Instant.now()
        );
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
            throw new ResourceNotFoundException("Province %d was not found".formatted(provinceId), "PROVINCE_NOT_FOUND");
        }
    }

    @Transactional(readOnly = true)
    public Page<BuildingSearchItem> search(String keyword, Long provinceId, Long wardId, Pageable pageable) {
        if (provinceId != null) validateProvince(provinceId);
        if (wardId != null) validateWard(wardId);
        if (wardId != null && provinceId != null) validateWardProvince(wardId, provinceId);
        return searchRepository.search(keyword, provinceId, wardId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BuildingSearchItem> nearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters, Pageable pageable) {
        if (latitude == null || latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0)
            throw new BadRequestException("latitude must be between -90 and 90", "INVALID_LATITUDE");
        if (longitude == null || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0)
            throw new BadRequestException("longitude must be between -180 and 180", "INVALID_LONGITUDE");
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > maxSearchRadiusMeters)
            throw new BadRequestException("radiusMeters must be greater than 0 and at most " + maxSearchRadiusMeters, "INVALID_RADIUS");
        return searchRepository.nearby(latitude, longitude, radiusMeters, pageable);
    }

    private void validateWardProvince(long wardId, long provinceId) {
        if (!referenceRepository.wardBelongsToProvince(wardId, provinceId)) {
            throw new BadRequestException("Ward does not belong to the supplied province", "WARD_PROVINCE_MISMATCH");
        }
    }
}
