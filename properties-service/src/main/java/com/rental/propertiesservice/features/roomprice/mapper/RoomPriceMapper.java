package com.rental.propertiesservice.features.roomprice.mapper;

import com.rental.propertiesservice.features.roomprice.api.dto.RoomPriceResponse;
import com.rental.propertiesservice.features.roomprice.domain.RoomPrice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoomPriceMapper {
    RoomPriceResponse toResponse(RoomPrice roomPrice);
}
