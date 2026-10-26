package fptu.semester8.mss301.propertiesservice.features.building.application.exception;

import fptu.exe202.signify.apiresponse.exception.ResourceNotFoundException;

public class BuildingNotFoundException extends ResourceNotFoundException {
    public BuildingNotFoundException(long buildingId) {
        super("Building %d was not found".formatted(buildingId), "BUILDING_NOT_FOUND");
    }
}
