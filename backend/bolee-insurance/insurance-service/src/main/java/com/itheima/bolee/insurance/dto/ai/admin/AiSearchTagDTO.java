package com.itheima.bolee.insurance.dto.ai.admin;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AiSearchTagDTO {
    @NotBlank
    @Size(max = 24)
    private String tagType;
    @NotBlank
    @Size(max = 128)
    private String tagValue;
    @Size(max = 512)
    private String evidence;

    public String getTagType() { return tagType; }
    public void setTagType(String tagType) { this.tagType = tagType; }
    public String getTagValue() { return tagValue; }
    public void setTagValue(String tagValue) { this.tagValue = tagValue; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
}
