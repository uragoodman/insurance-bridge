package com.itheima.bolee.insurance.dto.ai.terms;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class InsuranceTermIndexTaskFailureRequest {
    @NotBlank
    @Size(max = 64)
    private String leaseToken;
    @NotBlank
    @Size(max = 1000)
    private String reason;
    private Boolean retryable = Boolean.TRUE;

    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }
}
