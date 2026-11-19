package com.itheima.bolee.insurance.dto.ai.admin;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AiRecommendationToggleDTO {
    @NotNull
    private Boolean enabled;
}
