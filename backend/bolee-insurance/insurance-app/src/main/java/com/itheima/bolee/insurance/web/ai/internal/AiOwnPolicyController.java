package com.itheima.bolee.insurance.web.ai.internal;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicySummaryResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyDetailResponseVO;
import com.itheima.bolee.insurance.service.ai.AgnoAuthorizationContextService;
import com.itheima.bolee.insurance.service.ai.AiOwnPolicyQueryService;
import com.itheima.bolee.insurance.service.ai.AgentScope;
import com.itheima.bolee.insurance.service.ai.AgnoScope;
import com.itheima.bolee.insurance.dto.ai.AgnoAuthorizationContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Private read-only Agent endpoint; identity comes only from Java's opaque grant. */
@Api(tags = "AI 客服内部本人保单")
@RestController
@RequestMapping("/internal/agent-tools/v1/policies")
@AgentScope(AgnoScope.POLICY_OWN_READ)
public class AiOwnPolicyController {

    private final AgnoAuthorizationContextService authorizationContextService;
    private final AiOwnPolicyQueryService queryService;

    public AiOwnPolicyController(AgnoAuthorizationContextService authorizationContextService,
                                 AiOwnPolicyQueryService queryService) {
        this.authorizationContextService = authorizationContextService;
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    @ApiOperation("只读查询当前登录客户作为投保人的有效保单摘要")
    public ResponseResult<AiOwnPolicySummaryResponseVO> summary(
            @RequestHeader("X-Agent-Authorization-Context") String authorizationContextToken) {
        AgnoAuthorizationContext context = authorizationContextService.require(
                authorizationContextToken, AgnoScope.POLICY_OWN_READ);
        return ResponseResultBuild.successBuild(queryService.findOwnPolicies(context.getUserId()));
    }

    @GetMapping("/{policyId}")
    @ApiOperation("只读查询当前登录客户作为投保人的单张有效保单")
    public ResponseResult<AiOwnPolicyDetailResponseVO> detail(
            @RequestHeader("X-Agent-Authorization-Context") String authorizationContextToken,
            @PathVariable("policyId") String policyId) {
        AgnoAuthorizationContext context = authorizationContextService.require(
                authorizationContextToken, AgnoScope.POLICY_OWN_READ);
        return ResponseResultBuild.successBuild(
                queryService.findOwnPolicy(context.getUserId(), policyId));
    }
}
