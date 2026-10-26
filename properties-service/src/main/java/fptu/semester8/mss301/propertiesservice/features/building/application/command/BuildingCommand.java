package fptu.semester8.mss301.propertiesservice.features.building.application.command;

import java.math.BigDecimal;

public record BuildingCommand(
        String name,
        String address,
        Long wardId,
        Long provinceId,
        BigDecimal latitude,
        BigDecimal longitude,
        String description,
        String buildingStatus
) {
    public BuildingCommand(String name, String address, Long wardId, Long provinceId,
                           BigDecimal latitude, BigDecimal longitude, String description) {
        this(name, address, wardId, provinceId, latitude, longitude, description, null);
    }
}
