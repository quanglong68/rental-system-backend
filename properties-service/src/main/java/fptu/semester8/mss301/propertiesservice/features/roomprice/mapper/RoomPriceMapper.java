package fptu.semester8.mss301.propertiesservice.features.roomprice.mapper;

import fptu.semester8.mss301.propertiesservice.features.roomprice.api.dto.RoomPriceResponse;
import fptu.semester8.mss301.propertiesservice.features.roomprice.domain.RoomPrice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoomPriceMapper {
    RoomPriceResponse toResponse(RoomPrice roomPrice);
}
