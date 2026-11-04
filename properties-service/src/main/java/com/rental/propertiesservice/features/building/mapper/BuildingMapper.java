package com.rental.propertiesservice.features.building.mapper;

import com.rental.propertiesservice.features.building.api.dto.BuildingResponse;
import com.rental.propertiesservice.features.building.api.dto.BuildingRequest;
import com.rental.propertiesservice.features.building.application.command.BuildingCommand;
import com.rental.propertiesservice.features.building.domain.Building;
import com.rental.propertiesservice.features.building.application.model.BuildingSearchItem;
import com.rental.propertiesservice.features.building.api.dto.BuildingSearchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BuildingMapper {
    BuildingResponse toResponse(Building building);

    BuildingCommand toCommand(BuildingRequest request);

    BuildingSearchResponse toSearchResponse(BuildingSearchItem item);
}
