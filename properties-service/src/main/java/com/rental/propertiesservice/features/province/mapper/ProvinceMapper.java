package com.rental.propertiesservice.features.province.mapper;

import com.rental.propertiesservice.features.province.api.dto.*;
import com.rental.propertiesservice.features.province.domain.Province;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProvinceMapper {
    ProvinceResponse toResponse(Province province);
}
