package com.itheima.bolee.insurance.web.ai.internal;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermIndexTaskCompletionRequest;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermIndexTaskFailureRequest;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermIndexTaskHeartbeatRequest;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermIndexTaskVO;
import com.itheima.bolee.insurance.service.ai.AgentIndexScope;
import com.itheima.bolee.insurance.service.ai.AgnoIndexScope;
import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermIndexTaskService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;

/** OAuth service-only task contract for the Python term indexer. */
@Api(tags = "Agent 条款索引任务")
@RestController
@RequestMapping("/internal/agent-index/v1/tasks")
@AgentIndexScope(AgnoIndexScope.PRODUCT_TERMS_INDEX)
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermIndexTaskController {
    private final InsuranceTermIndexTaskService taskService;

    public InsuranceTermIndexTaskController(InsuranceTermIndexTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/claim")
    @ApiOperation("领取一个待处理条款索引/撤回任务")
    public ResponseEntity<InsuranceTermIndexTaskVO> claim() {
        InsuranceTermIndexTaskVO task = taskService.claimNext();
        return task == null ? ResponseEntity.noContent().build() : ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()).body(task);
    }

    @PostMapping("/{taskId}/complete")
    @ApiOperation("按原件 SHA-256 确认索引或撤回任务完成")
    public ResponseResult<Boolean> complete(@PathVariable Long taskId,
                                            @Valid @RequestBody InsuranceTermIndexTaskCompletionRequest request) {
        taskService.complete(taskId, request.getLeaseToken(), request.getSourceSha256());
        return ResponseResultBuild.successBuild(Boolean.TRUE);
    }

    @PostMapping("/{taskId}/fail")
    @ApiOperation("记录索引任务失败并按退避策略重试")
    public ResponseResult<Boolean> fail(@PathVariable Long taskId,
                                        @Valid @RequestBody InsuranceTermIndexTaskFailureRequest request) {
        taskService.fail(taskId, request.getLeaseToken(), request.getReason(), request.getRetryable());
        return ResponseResultBuild.successBuild(Boolean.TRUE);
    }

    @PostMapping("/{taskId}/heartbeat")
    @ApiOperation("续租当前索引任务，避免长文档解析期间重复领取")
    public ResponseResult<Boolean> heartbeat(@PathVariable Long taskId,
                                             @Valid @RequestBody InsuranceTermIndexTaskHeartbeatRequest request) {
        taskService.heartbeat(taskId, request.getLeaseToken());
        return ResponseResultBuild.successBuild(Boolean.TRUE);
    }

    @org.springframework.web.bind.annotation.GetMapping("/{taskId}/content")
    @ApiOperation("下载当前租约内索引任务的原始文件")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable Long taskId,
                                          @RequestHeader("X-Task-Lease-Token") String leaseToken) {
        InsuranceTermIndexTaskVO task = taskService.getLeasedTask(taskId, leaseToken);
        java.io.InputStream content = taskService.downloadTaskContent(taskId, leaseToken);
        StreamingResponseBody body = output -> {
            try (java.io.InputStream input = content) {
                input.transferTo(output);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(task.getFileSize());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<StreamingResponseBody>(body, headers, HttpStatus.OK);
    }
}
