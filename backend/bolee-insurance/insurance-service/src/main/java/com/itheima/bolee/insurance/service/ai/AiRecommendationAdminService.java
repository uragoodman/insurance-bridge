package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.framework.commons.constant.basic.SuperConstant;
import com.itheima.bolee.framework.commons.constant.category.CategoryConstant;
import com.itheima.bolee.insurance.dto.ai.admin.AiRecommendationAdminCategoryVO;
import com.itheima.bolee.insurance.dto.ai.admin.AiRecommendationAdminProductVO;
import com.itheima.bolee.insurance.mapper.CategoryMapper;
import com.itheima.bolee.insurance.mapper.ai.AiRecommendationAdminMapper;
import com.itheima.bolee.insurance.pojo.Category;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiRecommendationAdminService {
    private static final int MAX_LIMIT = 200;

    private final CategoryMapper categoryMapper;
    private final AiRecommendationAdminMapper adminMapper;

    public AiRecommendationAdminService(CategoryMapper categoryMapper,
                                        AiRecommendationAdminMapper adminMapper) {
        this.categoryMapper = categoryMapper;
        this.adminMapper = adminMapper;
    }

    public List<AiRecommendationAdminCategoryVO> listCategories() {
        List<Category> categories = categoryMapper.findActiveLeafProductCategories(
                SuperConstant.DATA_STATE_0, CategoryConstant.PRODUCT_TYPE);
        List<AiRecommendationAdminCategoryVO> result = new ArrayList<AiRecommendationAdminCategoryVO>();
        for (Category category : categories) {
            AiRecommendationAdminCategoryVO item = new AiRecommendationAdminCategoryVO();
            item.setCategoryId(category.getId());
            item.setCategoryName(category.getCategoryName());
            result.add(item);
        }
        return result;
    }

    public List<AiRecommendationAdminProductVO> listProducts(
            Long categoryId, String keyword, Integer requestedLimit, boolean recommendedOnly) {
        if (categoryId != null && categoryId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product category id");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search keyword is too long");
        }
        int limit = requestedLimit == null ? 200 : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 200");
        }
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return adminMapper.findActiveProducts(categoryId, normalizedKeyword, recommendedOnly, limit);
    }

    public void setRecommendationEnabled(Long productId, Boolean enabled, Long actorId) {
        if (productId == null || productId <= 0 || enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product and enabled state are required");
        }
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated admin user is required");
        }
        if (adminMapper.updateRecommendationFlag(productId, enabled, actorId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Product is unavailable or cannot be enabled while off sale");
        }
    }
}
