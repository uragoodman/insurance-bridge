package com.itheima.bolee.insurance.web.ai.internal;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonRequestDTO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonResponseVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductRecommendationRequestDTO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationCriteriaCatalogVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationResponseVO;
import com.itheima.bolee.insurance.service.ai.AgentScope;
import com.itheima.bolee.insurance.service.ai.AiProductRecommendationService;
import com.itheima.bolee.insurance.service.ai.AgnoScope;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Api(tags = "AI 客服内部产品推荐")
@RestController
@RequestMapping("/internal/agent-tools/v1/products")
@AgentScope(AgnoScope.PRODUCT_CATALOG_READ)
public class AiProductRecommendationController {

    private final AiProductRecommendationService recommendationService;

    public AiProductRecommendationController(AiProductRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendation-criteria")
    @ApiOperation("查询已发布的产品推荐条件目录")
    public ResponseResult<AiRecommendationCriteriaCatalogVO> criteria(
            @RequestParam("categoryId") String categoryId) {
        return ResponseResultBuild.successBuild(recommendationService.findCriteria(categoryId));
    }

    @PostMapping("/recommendations")
    @ApiOperation("按已发布条件和规则筛选、排序保险产品")
    public ResponseResult<AiRecommendationResponseVO> recommend(
            @Valid @RequestBody AiProductRecommendationRequestDTO request) {
        return ResponseResultBuild.successBuild(recommendationService.recommend(request));
    }

    @PostMapping("/compare")
    @ApiOperation("逐项比较当前已发布目录中的多个保险产品")
    public ResponseResult<AiProductComparisonResponseVO> compare(
            @Valid @RequestBody AiProductComparisonRequestDTO request) {
        return ResponseResultBuild.successBuild(recommendationService.compareProducts(request));
    }
}
