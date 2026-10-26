package fptu.semester8.mss301.propertiesservice.features.province.mapper;

import fptu.semester8.mss301.propertiesservice.features.province.api.dto.*;
import fptu.semester8.mss301.propertiesservice.features.province.domain.Province;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProvinceMapper {
    ProvinceResponse toResponse(Province province);
}
