package fptu.semester8.mss301.propertiesservice.features.building.mapper;

import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingResponse;
import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingRequest;
import fptu.semester8.mss301.propertiesservice.features.building.application.command.BuildingCommand;
import fptu.semester8.mss301.propertiesservice.features.building.domain.Building;
import fptu.semester8.mss301.propertiesservice.features.building.application.model.BuildingSearchItem;
import fptu.semester8.mss301.propertiesservice.features.building.api.dto.BuildingSearchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BuildingMapper {
    BuildingResponse toResponse(Building building);

    BuildingCommand toCommand(BuildingRequest request);

    BuildingSearchResponse toSearchResponse(BuildingSearchItem item);
}
