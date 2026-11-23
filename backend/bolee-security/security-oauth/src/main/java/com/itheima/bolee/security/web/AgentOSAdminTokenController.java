package com.itheima.bolee.security.web;

import com.itheima.bolee.framework.commons.constant.security.SecurityConstant;
import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.enums.basic.BaseEnum;
import com.itheima.bolee.framework.commons.enums.security.AuthEnum;
import com.itheima.bolee.security.service.AgentOSAdminTokenService;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Collections;

/** Issues short-lived AgentOS management tokens to authorized console users. */
@RestController
@RequestMapping("/agentos")
public class AgentOSAdminTokenController {

    @Autowired
    private AgentOSAdminTokenService agentOSAdminTokenService;

    @ApiOperation(value = "换取 AgentOS 管理令牌", notes = "仅允许管理端已认证用户按角色换取短时令牌")
    @PostMapping(value = "/admin-token", produces = "application/json")
    public ResponseEntity<ResponseResult<Map<String, Object>>> issueAdminToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = SecurityConstant.USER_TOKEN, required = false) String userToken) {
        try {
            Map<String, Object> response = agentOSAdminTokenService.exchange(authorization, userToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(ResponseResultBuild.build(BaseEnum.SUCCEED, response));
        } catch (ResponseStatusException exception) {
            HttpStatus status = exception.getStatus();
            AuthEnum result = status == HttpStatus.UNAUTHORIZED
                    ? AuthEnum.NEED_LOGIN : AuthEnum.AUTH_FAIL;
            return ResponseEntity.status(status)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(ResponseResultBuild.build(result, Collections.emptyMap()));
        }
    }
}
