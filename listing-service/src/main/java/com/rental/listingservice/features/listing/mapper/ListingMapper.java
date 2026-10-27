package com.rental.listingservice.features.listing.mapper;

import com.rental.listingservice.features.listing.api.response.ListingResponse;
import com.rental.listingservice.features.listing.infrastructure.persistence.entity.RoomListing;
import org.springframework.stereotype.Component;

@Component
public class ListingMapper {

    public ListingResponse toResponse(RoomListing listing) {
        return new ListingResponse(
                listing.getId(), listing.getRoomId(), listing.getBuildingId(), listing.getBuildingName(),
                listing.getAddress(), listing.getWardId(), listing.getDistrictId(), listing.getRoomTypeId(),
                listing.getRoomTypeName(), listing.getRoomNumber(), listing.getMaxCapacity(), listing.getPrice(),
                listing.getLatitude(), listing.getLongitude(), listing.getRoomStatus().getValue(),
                listing.getTitle(), listing.getDescription(), listing.getStatus().getValue(),
                listing.getCreatedAt(), listing.getUpdatedAt());
    }
}
