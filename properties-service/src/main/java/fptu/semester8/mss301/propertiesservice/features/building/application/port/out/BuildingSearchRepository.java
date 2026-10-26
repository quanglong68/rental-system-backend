package fptu.semester8.mss301.propertiesservice.features.building.application.port.out;

import fptu.semester8.mss301.propertiesservice.features.building.application.model.BuildingSearchItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface BuildingSearchRepository {
    Page<BuildingSearchItem> search(String keyword, Long provinceId, Long wardId, Pageable pageable);

    Page<BuildingSearchItem> nearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters, Pageable pageable);
}
