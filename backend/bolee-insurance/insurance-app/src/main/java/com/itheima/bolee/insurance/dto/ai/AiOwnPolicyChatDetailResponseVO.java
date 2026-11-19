package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Customer-facing single policy result with no internal policy reference. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicyChatDetailResponseVO {

    private String status;
    private String asOf;
    private AiOwnPolicyChatItemVO policy;
}
