package fptu.semester8.mss301.propertiesservice.features.ward.mapper;

import fptu.semester8.mss301.propertiesservice.features.ward.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.ward.domain.Ward;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WardMapper {
    WardResponse toResponse(Ward ward);
}
