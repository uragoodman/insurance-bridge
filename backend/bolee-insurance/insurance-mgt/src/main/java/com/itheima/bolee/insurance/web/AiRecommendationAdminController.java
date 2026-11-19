package com.itheima.bolee.insurance.web;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.utils.ResponseResultBuild;
import com.itheima.bolee.framework.commons.utils.SubjectContent;
import com.itheima.bolee.insurance.dto.ai.admin.AiRecommendationAdminCategoryVO;
import com.itheima.bolee.insurance.dto.ai.admin.AiRecommendationAdminProductVO;
import com.itheima.bolee.insurance.dto.ai.admin.AiRecommendationToggleDTO;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagSetRequest;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagSetVO;
import com.itheima.bolee.insurance.service.ai.AiRecommendationAdminService;
import com.itheima.bolee.insurance.service.ai.AiProductSearchTagService;
import com.itheima.bolee.insurance.service.ai.AiProductSearchTagGenerationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "AI 推荐产品管理")
@RestController
@RequestMapping("ai-recommendation")
public class AiRecommendationAdminController {
    private final AiRecommendationAdminService adminService;
    private final AiProductSearchTagService searchTagService;
    private final AiProductSearchTagGenerationService tagGenerationService;

    public AiRecommendationAdminController(AiRecommendationAdminService adminService,
                                           AiProductSearchTagService searchTagService,
                                           AiProductSearchTagGenerationService tagGenerationService) {
        this.adminService = adminService;
        this.searchTagService = searchTagService;
        this.tagGenerationService = tagGenerationService;
    }

    @GetMapping("categories")
    @ApiOperation("查询在用保险类别")
    public ResponseResult<List<AiRecommendationAdminCategoryVO>> categories() {
        return ResponseResultBuild.successBuild(adminService.listCategories());
    }

    @GetMapping("products")
    @ApiOperation("查询在售保险和 AI 推荐开关状态")
    public ResponseResult<List<AiRecommendationAdminProductVO>> products(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "recommendedOnly", defaultValue = "false") boolean recommendedOnly) {
        return ResponseResultBuild.successBuild(
                adminService.listProducts(categoryId, keyword, limit, recommendedOnly));
    }

    @PutMapping("products/{productId}/recommendation")
    @ApiOperation("直接加入或移出 AI 推荐产品池")
    public ResponseResult<Boolean> setRecommendation(
            @PathVariable Long productId, @Valid @RequestBody AiRecommendationToggleDTO request) {
        UserVO user = SubjectContent.getUserVO();
        adminService.setRecommendationEnabled(productId, request.getEnabled(), user == null ? null : user.getId());
        return ResponseResultBuild.successBuild(Boolean.TRUE);
    }

    @GetMapping("products/{productId}/search-tags")
    @ApiOperation("查询保险产品当前生效的 AI 检索标签")
    public ResponseResult<AiSearchTagSetVO> searchTags(@PathVariable Long productId) {
        return ResponseResultBuild.successBuild(searchTagService.getActive(productId));
    }

    @PutMapping("products/{productId}/search-tags")
    @ApiOperation("替换保险产品的人工 AI 检索标签并生成新版本")
    public ResponseResult<AiSearchTagSetVO> replaceSearchTags(
            @PathVariable Long productId, @Valid @RequestBody AiSearchTagSetRequest request) {
        UserVO user = SubjectContent.getUserVO();
        return ResponseResultBuild.successBuild(searchTagService.replaceManualTags(
                productId, request, user == null ? null : user.getId()));
    }

    @PostMapping("products/{productId}/search-tags/generate")
    @ApiOperation("根据保险描述生成并启用 AI 检索标签")
    public ResponseResult<AiSearchTagSetVO> generateSearchTags(@PathVariable Long productId) {
        UserVO user = SubjectContent.getUserVO();
        return ResponseResultBuild.successBuild(tagGenerationService.generateAndActivate(
                productId, user == null ? null : user.getId()));
    }
}
