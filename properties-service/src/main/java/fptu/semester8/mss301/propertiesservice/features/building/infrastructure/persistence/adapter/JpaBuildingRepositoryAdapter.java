package fptu.semester8.mss301.propertiesservice.features.building.infrastructure.persistence.adapter;

import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingRepository;
import fptu.semester8.mss301.propertiesservice.features.building.infrastructure.persistence.repository.SpringDataBuildingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaBuildingRepositoryAdapter implements BuildingRepository {
    private final SpringDataBuildingRepository repository;

    @Override
    public Building save(Building building) {
        return repository.save(building);
    }

    @Override
    public Optional<Building> findActiveById(long buildingId) {
        return repository.findByBuildingIdAndDeletedFalse(buildingId);
    }

    @Override
    public Optional<Building> findById(long buildingId) {
        return repository.findByBuildingId(buildingId);
    }

    @Override
    public Page<Building> findActive(Pageable pageable) {
        return repository.findAllByDeletedFalse(pageable);
    }

    @Override
    public Page<Building> findActiveByWardId(long wardId, Pageable pageable) {
        return repository.findAllByWardIdAndDeletedFalse(wardId, pageable);
    }
}
