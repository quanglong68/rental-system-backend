package fptu.semester8.mss301.propertiesservice.features.listingintegration.api.dto;

import java.math.BigDecimal;

public record ListingRoomSnapshotResponse(Long roomId, Long buildingId, String buildingName, String address,
        Long wardId, Long districtId, Long roomTypeId, String roomTypeName, String roomNumber,
        Integer maxCapacity, String roomStatus, BigDecimal price, BigDecimal latitude, BigDecimal longitude) {
}
