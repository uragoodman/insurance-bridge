package com.itheima.bolee.file.handler.minio;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "bolee.file.minio", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MinioFileStorageProperties.class)
public class MinioFileStorageConfiguration {

    @Bean
    public MinioClient minioFileClient(MinioFileStorageProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("File MinIO configuration is incomplete");
        }
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
