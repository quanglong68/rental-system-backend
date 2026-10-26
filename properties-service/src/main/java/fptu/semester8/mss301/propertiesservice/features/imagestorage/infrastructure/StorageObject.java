package fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure;

public record StorageObject(
        String key,
        String url,
        String contentType,
        long size
) {
}
