package com.itheima.bolee.file.web;

import com.itheima.bolee.file.adapter.FileStorageAdapter;
import com.itheima.bolee.file.handler.minio.MinioFileStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Public inline delivery for product health PDFs and product display images. */
@RestController
@RequestMapping("file/public-minio")
@ConditionalOnProperty(prefix = "bolee.file.minio", name = "enabled", havingValue = "true")
public class MinioProductDocumentController {

    private static final Pattern HEALTH_DOCUMENT_KEY = Pattern.compile("product-health-[a-fA-F0-9-]+\\.pdf");
    private static final Pattern PRODUCT_ASSET_KEY = Pattern.compile("product-asset-[a-fA-F0-9-]+\\.(png|jpe?g|gif|webp)");
    private static final Map<String, MediaType> SUPPORTED_MEDIA_TYPES;

    static {
        Map<String, MediaType> mediaTypes = new HashMap<>();
        mediaTypes.put("pdf", MediaType.APPLICATION_PDF);
        mediaTypes.put("png", MediaType.IMAGE_PNG);
        mediaTypes.put("jpg", MediaType.IMAGE_JPEG);
        mediaTypes.put("jpeg", MediaType.IMAGE_JPEG);
        mediaTypes.put("gif", MediaType.IMAGE_GIF);
        mediaTypes.put("webp", MediaType.parseMediaType("image/webp"));
        SUPPORTED_MEDIA_TYPES = Collections.unmodifiableMap(mediaTypes);
    }

    private final FileStorageAdapter storageAdapter;
    private final MinioFileStorageProperties properties;

    public MinioProductDocumentController(FileStorageAdapter storageAdapter,
                                          MinioFileStorageProperties properties) {
        this.storageAdapter = storageAdapter;
        this.properties = properties;
    }

    @GetMapping(value = "/{objectKey:.+}")
    public ResponseEntity<StreamingResponseBody> viewProductFile(@PathVariable String objectKey) {
        if (!HEALTH_DOCUMENT_KEY.matcher(objectKey).matches()
                && !PRODUCT_ASSET_KEY.matcher(objectKey).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        final InputStream content;
        try {
            content = storageAdapter.downloadFile("MINIO", properties.getBucket(), objectKey);
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product file not found");
        }
        StreamingResponseBody body = output -> {
            try (InputStream input = content) {
                input.transferTo(output);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(SUPPORTED_MEDIA_TYPES.get(extension));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectKey + "\"");
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<StreamingResponseBody>(body, headers, HttpStatus.OK);
    }
}
