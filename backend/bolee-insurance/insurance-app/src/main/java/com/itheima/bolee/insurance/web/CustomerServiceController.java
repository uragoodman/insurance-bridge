package com.itheima.bolee.insurance.web;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.framework.commons.utils.SubjectContent;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceChannelVO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestCreateDTO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestVO;
import com.itheima.bolee.insurance.service.ai.CustomerServiceHandoffService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Api(tags = "AI 客服转人工")
@RestController
@RequestMapping("customer-service")
public class CustomerServiceController {
    private final CustomerServiceHandoffService handoffService;

    public CustomerServiceController(CustomerServiceHandoffService handoffService) {
        this.handoffService = handoffService;
    }

    @GetMapping("channels")
    @ApiOperation("查询当前企业开放的客服渠道")
    public ResponseResult<List<CustomerServiceChannelVO>> channels(HttpServletResponse response) {
        requireCustomer();
        noStore(response);
        return ResponseResultBuild.successBuild(handoffService.listAvailableChannels(SubjectContent.getCompanyNo()));
    }

    @PostMapping("requests")
    @ApiOperation(value = "提交客服请求", notes = "客户身份及企业从登录上下文获取，支持 clientRequestId 幂等重放")
    public ResponseResult<CustomerServiceRequestVO> create(@RequestBody CustomerServiceRequestCreateDTO request) {
        UserVO user = requireCustomer();
        return ResponseResultBuild.successBuild(handoffService.create(
                SubjectContent.getCompanyNo(), user.getId(), request));
    }

    @GetMapping("requests")
    @ApiOperation("查询当前登录客户的客服请求")
    public ResponseResult<List<CustomerServiceRequestVO>> mine(
            @RequestParam(defaultValue = "20") int limit, HttpServletResponse response) {
        noStore(response);
        UserVO user = requireCustomer();
        return ResponseResultBuild.successBuild(handoffService.listMine(
                SubjectContent.getCompanyNo(), user.getId(), limit));
    }

    @GetMapping("requests/{requestId}")
    @ApiOperation("查询本人单个客服请求")
    public ResponseResult<CustomerServiceRequestVO> getMine(@PathVariable Long requestId,
                                                             HttpServletResponse response) {
        noStore(response);
        UserVO user = requireCustomer();
        return ResponseResultBuild.successBuild(handoffService.getMine(
                SubjectContent.getCompanyNo(), user.getId(), requestId));
    }

    @PostMapping("requests/{requestId}/cancel")
    @ApiOperation("取消尚未受理的本人客服请求")
    public ResponseResult<Boolean> cancel(@PathVariable Long requestId) {
        UserVO user = requireCustomer();
        return ResponseResultBuild.successBuild(handoffService.cancelMine(
                SubjectContent.getCompanyNo(), user.getId(), requestId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseResult<String>> handleAuthorizationFailure(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ResponseResult.<String>builder().code(exception.getStatus().value())
                        .msg("需要登录认证").data(exception.getReason()).operationTime(LocalDateTime.now()).build());
    }

    private UserVO requireCustomer() {
        UserVO user = SubjectContent.getUserVO();
        if (user == null || user.getId() == null || user.getId() <= 0
                || SubjectContent.getCompanyNo() == null || SubjectContent.getCompanyNo().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated customer is required");
        }
        return user;
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
    }
}
