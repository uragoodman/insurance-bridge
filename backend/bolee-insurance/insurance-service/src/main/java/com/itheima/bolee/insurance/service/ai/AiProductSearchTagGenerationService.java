package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagSetVO;
import com.itheima.bolee.insurance.pojo.Category;
import com.itheima.bolee.insurance.pojo.Insurance;
import com.itheima.bolee.insurance.repository.ai.AiProductCatalogRepository;
import com.itheima.bolee.insurance.service.ai.AiProductSearchTagGeneratorClient.GeneratedTags;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Service
public class AiProductSearchTagGenerationService {
    private final AiProductCatalogRepository catalogRepository;
    private final AiProductSearchTagService searchTagService;
    private final AiProductSearchTagGeneratorClient generatorClient;

    public AiProductSearchTagGenerationService(AiProductCatalogRepository catalogRepository,
                                               AiProductSearchTagService searchTagService,
                                               AiProductSearchTagGeneratorClient generatorClient) {
        this.catalogRepository = catalogRepository;
        this.searchTagService = searchTagService;
        this.generatorClient = generatorClient;
    }

    public AiSearchTagSetVO generateAndActivate(Long productId, Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated admin user is required");
        }
        Insurance product = catalogRepository.findProductForTagGeneration(productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        Map<String, String> source = new HashMap<String, String>();
        source.put("product_name", product.getInsuranceName());
        source.put("category_name", findCategoryName(String.valueOf(product.getCategoryNo())));
        source.put("product_labels", bounded(product.getLabelsJson()));
        source.put("application_notes", bounded(product.getRemakeJson()));
        source.put("product_review", bounded(product.getComment()));
        source.put("product_description", bounded(product.getRemake()));

        GeneratedTags generated;
        try {
            generated = generatorClient.generate(source);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI product tags could not be generated", exception);
        }
        return searchTagService.replaceGeneratedTags(productId, generated.getTags(), actorId,
                generated.getModelId(), generated.getPromptVersion());
    }

    private String findCategoryName(String categoryNo) {
        if (!StringUtils.hasText(categoryNo)) return null;
        for (Category category : catalogRepository.findActiveLeafProductCategories()) {
            if (categoryNo.equals(category.getCategoryNo())) return category.getCategoryName();
        }
        return null;
    }

    private static String bounded(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 4000 ? trimmed : trimmed.substring(0, 4000);
    }
}
