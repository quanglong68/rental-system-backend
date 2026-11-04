package com.rental.asset.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    // Ky presigned URL bang endpoint public de chu ky SigV4 khop Host trinh duyet dung;
    // region co dinh nen khong can goi mang toi MinIO luc ky.
    @Bean
    public MinioClient minioClient(
            @Value("${asset.minio.public-endpoint}") String endpoint,
            @Value("${asset.minio.region}") String region,
            @Value("${asset.minio.access-key}") String accessKey,
            @Value("${asset.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }
}
