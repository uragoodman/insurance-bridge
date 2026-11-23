package com.itheima.bolee.insurance.dto.ai.terms;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class InsuranceTermProductVersionVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long profileVersionId;
    private Integer productVersion;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    public Long getProfileVersionId() { return profileVersionId; }
    public void setProfileVersionId(Long profileVersionId) { this.profileVersionId = profileVersionId; }
    public Integer getProductVersion() { return productVersion; }
    public void setProductVersion(Integer productVersion) { this.productVersion = productVersion; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
}
