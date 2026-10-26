package fptu.semester8.mss301.propertiesservice.features.imagestorage.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Component
public class R2ObjectStorage implements ObjectStorage {
    private final S3Client s3Client;
    private final ImageStorageProperties properties;
    private final String bucket;

    public R2ObjectStorage(S3Client s3Client, ImageStorageProperties properties,
                           @Value("${cloudflare.r2.bucket-name:}") String bucket) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.bucket = bucket;
    }

    @Override
    public StorageObject upload(String key, byte[] data, String contentType, long contentLength) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Cloudflare R2 bucket is not configured");
        }
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(contentType).contentLength(contentLength).build(), RequestBody.fromBytes(data));
        String baseUrl = properties.publicBaseUrl();
        String url = baseUrl == null || baseUrl.isBlank() ? key : baseUrl.replaceAll("/+$", "") + "/" + key;
        return new StorageObject(key, url, contentType, contentLength);
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
