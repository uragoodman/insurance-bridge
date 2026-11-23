package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MinioInsuranceTermObjectStorageTest {

    @Test
    public void uploadsDirectlyToProvisionedBucketWithoutListingBucketContents() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.putObject(any(PutObjectArgs.class))).thenReturn(null);
        InsuranceTermObjectStorage storage = new MinioInsuranceTermObjectStorage(client, properties());

        storage.put("terms/1.txt", new ByteArrayInputStream(new byte[]{1, 2}), 2,
                "text/plain", "hash");

        verify(client).putObject(any(PutObjectArgs.class));
        verifyNoMoreInteractions(client);
    }

    private InsuranceTermsStorageProperties properties() {
        InsuranceTermsStorageProperties properties = new InsuranceTermsStorageProperties();
        properties.setBucket("insurance-source-files-test");
        return properties;
    }
}
