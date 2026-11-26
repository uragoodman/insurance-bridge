package com.itheima.bolee.file.handler.minio;

import com.google.common.io.ByteStreams;
import com.itheima.bolee.file.handler.AbsFileStorageHandler;
import com.itheima.bolee.file.handler.FileStorageHandler;
import com.itheima.bolee.file.pojo.File;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service("minioFileStorageHandler")
@ConditionalOnProperty(prefix = "bolee.file.minio", name = "enabled", havingValue = "true")
public class MinioFileStorageHandler extends AbsFileStorageHandler implements FileStorageHandler {

    private static final String HEALTH_DISCLOSURE_PREFIX = "product-health-";
    private static final String PRODUCT_ASSET_PREFIX = "product-asset-";
    private static final String HEALTH_DOCUMENT_KEY = "product-health-[a-fA-F0-9-]+\\.pdf";
    private static final String PRODUCT_ASSET_KEY = "product-asset-[a-fA-F0-9-]+\\.(png|jpe?g|gif|webp)";
    private final MinioClient client;
    private final MinioFileStorageProperties properties;

    public MinioFileStorageHandler(MinioClient minioFileClient, MinioFileStorageProperties properties) {
        this.client = minioFileClient;
        this.properties = properties;
    }

    @Override
    public String uploadFile(String suffix, String filename, String bucketName, boolean autoCatalog,
                            InputStream inputStream) {
        String normalizedSuffix = suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
        BufferedInputStream contentStream = inputStream instanceof BufferedInputStream
                ? (BufferedInputStream) inputStream : new BufferedInputStream(inputStream);
        String contentType = validateAndGetContentType(normalizedSuffix, contentStream);
        String prefix = "application/pdf".equals(contentType) ? HEALTH_DISCLOSURE_PREFIX : PRODUCT_ASSET_PREFIX;
        String key = prefix + UUID.randomUUID() + normalizedSuffix;
        String bucket = resolveBucket(bucketName);
        try {
            ensureBucket(bucket);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(contentStream, -1, 10 * 1024 * 1024)
                    .contentType(contentType)
                    .build());
            return key;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not upload insurance product file to MinIO", exception);
        }
    }

    @Override
    public File initiateMultipartUpload(String suffix, String filename, String bucketName, boolean autoCatalog) {
        throw new UnsupportedOperationException("MinIO uploads use the bounded single-file endpoint");
    }

    @Override
    public String uploadPart(String upLoadId, String filename, int partNumber, long partSize,
                             String bucketName, InputStream inputStream) {
        throw new UnsupportedOperationException("Multipart upload is not enabled for MinIO product files");
    }

    @Override
    public String completeMultipartUpload(String upLoadId, List<String> partETags, String filename,
                                          String bucketName) {
        throw new UnsupportedOperationException("Multipart upload is not enabled for MinIO product files");
    }

    @Override
    public InputStream downloadFile(String bucketName, String pathUrl) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(resolveBucket(bucketName))
                    .object(requireSupportedProductKey(pathUrl)).build());
        } catch (Exception exception) {
            throw new IOException("Could not read insurance product file from MinIO", exception);
        }
    }

    @Override
    public void delete(String bucketName, String pathUrl) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(resolveBucket(bucketName))
                    .object(requireSupportedProductKey(pathUrl)).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not delete insurance product file from MinIO", exception);
        }
    }

    @Override
    public void deleteBatch(String bucketName, List<String> pathUrls) {
        if (pathUrls == null) return;
        for (String pathUrl : pathUrls) delete(bucketName, pathUrl);
    }

    @Override
    public String getFileContent(String bucketName, String pathUrl) throws IOException {
        try (InputStream input = downloadFile(bucketName, pathUrl)) {
            return new String(ByteStreams.toByteArray(input), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String resolveBucket(String bucketName) {
        String configured = StringUtils.hasText(bucketName) ? bucketName : properties.getBucket();
        if (!properties.getBucket().equals(configured)) {
            throw new IllegalArgumentException("MinIO file bucket is restricted to the configured insurance bucket");
        }
        return configured;
    }

    private String requireSupportedProductKey(String key) {
        if (!StringUtils.hasText(key) || !(key.matches(HEALTH_DOCUMENT_KEY) || key.matches(PRODUCT_ASSET_KEY))) {
            throw new IllegalArgumentException("Unsupported MinIO product file key");
        }
        return key;
    }

    private String validateAndGetContentType(String suffix, BufferedInputStream stream) {
        try {
            stream.mark(12);
            byte[] signature = new byte[12];
            int read = stream.read(signature);
            stream.reset();
            if (".pdf".equals(suffix) && hasPrefix(signature, read, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                return "application/pdf";
            }
            if (".png".equals(suffix) && read >= 8
                    && signature[0] == (byte) 0x89 && signature[1] == 'P' && signature[2] == 'N'
                    && signature[3] == 'G' && signature[4] == 0x0D && signature[5] == 0x0A
                    && signature[6] == 0x1A && signature[7] == 0x0A) {
                return "image/png";
            }
            if ((".jpg".equals(suffix) || ".jpeg".equals(suffix)) && read >= 3
                    && signature[0] == (byte) 0xFF && signature[1] == (byte) 0xD8
                    && signature[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            if (".gif".equals(suffix) && read >= 6
                    && (new String(signature, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("GIF87a")
                    || new String(signature, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("GIF89a"))) {
                return "image/gif";
            }
            if (".webp".equals(suffix) && read >= 12
                    && new String(signature, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(signature, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
                return "image/webp";
            }
            throw new IllegalArgumentException("Unsupported or invalid MinIO product file; upload a valid PDF or image");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not validate MinIO product file", exception);
        }
    }

    private boolean hasPrefix(byte[] value, int length, byte[] prefix) {
        if (length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
    }
}
