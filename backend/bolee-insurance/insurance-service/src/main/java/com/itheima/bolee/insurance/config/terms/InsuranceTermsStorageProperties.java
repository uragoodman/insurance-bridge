package com.itheima.bolee.insurance.config.terms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "insurance.terms.storage")
public class InsuranceTermsStorageProperties {

    private boolean enabled;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "insurance-source-files";
    private long maxFileSizeBytes = 52428800L;
    private boolean syntheticSourcesEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public boolean isSyntheticSourcesEnabled() { return syntheticSourcesEnabled; }
    public void setSyntheticSourcesEnabled(boolean syntheticSourcesEnabled) {
        this.syntheticSourcesEnabled = syntheticSourcesEnabled;
    }
}
