package fptu.semester8.mss301.propertiesservice.features.building.application.model;

import java.math.BigDecimal;
import java.time.Instant;

public record BuildingSearchItem(Long buildingId, String name, String address, Long wardId,
                                 String wardName, Long provinceId, String provinceName,
                                 BigDecimal latitude, BigDecimal longitude, String description, String buildingStatus,
                                 Instant createdAt, Instant updatedAt, Double distanceMeters,
                                 String buildingUrlImage) {
}
