package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonRequestDTO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonResponseVO;
import com.itheima.bolee.insurance.pojo.Category;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationAttributeRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOptionRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductFactRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationRuleRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationSnapshotRow;
import com.itheima.bolee.insurance.repository.ai.AiProductCatalogRepository;
import com.itheima.bolee.insurance.repository.ai.AiRecommendationRepository;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiProductComparisonServiceTest {

    private static final Long CATEGORY_ID = 42L;
    private static final Long ATTRIBUTE_ID = 100L;
    private static final Long PROFILE_ONE = 301L;
    private static final Long PROFILE_TWO = 302L;

    @Test
    public void comparesEveryRequestedProductInRequestOrder() {
        Fixture fixture = new Fixture();
        fixture.setProducts(product(2L, PROFILE_TWO, CATEGORY_ID), product(1L, PROFILE_ONE, CATEGORY_ID));
        fixture.setFacts(numberFact(501L, PROFILE_ONE, "100"), numberFact(502L, PROFILE_TWO, "250"));

        AiProductComparisonResponseVO response = fixture.service.compareProducts(request("2", "1"));

        assertEquals("COMPLETE", response.getStatus());
        assertEquals(2, response.getProducts().size());
        assertEquals("2", response.getProducts().get(0).getProductId());
        assertEquals("250", response.getProducts().get(0).getFacts().get(0).getValueNumber().toPlainString());
        assertEquals("1", response.getProducts().get(1).getProductId());
        assertEquals("100", response.getProducts().get(1).getFacts().get(0).getValueNumber().toPlainString());
        assertEquals("catalog-v1", response.getCatalogVersion());
        assertEquals(Integer.valueOf(3), response.getRuleVersion());
    }

    @Test
    public void returnsPartialWhenAProductFactIsMissing() {
        Fixture fixture = new Fixture();
        fixture.setProducts(product(1L, PROFILE_ONE, CATEGORY_ID), product(2L, PROFILE_TWO, CATEGORY_ID));
        fixture.setFacts(numberFact(501L, PROFILE_ONE, "100"), missingFact(502L, PROFILE_TWO));

        AiProductComparisonResponseVO response = fixture.service.compareProducts(request("1", "2"));

        assertEquals("PARTIAL", response.getStatus());
        assertEquals(1, response.getUnresolvedCriteria().size());
        assertEquals("coverage_amount", response.getUnresolvedCriteria().get(0).getCode());
        assertEquals(Integer.valueOf(1), response.getUnresolvedCriteria().get(0).getAffectedProductCount());
        assertEquals("NOT_PROVIDED", response.getProducts().get(1).getFacts().get(0).getValueStatus());
    }

    @Test
    public void rejectsProductOutsideThePublishedCategory() {
        Fixture fixture = new Fixture();
        fixture.setProducts(product(1L, PROFILE_ONE, CATEGORY_ID), product(2L, PROFILE_TWO, 99L));
        fixture.setFacts(numberFact(501L, PROFILE_ONE, "100"), numberFact(502L, PROFILE_TWO, "250"));

        try {
            fixture.service.compareProducts(request("1", "2"));
            fail("Expected a cross-category product to be rejected");
        } catch (ResponseStatusException exception) {
            assertEquals(503, exception.getStatus().value());
        }
    }

    @Test
    public void returnsPublishedDisplayNameForEnumComparisonFacts() {
        Fixture fixture = new Fixture();
        AiRecommendationAttributeRow attribute = Fixture.attribute();
        attribute.setValueType("ENUM");
        when(fixture.recommendationRepository.findSnapshotAttributes(200L))
                .thenReturn(Collections.singletonList(attribute));
        AiRecommendationOptionRow option = new AiRecommendationOptionRow();
        option.setDefinitionId(ATTRIBUTE_ID);
        option.setValueCode("ADULT");
        option.setDisplayName("成人");
        option.setDataState("0");
        when(fixture.recommendationRepository.findSnapshotOptions(200L))
                .thenReturn(Collections.singletonList(option));
        AiRecommendationProductFactRow firstFact = enumFact(501L, PROFILE_ONE, "ADULT");
        AiRecommendationProductFactRow secondFact = enumFact(502L, PROFILE_TWO, "ADULT");
        fixture.setProducts(product(1L, PROFILE_ONE, CATEGORY_ID), product(2L, PROFILE_TWO, CATEGORY_ID));
        fixture.setFacts(firstFact, secondFact);

        AiProductComparisonResponseVO response = fixture.service.compareProducts(request("1", "2"));

        assertEquals("ADULT", response.getProducts().get(0).getFacts().get(0).getValueCode());
        assertEquals("成人", response.getProducts().get(0).getFacts().get(0).getValueDisplay());
    }

    private static AiProductComparisonRequestDTO request(String... productIds) {
        AiProductComparisonRequestDTO request = new AiProductComparisonRequestDTO();
        request.setRequestId("5b2f4f35-bd12-48b0-923b-6c632850bc89");
        request.setCategoryId(String.valueOf(CATEGORY_ID));
        request.setProductIds(Arrays.asList(productIds));
        return request;
    }

    private static AiRecommendationProductRow product(Long productId, Long profileId, Long actualCategoryId) {
        AiRecommendationProductRow product = new AiRecommendationProductRow();
        product.setProductId(productId);
        product.setProfileVersionId(profileId);
        product.setProfileCategoryId(CATEGORY_ID);
        product.setActualCategoryId(actualCategoryId);
        product.setProductVersion(1);
        product.setProductName("产品 " + productId);
        product.setProfileStatus("PUBLISHED");
        product.setProfileDataState("0");
        return product;
    }

    private static AiRecommendationProductFactRow numberFact(Long factId, Long profileId, String amount) {
        AiRecommendationProductFactRow fact = new AiRecommendationProductFactRow();
        fact.setId(factId);
        fact.setProfileVersionId(profileId);
        fact.setDefinitionId(ATTRIBUTE_ID);
        fact.setValueStatus("KNOWN");
        fact.setValueNumber(new BigDecimal(amount));
        return fact;
    }

    private static AiRecommendationProductFactRow missingFact(Long factId, Long profileId) {
        AiRecommendationProductFactRow fact = new AiRecommendationProductFactRow();
        fact.setId(factId);
        fact.setProfileVersionId(profileId);
        fact.setDefinitionId(ATTRIBUTE_ID);
        fact.setValueStatus("NOT_PROVIDED");
        return fact;
    }

    private static AiRecommendationProductFactRow enumFact(Long factId, Long profileId, String valueCode) {
        AiRecommendationProductFactRow fact = new AiRecommendationProductFactRow();
        fact.setId(factId);
        fact.setProfileVersionId(profileId);
        fact.setDefinitionId(ATTRIBUTE_ID);
        fact.setValueStatus("KNOWN");
        fact.setValueCode(valueCode);
        return fact;
    }

    private static final class Fixture {
        private final AiRecommendationRepository recommendationRepository = mock(AiRecommendationRepository.class);
        private final AiProductCatalogRepository catalogRepository = mock(AiProductCatalogRepository.class);
        private final AiProductRecommendationService service =
                new AiProductRecommendationService(recommendationRepository, catalogRepository);

        private Fixture() {
            Category category = new Category();
            category.setId(CATEGORY_ID);
            when(catalogRepository.findActiveLeafProductCategory(CATEGORY_ID)).thenReturn(category);

            AiRecommendationSnapshotRow snapshot = new AiRecommendationSnapshotRow();
            snapshot.setId(200L);
            snapshot.setCategoryId(CATEGORY_ID);
            snapshot.setRuleSetCategoryId(CATEGORY_ID);
            snapshot.setCatalogVersion("catalog-v1");
            snapshot.setRuleSetVersion(3);
            snapshot.setScoreStrategy("SUM");
            when(recommendationRepository.findCurrentSnapshot(any(Long.class), any(LocalDateTime.class)))
                    .thenReturn(snapshot);
            when(recommendationRepository.findSnapshotAttributes(200L))
                    .thenReturn(Collections.singletonList(attribute()));
            when(recommendationRepository.findSnapshotRules(200L))
                    .thenReturn(Collections.singletonList(rule()));
            when(recommendationRepository.findSnapshotOperators(200L)).thenReturn(Collections.emptyList());
            when(recommendationRepository.findSnapshotOptions(200L)).thenReturn(Collections.emptyList());
            when(recommendationRepository.findSnapshotMultiValues(any())).thenReturn(Collections.emptyList());
        }

        private void setProducts(AiRecommendationProductRow... products) {
            when(recommendationRepository.findSnapshotProductsByIds(any(), any()))
                    .thenReturn(Arrays.asList(products));
        }

        private void setFacts(AiRecommendationProductFactRow... facts) {
            when(recommendationRepository.findSnapshotProductFacts(any(), any(), any()))
                    .thenReturn(Arrays.asList(facts));
        }

        private static AiRecommendationAttributeRow attribute() {
            AiRecommendationAttributeRow attribute = new AiRecommendationAttributeRow();
            attribute.setId(ATTRIBUTE_ID);
            attribute.setCategoryId(CATEGORY_ID);
            attribute.setAttributeCode("coverage_amount");
            attribute.setDefinitionVersion(1);
            attribute.setDisplayName("保障额度");
            attribute.setValueType("NUMBER");
            attribute.setUnitCode("CNY");
            attribute.setAllowCompare(true);
            attribute.setAiEnabled(true);
            attribute.setSensitivityLevel("NONE");
            attribute.setStatus("PUBLISHED");
            attribute.setDataState("0");
            return attribute;
        }

        private static AiRecommendationRuleRow rule() {
            AiRecommendationRuleRow rule = new AiRecommendationRuleRow();
            rule.setId(400L);
            rule.setDefinitionId(ATTRIBUTE_ID);
            rule.setDefinitionCategoryId(CATEGORY_ID);
            rule.setRuleMode("COMPARE_ONLY");
            rule.setRequiredForClarification(false);
            rule.setScoreWeight(0);
            rule.setMissingPolicy("INCOMPLETE");
            return rule;
        }
    }
}
