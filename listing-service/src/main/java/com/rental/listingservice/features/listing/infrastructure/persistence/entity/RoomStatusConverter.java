package com.rental.listingservice.features.listing.infrastructure.persistence.entity;

import com.rental.listingservice.features.listing.domain.enums.RoomStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoomStatusConverter implements AttributeConverter<RoomStatus, String> {

    @Override
    public String convertToDatabaseColumn(RoomStatus status) {
        return status == null ? null : status.getValue();
    }

    @Override
    public RoomStatus convertToEntityAttribute(String value) {
        return value == null ? null : RoomStatus.fromValue(value);
    }
}
