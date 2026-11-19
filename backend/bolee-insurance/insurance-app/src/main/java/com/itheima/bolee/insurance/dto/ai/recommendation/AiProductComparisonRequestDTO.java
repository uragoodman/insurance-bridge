package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class AiProductComparisonRequestDTO {
    @NotBlank
    private String requestId;

    @NotBlank
    private String categoryId;

    @NotNull
    @Size(min = 2, max = 10)
    private List<@NotBlank String> productIds;
}
