package com.itheima.bolee.insurance.service.ai.terms;

public interface InsuranceTermObjectStorage {

    void put(String objectKey, java.io.InputStream content, long size, String contentType, String sha256);

    java.io.InputStream open(String objectKey);

    void delete(String objectKey);
}
