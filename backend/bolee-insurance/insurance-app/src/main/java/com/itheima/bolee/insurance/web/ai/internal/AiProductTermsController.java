package com.itheima.bolee.insurance.web.ai.internal;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceVO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermProductVersionVO;
import com.itheima.bolee.insurance.service.ai.AgnoScope;
import com.itheima.bolee.insurance.service.ai.AgentScope;
import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermSourceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/** Read-only Java authority boundary for approved product term sources. */
@Api(tags = "AI 客服内部保险条款来源")
@RestController
@RequestMapping("/internal/agent-tools/v1/insurance-terms")
@AgentScope(AgnoScope.PRODUCT_TERMS_READ)
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class AiProductTermsController {

    private final InsuranceTermSourceService sourceService;

    public AiProductTermsController(InsuranceTermSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping("/products/{productId}/sources")
    @ApiOperation("查询指定产品与可选档案版本下当前有效的已发布正式条款来源")
    public ResponseResult<List<InsuranceTermSourceVO>> listPublished(
            @PathVariable Long productId,
            @RequestParam(value = "profileVersionId", required = false) Long profileVersionId) {
        return ResponseResultBuild.successBuild(sourceService.listActivePublished(productId, profileVersionId));
    }

    @GetMapping("/products/{productId}/versions")
    @ApiOperation("查询该产品具有已发布、已索引正式条款的当前有效档案版本")
    public ResponseResult<List<InsuranceTermProductVersionVO>> listPublishedVersions(
            @PathVariable Long productId) {
        return ResponseResultBuild.successBuild(sourceService.listActivePublishedVersions(productId));
    }

    @GetMapping("/products/{productId}/versions/{profileVersionId}/sources/{sourceId}/content")
    @ApiOperation("读取指定产品版本已发布条款原件，用于受限的 Agent 索引流程")
    public ResponseEntity<StreamingResponseBody> downloadPublished(
            @PathVariable Long productId, @PathVariable Long profileVersionId, @PathVariable Long sourceId) {
        InsuranceTermSourceVO source = sourceService.getPublished(sourceId);
        if (!productId.equals(source.getInsuranceId()) || !profileVersionId.equals(source.getProfileVersionId())) {
            return ResponseEntity.notFound().build();
        }
        java.io.InputStream content = sourceService.downloadPublished(sourceId, productId, profileVersionId);
        StreamingResponseBody body = output -> {
            try (java.io.InputStream input = content) {
                input.transferTo(output);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(source.getFileSize());
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.set("X-Content-SHA256", source.getSha256());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<StreamingResponseBody>(body, headers, HttpStatus.OK);
    }
}
