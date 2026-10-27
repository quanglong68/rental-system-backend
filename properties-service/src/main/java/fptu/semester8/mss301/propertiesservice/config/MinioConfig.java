package fptu.semester8.mss301.propertiesservice.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình MinIO client theo đặc tả hệ thống (thay thế Cloudflare R2 / AWS S3 SDK).
 * Tương thích S3: MinioClient hỗ trợ pathStyle và custom endpoint cho R2/on-prem.
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:}")
    private String endpoint;

    @Value("${minio.access-key:}")
    private String accessKey;

    @Value("${minio.secret-key:}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint.isBlank() ? "http://localhost:9000" : endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
