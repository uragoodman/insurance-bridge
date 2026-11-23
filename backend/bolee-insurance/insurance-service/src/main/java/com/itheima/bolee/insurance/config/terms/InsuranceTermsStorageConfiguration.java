package com.itheima.bolee.insurance.config.terms;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(InsuranceTermsStorageProperties.class)
public class InsuranceTermsStorageConfiguration {

    @Bean
    public MinioClient insuranceTermsMinioClient(InsuranceTermsStorageProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getBucket())
                || properties.getMaxFileSizeBytes() <= 0) {
            throw new IllegalStateException("Insurance terms MinIO configuration is incomplete");
        }
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
