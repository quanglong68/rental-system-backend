package fptu.semester8.mss301.propertiesservice.features.building.application.exception;

import fptu.exe202.signify.apiresponse.exception.ConflictException;

public class BuildingHasRoomsException extends ConflictException {
    public BuildingHasRoomsException(long buildingId) {
        super("Building %d cannot be deleted while it has rooms".formatted(buildingId), "BUILDING_HAS_ROOMS");
    }
}
