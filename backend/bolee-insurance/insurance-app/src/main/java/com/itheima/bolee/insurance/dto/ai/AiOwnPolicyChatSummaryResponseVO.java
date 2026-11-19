package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Customer-facing policy summary with a strict field allowlist. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicyChatSummaryResponseVO {

    private String status;
    private String asOf;
    private List<AiOwnPolicyChatItemVO> policies;
    private int returnedCount;
    private boolean truncated;
}
