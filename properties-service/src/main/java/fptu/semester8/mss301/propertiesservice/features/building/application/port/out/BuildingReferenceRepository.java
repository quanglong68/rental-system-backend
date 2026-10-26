package fptu.semester8.mss301.propertiesservice.features.building.application.port.out;

public interface BuildingReferenceRepository {
    boolean wardExists(long wardId);

    boolean hasRooms(long buildingId);

    void lockBuilding(long buildingId);

    boolean hasResidents(long buildingId);

    boolean hasOccupiedRooms(long buildingId);
    boolean buildingExists(long buildingId);
    boolean existProvinceById(long provinceId);

    boolean existWardById(long wardId);

    boolean wardBelongsToProvince(long wardId, long provinceId);
}
