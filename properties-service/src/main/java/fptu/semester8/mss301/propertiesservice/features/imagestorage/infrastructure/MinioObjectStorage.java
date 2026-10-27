package fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * MinIO storage adapter theo đặc tả hệ thống.
 * Tương thích S3: MinIO cũng chấp nhận endpoint Cloudflare R2 qua pathStyleAccess.
 */
@Component
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient minioClient;
    private final ImageStorageProperties properties;
    private final String bucket;

    public MinioObjectStorage(MinioClient minioClient,
                               ImageStorageProperties properties,
                               @Value("${minio.bucket-name:}") String bucket) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.bucket = bucket;
    }

    @Override
    public StorageObject upload(String key, byte[] data, String contentType, long contentLength) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("MinIO bucket is not configured (minio.bucket-name)");
        }
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(data), contentLength, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload object to MinIO: " + e.getMessage(), e);
        }
        String baseUrl = properties.publicBaseUrl();
        String url = baseUrl == null || baseUrl.isBlank()
                ? key
                : baseUrl.replaceAll("/+$", "") + "/" + key;
        return new StorageObject(key, url, contentType, contentLength);
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete object from MinIO: " + e.getMessage(), e);
        }
    }
}
