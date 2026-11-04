package com.rental.propertiesservice.features.building.application.port.out;

import com.rental.propertiesservice.features.building.application.model.BuildingSearchItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;

public interface BuildingSearchRepository {
    /**
     * @param buildingIds {@code null} for no scope (ADMIN/public); otherwise the only buildings the
     *                    caller may see. An empty collection must return an empty page.
     */
    Page<BuildingSearchItem> search(String keyword, Long provinceId, Long wardId,
                                    Collection<Long> buildingIds, Pageable pageable);

    Page<BuildingSearchItem> nearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters, Pageable pageable);
}
