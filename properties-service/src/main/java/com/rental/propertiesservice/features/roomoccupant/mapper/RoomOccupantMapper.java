package com.rental.propertiesservice.features.roomoccupant.mapper;

import org.mapstruct.Mapper;
import com.rental.propertiesservice.features.roomoccupant.api.dto.RoomOccupantResponse;
import com.rental.propertiesservice.features.roomoccupant.domain.RoomOccupant;

@Mapper(componentModel = "spring")
public interface RoomOccupantMapper {
    RoomOccupantResponse toResponse(RoomOccupant occupant);
}
