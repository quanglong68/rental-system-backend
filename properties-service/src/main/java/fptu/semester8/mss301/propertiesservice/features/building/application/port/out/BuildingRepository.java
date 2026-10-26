package fptu.semester8.mss301.propertiesservice.features.building.application.port.out;

import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BuildingRepository {
    Building save(Building building);

    Optional<Building> findActiveById(long buildingId);

    Optional<Building> findById(long buildingId);

    Page<Building> findActive(Pageable pageable);

    Page<Building> findActiveByWardId(long wardId, Pageable pageable);
}
