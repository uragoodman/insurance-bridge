package com.itheima.bolee.insurance.dto.ai.terms;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class InsuranceTermSourceWithdrawRequest {
    @NotBlank
    @Size(max = 1000)
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
