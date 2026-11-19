package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sanitized transport error shared by customer and internal AI APIs. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiErrorDTO {
    private String code;
    private String message;
    private String traceId;
}
