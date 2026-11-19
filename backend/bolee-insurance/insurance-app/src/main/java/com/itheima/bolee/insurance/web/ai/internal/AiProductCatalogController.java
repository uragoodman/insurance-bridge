package com.itheima.bolee.insurance.web.ai.internal;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.insurance.dto.ai.AiProductCategoryVO;
import com.itheima.bolee.insurance.dto.ai.AiProductSearchResponseVO;
import com.itheima.bolee.insurance.service.ai.AgentScope;
import com.itheima.bolee.insurance.service.ai.AiProductCatalogService;
import com.itheima.bolee.insurance.service.ai.AgnoScope;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList;

/** Private, read-only product catalog tools for the Agno service. */
@Api(tags = "AI 客服内部产品目录")
@RestController
@RequestMapping("/internal/agent-tools/v1/products")
@AgentScope(AgnoScope.PRODUCT_CATALOG_READ)
public class AiProductCatalogController {

    private final AiProductCatalogService catalogService;

    public AiProductCatalogController(AiProductCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    @ApiOperation("查询可用于客服目录检索的动态产品分类")
    public ResponseResult<List<AiProductCategoryVO>> categories() {
        return ResponseResultBuild.successBuild(catalogService.findCategories());
    }

    @GetMapping("/search")
    @ApiOperation("按类别或关键词查询最多三项已开启 AI 推荐的在售保险")
    public ResponseResult<AiProductSearchResponseVO> search(
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "keywords", required = false) List<String> keywords,
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<String> searchTerms = keywords == null ? new ArrayList<String>() : new ArrayList<String>(keywords);
        if (keyword != null) searchTerms.add(keyword);
        return ResponseResultBuild.successBuild(catalogService.search(categoryId, searchTerms, limit));
    }
}
