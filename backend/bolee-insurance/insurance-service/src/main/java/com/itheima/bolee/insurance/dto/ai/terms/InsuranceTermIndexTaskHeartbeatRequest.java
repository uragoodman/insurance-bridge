package com.itheima.bolee.insurance.dto.ai.terms;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class InsuranceTermIndexTaskHeartbeatRequest {
    @NotBlank
    @Size(max = 64)
    private String leaseToken;

    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
}
