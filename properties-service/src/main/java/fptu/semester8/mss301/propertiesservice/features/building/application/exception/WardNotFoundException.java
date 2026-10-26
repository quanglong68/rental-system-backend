package fptu.semester8.mss301.propertiesservice.features.building.application.exception;

import fptu.exe202.signify.apiresponse.exception.ResourceNotFoundException;

public class WardNotFoundException extends ResourceNotFoundException {
    public WardNotFoundException(long wardId) {
        super("Ward %d was not found".formatted(wardId), "WARD_NOT_FOUND");
    }
}
