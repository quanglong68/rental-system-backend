package fptu.semester8.mss301.propertiesservice.features.building.infrastructure.persistence.repository;

import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataBuildingRepository extends JpaRepository<Building, Long> {
    Optional<Building> findByBuildingIdAndDeletedFalse(Long buildingId);

    Optional<Building> findByBuildingId(Long buildingId);

    Page<Building> findAllByDeletedFalse(Pageable pageable);

    Page<Building> findAllByWardIdAndDeletedFalse(Long wardId, Pageable pageable);
}
