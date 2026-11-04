package com.rental.propertiesservice.features.roomtype.mapper;

import com.rental.propertiesservice.features.roomtype.api.dto.RoomTypeResponse;
import com.rental.propertiesservice.features.roomtype.domain.RoomType;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoomTypeMapper {
    RoomTypeResponse toResponse(RoomType roomType);
}
