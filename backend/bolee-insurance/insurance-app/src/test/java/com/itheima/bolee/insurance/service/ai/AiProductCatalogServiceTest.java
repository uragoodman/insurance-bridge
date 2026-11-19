package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.AiProductSearchResponseVO;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagRow;
import com.itheima.bolee.insurance.pojo.Category;
import com.itheima.bolee.insurance.pojo.Insurance;
import com.itheima.bolee.insurance.repository.ai.AiProductCatalogRepository;
import com.itheima.bolee.insurance.repository.ai.AiProductSearchTagRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class AiProductCatalogServiceTest {
    private AiProductCatalogRepository catalogRepository;
    private AiProductSearchTagRepository tagRepository;
    private AiProductCatalogService service;
    private Category category;
    private Insurance product;

    @Before
    public void setUp() {
        catalogRepository = mock(AiProductCatalogRepository.class);
        tagRepository = mock(AiProductSearchTagRepository.class);
        service = new AiProductCatalogService(catalogRepository, tagRepository);

        category = new Category();
        category.setId(42L);
        category.setCategoryNo("1001");
        category.setCategoryName("测试分类");
        when(catalogRepository.findActiveLeafProductCategories()).thenReturn(Collections.singletonList(category));
        when(catalogRepository.findActiveLeafProductCategory(42L)).thenReturn(category);

        product = new Insurance();
        product.setId(7001L);
        product.setCategoryNo(1001L);
        product.setInsuranceName("示例产品");
        when(catalogRepository.findActiveProducts(anyList(), eq(1001L), anyList(), eq(3)))
                .thenReturn(Collections.singletonList(product));
    }

    @Test
    public void returnsProductWhenAtLeastTwoOfThreeNeedTermsMatchTags() {
        when(tagRepository.findActiveTagsByProductIds(anyList())).thenReturn(Arrays.asList(
                tag("AUDIENCE", "团体"), tag("SCENARIO", "运动")));

        AiProductSearchResponseVO result = service.search(
                "42", Arrays.asList("团体", "运动", "意外险"), 3);

        assertEquals("MATCHED", result.getStatus());
        assertEquals("7001", result.getCandidates().get(0).getProductId());
        assertEquals(2, result.getCandidates().get(0).getMatchReasons().size());
        verify(catalogRepository).findActiveProducts(anyList(), eq(1001L),
                eq(Arrays.asList("团体", "运动", "意外险")), eq(3));
    }

    @Test
    public void rejectsCandidateThatMatchesOnlyOneOfThreeNeedTerms() {
        when(tagRepository.findActiveTagsByProductIds(anyList()))
                .thenReturn(Collections.singletonList(tag("AUDIENCE", "团体")));

        AiProductSearchResponseVO result = service.search(
                "42", Arrays.asList("团体", "运动", "意外险"), 3);

        assertEquals("NO_MATCH", result.getStatus());
        assertEquals(0, result.getCandidates().size());
    }

    private static AiSearchTagRow tag(String type, String value) {
        AiSearchTagRow tag = new AiSearchTagRow();
        tag.setProductId(7001L);
        tag.setTagType(type);
        tag.setTagValue(value);
        return tag;
    }
}
