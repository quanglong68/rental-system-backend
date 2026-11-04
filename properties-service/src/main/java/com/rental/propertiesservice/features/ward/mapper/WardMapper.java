package com.rental.propertiesservice.features.ward.mapper;

import com.rental.propertiesservice.features.ward.api.dto.*;
import com.rental.propertiesservice.features.ward.domain.Ward;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WardMapper {
    WardResponse toResponse(Ward ward);
}
