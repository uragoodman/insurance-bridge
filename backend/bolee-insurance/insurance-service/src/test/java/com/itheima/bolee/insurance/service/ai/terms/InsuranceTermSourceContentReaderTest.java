package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InsuranceTermSourceContentReaderTest {
    @Test
    public void verificationMakesProgressWhenBulkReadReturnsZero() throws Exception {
        byte[] content = "term document".getBytes("UTF-8");
        InsuranceTermObjectStorage storage = mock(InsuranceTermObjectStorage.class);
        when(storage.open("terms/source.pdf")).thenReturn(new ZeroOnceInputStream(content));
        InsuranceTermsStorageProperties properties = new InsuranceTermsStorageProperties();
        properties.setMaxFileSizeBytes(1024);
        InsuranceTermSourceContentReader reader = new InsuranceTermSourceContentReader(storage, properties);
        InsuranceTermSourceDO source = new InsuranceTermSourceDO();
        source.setObjectKey("terms/source.pdf");
        source.setFileSize((long) content.length);
        source.setSha256(toHex(MessageDigest.getInstance("SHA-256").digest(content)));

        reader.verify(source);
    }

    private String toHex(byte[] hash) {
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static final class ZeroOnceInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean returnZero = true;

        private ZeroOnceInputStream(byte[] content) {
            delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (returnZero) {
                returnZero = false;
                return 0;
            }
            return delegate.read(buffer, offset, length);
        }

        @Override
        public int read() {
            return delegate.read();
        }
    }
}
