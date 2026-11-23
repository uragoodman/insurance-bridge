package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import io.minio.MinioClient;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;

/** Exercises the configured local MinIO bucket when INSURANCE_TERMS_MINIO_* are supplied. */
public class MinioInsuranceTermObjectStorageIntegrationTest {

    @Test
    public void uploadsStreamsBackAndDeletesAnIsolatedObject() throws Exception {
        String endpoint = System.getenv("INSURANCE_TERMS_MINIO_ENDPOINT");
        String accessKey = System.getenv("INSURANCE_TERMS_MINIO_ACCESS_KEY");
        String secretKey = System.getenv("INSURANCE_TERMS_MINIO_SECRET_KEY");
        String bucket = System.getenv("INSURANCE_TERMS_MINIO_BUCKET");
        Assume.assumeTrue("INSURANCE_TERMS_MINIO_* is required for MinIO integration testing",
                hasText(endpoint) && hasText(accessKey) && hasText(secretKey) && hasText(bucket));

        InsuranceTermsStorageProperties properties = new InsuranceTermsStorageProperties();
        properties.setEndpoint(endpoint);
        properties.setAccessKey(accessKey);
        properties.setSecretKey(secretKey);
        properties.setBucket(bucket);
        MinioClient client = MinioClient.builder().endpoint(endpoint)
                .credentials(accessKey, secretKey).build();
        InsuranceTermObjectStorage storage = new MinioInsuranceTermObjectStorage(client, properties);
        String objectKey = "products/test-integration/" + UUID.randomUUID() + ".txt";
        byte[] payload = "insurance-term-storage-integration".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sha256 = toHex(MessageDigest.getInstance("SHA-256").digest(payload));
        try {
            storage.put(objectKey, new ByteArrayInputStream(payload), payload.length, "text/plain", sha256);
            try (InputStream input = storage.open(objectKey); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                assertArrayEquals(payload, output.toByteArray());
            }
        } finally {
            storage.delete(objectKey);
        }
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    private String toHex(byte[] hash) {
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
