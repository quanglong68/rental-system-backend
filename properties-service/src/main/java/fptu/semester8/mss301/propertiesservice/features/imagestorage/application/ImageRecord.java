package fptu.semester8.mss301.propertiesservice.features.imagestorage.application;

import java.time.Instant;

public record ImageRecord(String imageId, String ownerId, String imageUrl,
                          int displayOrder, boolean primaryImage, Instant createdAt) {
}
