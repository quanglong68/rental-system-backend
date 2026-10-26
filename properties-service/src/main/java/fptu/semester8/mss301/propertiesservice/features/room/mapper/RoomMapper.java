package fptu.semester8.mss301.propertiesservice.features.room.mapper;
import fptu.semester8.mss301.propertiesservice.features.room.api.dto.RoomResponse;
import fptu.semester8.mss301.propertiesservice.features.room.domain.Room;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface RoomMapper { RoomResponse toResponse(Room room); }
