package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;

@Component
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class MinioInsuranceTermObjectStorage implements InsuranceTermObjectStorage {

    private final MinioClient minioClient;
    private final InsuranceTermsStorageProperties properties;

    public MinioInsuranceTermObjectStorage(MinioClient minioClient,
                                           InsuranceTermsStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, InputStream content, long size, String contentType, String sha256) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .userMetadata(Collections.singletonMap("sha256", sha256))
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store insurance source document", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read insurance source document", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
            return true;
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse() == null ? null : exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code)) {
                return false;
            }
            throw new IllegalStateException("Could not inspect insurance source document", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inspect insurance source document", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not remove insurance source document", exception);
        }
    }
}
