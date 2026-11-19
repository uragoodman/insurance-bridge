package com.itheima.bolee.insurance.web;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.framework.commons.utils.SubjectContent;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceChannelAdminVO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceChannelUpsertDTO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestAdminVO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestStatusDTO;
import com.itheima.bolee.insurance.service.ai.CustomerServiceHandoffService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;

@Api(tags = "客服请求管理")
@RestController
@RequestMapping("customer-service-admin")
public class CustomerServiceAdminController {
    private final CustomerServiceHandoffService handoffService;

    public CustomerServiceAdminController(CustomerServiceHandoffService handoffService) {
        this.handoffService = handoffService;
    }

    @GetMapping("requests")
    @ApiOperation("查询当前企业客服请求队列")
    public ResponseResult<List<CustomerServiceRequestAdminVO>> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit, HttpServletResponse response) {
        requireOperator();
        response.setHeader("Cache-Control", "no-store, private");
        return ResponseResultBuild.successBuild(handoffService.listQueue(
                SubjectContent.getCompanyNo(), status, limit));
    }

    @PutMapping("requests/{requestId}/status")
    @ApiOperation("推进客服请求状态：待受理、受理中、已解决")
    public ResponseResult<Boolean> transition(@PathVariable Long requestId,
                                               @RequestBody CustomerServiceRequestStatusDTO request) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(handoffService.transition(SubjectContent.getCompanyNo(),
                user == null ? null : user.getId(), requestId, request == null ? null : request.getStatus()));
    }

    @GetMapping("channels")
    @ApiOperation("查询当前企业客服渠道配置")
    public ResponseResult<List<CustomerServiceChannelAdminVO>> channels(HttpServletResponse response) {
        requireOperator();
        response.setHeader("Cache-Control", "no-store, private");
        return ResponseResultBuild.successBuild(handoffService.listChannelsForAdmin(SubjectContent.getCompanyNo()));
    }

    @PutMapping("channels")
    @ApiOperation("按渠道编码新增或更新客服渠道配置")
    public ResponseResult<Boolean> upsertChannel(@RequestBody CustomerServiceChannelUpsertDTO request) {
        UserVO user = requireOperator();
        handoffService.upsertChannel(SubjectContent.getCompanyNo(), user == null ? null : user.getId(), request);
        return ResponseResultBuild.successBuild(Boolean.TRUE);
    }

    private UserVO requireOperator() {
        UserVO user = SubjectContent.getUserVO();
        if (user == null || !Boolean.FALSE.equals(user.getOnlyAuthenticate())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "customer-service management requires an authorized operator");
        }
        return user;
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ResponseResult<String>> handleAuthorizationFailure(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ResponseResult.<String>builder().code(exception.getStatus().value())
                        .msg("无权访问").data(exception.getReason()).operationTime(LocalDateTime.now()).build());
    }
}
