package com.rental.listingservice.features.listing.infrastructure.persistence.entity;

import com.rental.listingservice.features.listing.domain.enums.ListingStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ListingStatusConverter implements AttributeConverter<ListingStatus, String> {

    @Override
    public String convertToDatabaseColumn(ListingStatus status) {
        return status == null ? null : status.getValue();
    }

    @Override
    public ListingStatus convertToEntityAttribute(String value) {
        return value == null ? null : ListingStatus.fromValue(value);
    }
}
