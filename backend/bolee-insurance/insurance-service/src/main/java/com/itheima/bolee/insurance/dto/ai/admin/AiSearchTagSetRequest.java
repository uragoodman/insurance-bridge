package com.itheima.bolee.insurance.dto.ai.admin;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

public class AiSearchTagSetRequest {
    @NotNull
    @Size(max = 50)
    @Valid
    private List<AiSearchTagDTO> tags;

    public List<AiSearchTagDTO> getTags() { return tags; }
    public void setTags(List<AiSearchTagDTO> tags) { this.tags = tags; }
}
