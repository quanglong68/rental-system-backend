package fptu.semester8.mss301.propertiesservice.features.building.application.port.out;

import fptu.semester8.mss301.propertiesservice.features.building.application.model.BuildingSearchItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Set;

public interface BuildingSearchRepository {
    Page<BuildingSearchItem> search(String keyword, Long provinceId, Long wardId, Pageable pageable);

    Page<BuildingSearchItem> searchScoped(String keyword, Long provinceId, Long wardId,
                                          Set<Long> buildingIds, Pageable pageable);

    Page<BuildingSearchItem> nearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters, Pageable pageable);
}
