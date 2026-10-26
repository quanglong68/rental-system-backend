package fptu.semester8.mss301.propertiesservice.features.imagestorage.application;

import fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure.ImageStorageProperties;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure.ObjectStorage;
import fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ImageStorageService {
    static Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");


    ObjectStorage objectStorage;

    ImageStorageProperties properties;

    public StorageObject upload(String ownerType, long ownerId, MultipartFile file) {
        if (ownerId <= 0 || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A positive owner ID and non-empty image are required");
        }
        if (file.getSize() > properties.maxSize()) {
            throw new IllegalArgumentException("Image exceeds the maximum allowed size");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are supported");
        }

        String extension = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        String key = ownerType + "/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
        try {
            byte[] data = file.getBytes();
            if (!hasValidSignature(data, contentType.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Image content does not match its media type");
            }
            return objectStorage.upload(key, data, contentType, data.length);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read uploaded image", ex);
        }
    }

    public List<String> upload(String folderName, MultipartFile[] files) {
        if (folderName == null || !folderName.matches("[a-zA-Z0-9_-]{1,80}") || files == null || files.length == 0) {
            throw new IllegalArgumentException("A valid folder name and at least one image are required");
        }
        List<String> urls = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            urls.add(uploadToFolder(folderName, file));
        }
        return urls;
    }

    private String uploadToFolder(String folderName, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > properties.maxSize()) {
            throw new IllegalArgumentException("Image is empty or exceeds the maximum allowed size");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are supported");
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        String extension = switch (normalizedType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "webp";
        };
        try {
            byte[] data = file.getBytes();
            if (!hasValidSignature(data, normalizedType)) {
                throw new IllegalArgumentException("Image content does not match its media type");
            }
            return objectStorage.upload(folderName + "/" + UUID.randomUUID() + "." + extension,
                    data, contentType, data.length).url();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read uploaded image", ex);
        }
    }

    private boolean hasValidSignature(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" ->
                    bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" ->
                    bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" ->
                    bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }
}
