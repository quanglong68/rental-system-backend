package com.rental.propertiesservice.features.room.mapper;
import com.rental.propertiesservice.features.room.api.dto.RoomResponse;
import com.rental.propertiesservice.features.room.domain.Room;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface RoomMapper { RoomResponse toResponse(Room room); }
