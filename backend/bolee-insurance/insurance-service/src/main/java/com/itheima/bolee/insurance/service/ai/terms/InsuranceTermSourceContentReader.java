package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceContentReader {
    private final InsuranceTermObjectStorage objectStorage;
    private final InsuranceTermsStorageProperties properties;

    public InsuranceTermSourceContentReader(InsuranceTermObjectStorage objectStorage,
                                            InsuranceTermsStorageProperties properties) {
        this.objectStorage = objectStorage;
        this.properties = properties;
    }

    public InputStream openVerified(InsuranceTermSourceDO source) {
        verify(source);
        return objectStorage.open(source.getObjectKey());
    }

    public void verify(InsuranceTermSourceDO source) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        long copied = 0;
        try (InputStream input = objectStorage.open(source.getObjectKey())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    int singleByte = input.read();
                    if (singleByte < 0) break;
                    copied++;
                    if (copied > properties.getMaxFileSizeBytes()) {
                        throw new IllegalStateException("Stored source exceeds the configured file size limit");
                    }
                    digest.update((byte) singleByte);
                    continue;
                }
                copied += read;
                if (copied > properties.getMaxFileSizeBytes()) {
                    throw new IllegalStateException("Stored source exceeds the configured file size limit");
                }
                digest.update(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not verify insurance source document", exception);
        }
        if (source.getFileSize() == null || copied != source.getFileSize()
                || !toHex(digest.digest()).equalsIgnoreCase(source.getSha256())) {
            throw new IllegalStateException("Stored source integrity check failed");
        }
    }

    private String toHex(byte[] hash) {
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }
}
