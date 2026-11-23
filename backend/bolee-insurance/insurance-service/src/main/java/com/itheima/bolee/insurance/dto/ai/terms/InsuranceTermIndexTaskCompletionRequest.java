package com.itheima.bolee.insurance.dto.ai.terms;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class InsuranceTermIndexTaskCompletionRequest {
    @NotBlank
    @Size(max = 64)
    private String leaseToken;
    @NotBlank
    @Size(max = 64)
    private String sourceSha256;

    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String sourceSha256) { this.sourceSha256 = sourceSha256; }
}
