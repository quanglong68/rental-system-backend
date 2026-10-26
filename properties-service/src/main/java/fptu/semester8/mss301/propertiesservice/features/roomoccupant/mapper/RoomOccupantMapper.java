package fptu.semester8.mss301.propertiesservice.features.roomoccupant.mapper;

import org.mapstruct.Mapper;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.api.dto.RoomOccupantResponse;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.domain.RoomOccupant;

@Mapper(componentModel = "spring")
public interface RoomOccupantMapper {
    RoomOccupantResponse toResponse(RoomOccupant occupant);
}
