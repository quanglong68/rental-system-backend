package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.contracttenant.entity.SignatureParty;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
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
 * Private MinIO storage for contract PDFs and signature images. Files are never exposed by a public
 * URL; callers stream them through permission-checked endpoints.
 */
@Slf4j
@Service
public class ContractDocumentStorage {

    private static final long MAX_SIGNATURE_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_SIGNATURE_TYPES = Set.of("image/jpeg", "image/png");

    private final MinioClient minioClient;
    private final String bucket;

    public ContractDocumentStorage(MinioClient minioClient, @Value("${contract.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public String uploadPdf(Long contractId, int version, byte[] pdf) {
        String objectKey = "contracts/%d/v%d.pdf".formatted(contractId, version);
        put(objectKey, pdf, "application/pdf", "hợp đồng PDF");
        return objectKey;
    }

    public String uploadSignatureImage(Long contractId, SignatureParty party, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn ảnh chữ ký để tải lên");
        }
        if (file.getSize() > MAX_SIGNATURE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh chữ ký tối đa 2 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SIGNATURE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh chữ ký chỉ chấp nhận định dạng JPEG hoặc PNG");
        }
        if (!magicMatches(contentType, file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nội dung tệp không khớp định dạng ảnh chữ ký");
        }
        String extension = "image/png".equals(contentType) ? "png" : "jpg";
        String prefix = "contracts/%d/signature-%s-".formatted(contractId, party.name().toLowerCase(Locale.ROOT));
        String objectKey = prefix + UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(in, file.getSize(), -1).contentType(contentType).build());
            return objectKey;
        } catch (Exception ex) {
            log.error("Không lưu được ảnh chữ ký của hợp đồng {} lên MinIO", contractId, ex);
            throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                    "Không thể lưu ảnh chữ ký lúc này, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public boolean isSignatureImageOf(Long contractId, SignatureParty party, String objectKey) {
        return objectKey != null && objectKey.startsWith(
                "contracts/%d/signature-%s-".formatted(contractId, party.name().toLowerCase(Locale.ROOT)));
    }

    public String uploadMoveOutRefundReceipt(Long moveOutId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn ảnh biên lai hoàn cọc để tải lên");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh biên lai tối đa 5 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SIGNATURE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Biên lai chỉ chấp nhận định dạng JPEG hoặc PNG");
        }
        if (!magicMatches(contentType, file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nội dung tệp không khớp định dạng ảnh");
        }
        String extension = "image/png".equals(contentType) ? "png" : "jpg";
        String objectKey = "move-outs/%d/refund-receipt-%s.%s".formatted(moveOutId, UUID.randomUUID(), extension);
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(in, file.getSize(), -1).contentType(contentType).build());
            return objectKey;
        } catch (Exception ex) {
            log.error("Không lưu được ảnh biên lai hoàn cọc cho yêu cầu dọn đi {} lên MinIO", moveOutId, ex);
            throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                    "Không thể lưu ảnh biên lai lúc này, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public Download open(String objectKey) {
        try {
            var stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            GetObjectResponse stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
            String contentType = stat.contentType() == null ? "application/octet-stream" : stat.contentType();
            return new Download(stream, contentType, stat.size(), filename(objectKey));
        } catch (Exception ex) {
            log.error("Không đọc được tài liệu hợp đồng {} từ MinIO", objectKey, ex);
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu hợp đồng đã lưu");
        }
    }

    public byte[] readBytes(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (Exception ex) {
            log.warn("Không đọc được ảnh chữ ký {} từ MinIO: {}", objectKey, ex.getMessage());
            return null;
        }
    }

    private void put(String objectKey, byte[] content, String contentType, String label) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType).build());
        } catch (Exception ex) {
            log.error("Không lưu được {} {} lên MinIO", label, objectKey, ex);
            throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                    "Không thể lưu tài liệu hợp đồng lúc này, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void ensureBucket() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private static String filename(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
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
            default -> false;
        };
    }

    public record Download(InputStream stream, String contentType, long size, String filename) {
    }
}
