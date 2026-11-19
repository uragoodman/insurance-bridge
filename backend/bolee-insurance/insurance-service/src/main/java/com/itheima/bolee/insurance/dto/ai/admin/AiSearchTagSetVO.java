package com.itheima.bolee.insurance.dto.ai.admin;

import java.util.List;

public class AiSearchTagSetVO {
    private Long productId;
    private Integer version;
    private String sourceType;
    private String modelId;
    private String promptVersion;
    private List<AiSearchTagDTO> tags;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public List<AiSearchTagDTO> getTags() { return tags; }
    public void setTags(List<AiSearchTagDTO> tags) { this.tags = tags; }
}
