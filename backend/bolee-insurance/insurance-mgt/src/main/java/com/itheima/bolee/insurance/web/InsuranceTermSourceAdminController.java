package com.itheima.bolee.insurance.web;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.framework.commons.utils.SubjectContent;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourcePageVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceReviewRequest;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceAuditVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceUpdateRequest;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceWithdrawRequest;
import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermSourceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Api(tags = "保险条款来源管理")
@RestController
@RequestMapping("insurance-terms/sources")
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceAdminController {

    private final InsuranceTermSourceService sourceService;

    public InsuranceTermSourceAdminController(InsuranceTermSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    @ApiOperation("分页查询保险条款来源")
    public ResponseResult<InsuranceTermSourcePageVO> list(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "profileVersionId", required = false) Long profileVersionId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletResponse response) {
        requireOperator();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        return ResponseResultBuild.successBuild(sourceService.list(productId, profileVersionId, status, page, pageSize));
    }

    @GetMapping("/products/{productId}/versions")
    @ApiOperation("查询产品可维护条款的档案版本")
    public ResponseResult<List<com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermProductVersionVO>> versions(
            @PathVariable Long productId, HttpServletResponse response) {
        requireOperator();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        return ResponseResultBuild.successBuild(sourceService.listProductVersionsForAdmin(productId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("上传产品条款来源文件并登记为草稿")
    public ResponseResult<InsuranceTermSourceVO> upload(
            @RequestParam("productId") Long productId,
            @RequestParam("profileVersionId") Long profileVersionId,
            @RequestParam("sourceType") String sourceType,
            @RequestParam("title") String title,
            @RequestParam(value = "sourceUri", required = false) String sourceUri,
            @RequestParam(value = "effectiveFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam(value = "effectiveTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo,
            @RequestParam("file") MultipartFile file) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(sourceService.upload(
                productId, profileVersionId, sourceType, title, sourceUri,
                effectiveFrom, effectiveTo, file, user.getId()));
    }

    @GetMapping("/{sourceId}")
    @ApiOperation("查询条款来源和审核发布状态")
    public ResponseResult<InsuranceTermSourceVO> detail(@PathVariable Long sourceId,
                                                         HttpServletResponse response) {
        requireOperator();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        return ResponseResultBuild.successBuild(sourceService.get(sourceId));
    }

    @GetMapping("/{sourceId}/audit")
    @ApiOperation("查询条款来源的追加式审核与发布审计记录")
    public ResponseResult<List<InsuranceTermSourceAuditVO>> audit(@PathVariable Long sourceId,
                                                                  HttpServletResponse response) {
        requireOperator();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        return ResponseResultBuild.successBuild(sourceService.listAudit(sourceId));
    }

    @GetMapping("/{sourceId}/file")
    @ApiOperation("私有下载条款来源原件")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long sourceId) {
        requireOperator();
        InsuranceTermSourceVO source = sourceService.get(sourceId);
        java.io.InputStream content = sourceService.download(sourceId);
        StreamingResponseBody body = output -> {
            try (java.io.InputStream input = content) {
                input.transferTo(output);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(source.getFileSize());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(source.getOriginalFileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<StreamingResponseBody>(body, headers, HttpStatus.OK);
    }

    @PutMapping("/{sourceId}")
    @ApiOperation("修改草稿或驳回资料的标题与有效期")
    public ResponseResult<InsuranceTermSourceVO> update(
            @PathVariable Long sourceId, @Valid @RequestBody InsuranceTermSourceUpdateRequest request) {
        UserVO user = requireOperator();
        sourceService.updateDraft(sourceId, request.getTitle(), request.getSourceUri(),
                request.getEffectiveFrom(), request.getEffectiveTo(), user.getId());
        return ResponseResultBuild.successBuild(sourceService.get(sourceId));
    }

    @PostMapping("/{sourceId}/submit-review")
    @ApiOperation("提交条款来源审核")
    public ResponseResult<InsuranceTermSourceVO> submitReview(@PathVariable Long sourceId) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(sourceService.submitForReview(sourceId, user.getId()));
    }

    @PostMapping("/{sourceId}/review")
    @ApiOperation("审核通过或驳回条款来源")
    public ResponseResult<InsuranceTermSourceVO> review(
            @PathVariable Long sourceId, @Valid @RequestBody InsuranceTermSourceReviewRequest request) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(sourceService.review(sourceId,
                Boolean.TRUE.equals(request.getApproved()), request.getNote(), user.getId()));
    }

    @PostMapping("/{sourceId}/publish")
    @ApiOperation("发布已审核的正式产品条款")
    public ResponseResult<InsuranceTermSourceVO> publish(@PathVariable Long sourceId) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(sourceService.publish(sourceId, user.getId()));
    }

    @PostMapping("/{sourceId}/withdraw")
    @ApiOperation("撤回已发布条款")
    public ResponseResult<InsuranceTermSourceVO> withdraw(
            @PathVariable Long sourceId, @Valid @RequestBody InsuranceTermSourceWithdrawRequest request) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(sourceService.withdraw(sourceId, request.getReason(), user.getId()));
    }

    @PostMapping("/{sourceId}/retry-index-sync")
    @ApiOperation("重试失败的条款索引或撤回同步")
    public ResponseResult<InsuranceTermSourceVO> retryIndexSync(@PathVariable Long sourceId) {
        UserVO user = requireOperator();
        return ResponseResultBuild.successBuild(sourceService.retryIndexSync(sourceId, user.getId()));
    }

    @DeleteMapping("/{sourceId}")
    @ApiOperation("删除草稿或已驳回来源及其私有原件")
    public ResponseResult<Boolean> delete(@PathVariable Long sourceId) {
        UserVO user = requireOperator();
        sourceService.deleteDraft(sourceId, user.getId());
        return ResponseResultBuild.successBuild(Boolean.TRUE);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseResult<String>> handleStatusException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ResponseResult.<String>builder().code(exception.getStatus().value())
                        .msg(exception.getStatus() == HttpStatus.FORBIDDEN ? "无权访问" : "请求失败")
                        .data(exception.getReason()).operationTime(LocalDateTime.now()).build());
    }

    private UserVO requireOperator() {
        UserVO user = SubjectContent.getUserVO();
        if (user == null || user.getId() == null || !Boolean.FALSE.equals(user.getOnlyAuthenticate())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "insurance term management requires an authorized operator");
        }
        return user;
    }

}
