package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu ảnh CCCD trong bucket private; chỉ tải qua endpoint proxy có kiểm quyền,
 * không phát presigned URL công khai.
 */
@Slf4j
@Service
public class IdentityFileStorage {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    private final MinioClient minioClient;
    private final String bucket;

    public IdentityFileStorage(MinioClient minioClient, @Value("${booking.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public String upload(Long customerId, String side, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn ảnh CCCD để tải lên");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh CCCD tối đa 5 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ảnh CCCD chỉ chấp nhận định dạng JPEG, PNG hoặc PDF");
        }
        if (!magicMatches(contentType, file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Nội dung tệp không khớp định dạng ảnh/PDF đã chọn");
        }

        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "application/pdf" -> "pdf";
            default -> "jpg";
        };
        String objectKey = "identity/%d/%s-%s.%s".formatted(customerId, side.toLowerCase(Locale.ROOT),
                UUID.randomUUID(), extension);
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Không lưu được ảnh CCCD lên MinIO", ex);
            throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                    "Không thể lưu ảnh CCCD lúc này, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public Download open(String objectKey) {
        try {
            var stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            GetObjectResponse stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
            String contentType = stat.contentType() == null ? "application/octet-stream" : stat.contentType();
            return new Download(stream, contentType, stat.size(), objectKey.substring(objectKey.lastIndexOf('/') + 1));
        } catch (Exception ex) {
            log.error("Không đọc được ảnh CCCD {} từ MinIO", objectKey, ex);
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ảnh CCCD đã lưu");
        }
    }

    public record Download(java.io.InputStream stream, String contentType, long size, String filename) {}

    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static boolean magicMatches(String contentType, MultipartFile file) {
        byte[] header = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            if (read < 4) return false;
        } catch (IOException ex) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
            case "image/png" -> (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "application/pdf" -> header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
            default -> false;
        };
    }
}
