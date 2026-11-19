package com.itheima.bolee.insurance.repository.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.bolee.framework.commons.constant.basic.SuperConstant;
import com.itheima.bolee.framework.commons.constant.category.CategoryConstant;
import com.itheima.bolee.insurance.constant.InsuranceConstant;
import com.itheima.bolee.insurance.mapper.CategoryMapper;
import com.itheima.bolee.insurance.mapper.InsuranceMapper;
import com.itheima.bolee.insurance.mapper.ai.AiProductSearchTagMapper;
import com.itheima.bolee.insurance.pojo.Category;
import com.itheima.bolee.insurance.pojo.Insurance;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Read-only, narrow database access for the first AI product-catalog slice. */
@Repository
public class AiProductCatalogRepository {

    private final CategoryMapper categoryMapper;
    private final InsuranceMapper insuranceMapper;
    private final AiProductSearchTagMapper searchTagMapper;

    public AiProductCatalogRepository(CategoryMapper categoryMapper, InsuranceMapper insuranceMapper,
                                      AiProductSearchTagMapper searchTagMapper) {
        this.categoryMapper = categoryMapper;
        this.insuranceMapper = insuranceMapper;
        this.searchTagMapper = searchTagMapper;
    }

    public List<Category> findActiveLeafProductCategories() {
        return categoryMapper.findActiveLeafProductCategories(
                SuperConstant.DATA_STATE_0, CategoryConstant.PRODUCT_TYPE);
    }

    public Category findActiveLeafProductCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryMapper.findActiveLeafProductCategory(
                categoryId, SuperConstant.DATA_STATE_0, CategoryConstant.PRODUCT_TYPE);
    }

    public Insurance findProductForTagGeneration(Long productId) {
        if (productId == null) return null;
        return insuranceMapper.selectOne(new LambdaQueryWrapper<Insurance>()
                .select(Insurance::getId, Insurance::getCategoryNo, Insurance::getInsuranceName,
                        Insurance::getLabelsJson, Insurance::getRemakeJson, Insurance::getComment,
                        Insurance::getRemake)
                .eq(Insurance::getId, productId)
                .eq(Insurance::getDataState, SuperConstant.DATA_STATE_0)
                .last("LIMIT 1"));
    }

    public List<Insurance> findActiveProducts(List<Long> activeCategoryNos, Long categoryNo,
                                              List<String> keywords, int categoryOnlyLimit) {
        if (categoryNo == null && (activeCategoryNos == null || activeCategoryNos.isEmpty())) {
            return java.util.Collections.emptyList();
        }
        LambdaQueryWrapper<Insurance> query = new LambdaQueryWrapper<Insurance>()
                .select(Insurance::getId, Insurance::getCategoryNo, Insurance::getInsuranceName,
                        Insurance::getSortNo, Insurance::getInsuranceState, Insurance::getAiRecommendEnabled,
                        Insurance::getComment, Insurance::getRemake, Insurance::getLabelsJson,
                        Insurance::getRemakeJson, Insurance::getPublishNumber)
                .eq(Insurance::getDataState, SuperConstant.DATA_STATE_0)
                .eq(Insurance::getInsuranceState, InsuranceConstant.INSURANCE_STATE_0)
                .eq(Insurance::getAiRecommendEnabled, true);
        if (categoryNo != null) {
            query.eq(Insurance::getCategoryNo, categoryNo);
        } else {
            query.in(Insurance::getCategoryNo, activeCategoryNos);
        }
        if (keywords != null && !keywords.isEmpty()) {
            List<Long> taggedProductIds = searchTagMapper.findActiveProductIdsMatchingKeywords(keywords);
            query.and(wrapper -> {
                boolean first = true;
                for (String keyword : keywords) {
                    if (first) {
                        wrapper.like(Insurance::getInsuranceName, keyword);
                        first = false;
                    } else {
                        wrapper.or().like(Insurance::getInsuranceName, keyword);
                    }
                    wrapper.or().like(Insurance::getPublishNumber, keyword)
                            .or().like(Insurance::getLabelsJson, keyword)
                            .or().like(Insurance::getRemakeJson, keyword)
                            .or().like(Insurance::getComment, keyword)
                            .or().like(Insurance::getRemake, keyword);
                }
                if (taggedProductIds != null && !taggedProductIds.isEmpty()) {
                    wrapper.or().in(Insurance::getId, taggedProductIds);
                }
            });
        }
        query.orderByAsc(Insurance::getSortNo).orderByAsc(Insurance::getId);
        if (keywords == null || keywords.isEmpty()) query.last("LIMIT " + categoryOnlyLimit);
        return insuranceMapper.selectList(query);
    }
}
