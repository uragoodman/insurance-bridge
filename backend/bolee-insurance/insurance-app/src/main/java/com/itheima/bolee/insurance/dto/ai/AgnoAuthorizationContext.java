package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/** Server-side grant bound to one authenticated user and AgentOS session. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgnoAuthorizationContext {

    private String userId;
    private String sessionId;
    private Set<String> scopes;
    private long expiresAtEpochSecond;
}
