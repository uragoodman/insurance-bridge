package com.itheima.bolee.insurance.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.insurance.dto.ai.AgnoChatResponse;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatResponseVO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class AgnoChatResponseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void mapsSingleOwnPolicyDetailOnlyFromJavaStructuredResult() throws Exception {
        String payload = "{\"answer\":\"您有一份保障中的个人意外险。\","
                + "\"own_policy_detail\":{\"status\":\"FOUND\","
                + "\"as_of\":\"2026-11-17T10:00:00Z\",\"policy\":{"
                + "\"policy_id\":\"9001\",\"product_name\":\"个人意外险\","
                + "\"policy_status\":\"IN_FORCE\",\"policy_status_label\":\"保障中\","
                + "\"coverage_start_time\":\"2026-01-01T00:00:00\","
                + "\"coverage_end_time\":\"2026-12-31T23:59:59\","
                + "\"auto_extension_enabled\":false}}}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        AiAssistantChatResponseVO customerResponse =
                AgnoChatResponseMapper.toCustomerResponse("client-session", response);

        assertEquals("FOUND", customerResponse.getOwnPolicyDetail().getStatus());
        assertEquals("保障中", customerResponse.getOwnPolicyDetail().getPolicy().getPolicyStatusLabel());
        assertEquals(null, customerResponse.getOwnPolicySummary());
        assertFalse(objectMapper.writeValueAsString(customerResponse).contains("policyId"));
    }

    @Test
    public void parsesPythonSnakeCaseAndMapsTrustedRecommendations() throws Exception {
        String payload = "{\"session_id\":\"agent-session\","
                + "\"answer\":\"Java 目录找到一项产品\","
                + "\"recommendation_status\":\"MATCHED\","
                + "\"evaluation_completeness\":\"COMPLETE\","
                + "\"catalog_version\":\"catalog-test-v1\","
                + "\"rule_version\":2,"
                + "\"recommendations\":[{"
                + "\"product_id\":\"product-1\","
                + "\"name\":\"示例意外险\","
                + "\"category_id\":\"42\","
                + "\"category_code\":\"001002003\","
                + "\"category_name\":\"意外险\","
                + "\"match_reasons\":[\"来自 Java 目录\"],"
                + "\"product_version\":2,"
                + "\"verification_status\":\"VERIFIED_MATCH\","
                + "\"facts\":[{\"code\":\"waiting_period_days\","
                + "\"definition_version\":1,\"name\":\"等待期\","
                + "\"value_type\":\"NUMBER\",\"unit\":\"DAY\","
                + "\"value_number\":0,\"value_status\":\"KNOWN\"}]}]}";

        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);
        AiAssistantChatResponseVO customerResponse =
                AgnoChatResponseMapper.toCustomerResponse("client-session", response);

        assertEquals("client-session", customerResponse.getSessionId());
        assertEquals("product-1", customerResponse.getRecommendations().get(0).getProductId());
        assertEquals("001002003", customerResponse.getRecommendations().get(0).getCategoryCode());
        assertEquals("来自 Java 目录", customerResponse.getRecommendations().get(0).getMatchReasons().get(0));
        assertEquals("MATCHED", customerResponse.getRecommendationStatus());
        assertEquals(Integer.valueOf(2), customerResponse.getRuleVersion());
        assertEquals(
                "waiting_period_days",
                customerResponse.getRecommendations().get(0).getFacts().get(0).getCode());
        assertEquals(
                "0",
                customerResponse.getRecommendations().get(0).getFacts().get(0).getValueNumber().toPlainString());
        String customerJson = objectMapper.writeValueAsString(customerResponse);
        org.junit.Assert.assertTrue(customerJson.contains("\"definitionVersion\":1"));
        org.junit.Assert.assertFalse(customerJson.contains("definition_version"));
    }

    @Test
    public void mapsRecommendationCandidateWithoutCatalogOnlyCategoryLabels() throws Exception {
        String payload = "{\"answer\":\"已核对比较结果\",\"recommendation_status\":\"MATCHED\","
                + "\"evaluation_completeness\":\"COMPLETE\",\"catalog_version\":\"catalog-v1\","
                + "\"rule_version\":1,"
                + "\"recommendations\":[{\"product_id\":\"p-1\",\"name\":\"医疗险\","
                + "\"category_id\":\"42\",\"product_version\":1,"
                + "\"verification_status\":\"VERIFIED_MATCH\",\"facts\":[]}]}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        AiAssistantChatResponseVO customerResponse =
                AgnoChatResponseMapper.toCustomerResponse("client-session", response);

        assertEquals("p-1", customerResponse.getRecommendations().get(0).getProductId());
        assertEquals(null, customerResponse.getRecommendations().get(0).getCategoryCode());
    }

    @Test
    public void preservesPartialCatalogStatusAndUnresolvedCriteria() throws Exception {
        String payload = "{\"answer\":\"有部分资料尚未核实\","
                + "\"recommendation_status\":\"CATALOG_INCOMPLETE\","
                + "\"evaluation_completeness\":\"PARTIAL\","
                + "\"catalog_version\":\"catalog-v1\",\"rule_version\":1,"
                + "\"unresolved_criteria\":[{\"code\":\"waiting_days\","
                + "\"affected_product_count\":2,\"reason\":\"FACT_NOT_PROVIDED\"}],"
                + "\"recommendations\":[]}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        AiAssistantChatResponseVO customerResponse =
                AgnoChatResponseMapper.toCustomerResponse("client-session", response);

        assertEquals("CATALOG_INCOMPLETE", customerResponse.getRecommendationStatus());
        assertEquals(Integer.valueOf(2), customerResponse.getUnresolvedCriteria().get(0).getAffectedProductCount());
    }

    @Test
    public void mapsStructuredMultiProductComparisonForChatUi() throws Exception {
        String payload = "{\"answer\":\"两款产品的保障额度不同。\","
                + "\"product_comparison\":{\"request_id\":\"11111111-1111-4111-8111-111111111111\","
                + "\"status\":\"COMPLETE\",\"category_id\":\"42\","
                + "\"catalog_version\":\"catalog-v1\",\"rule_version\":3,"
                + "\"products\":["
                + comparisonProduct("9001", "产品 A", "100000") + ","
                + comparisonProduct("9002", "产品 B", "250000") + "],"
                + "\"unresolved_criteria\":[]}}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        AiAssistantChatResponseVO customerResponse =
                AgnoChatResponseMapper.toCustomerResponse("client-session", response);

        assertEquals(null, customerResponse.getRecommendationStatus());
        assertEquals("COMPLETE", customerResponse.getProductComparison().getStatus());
        assertEquals(2, customerResponse.getProductComparison().getProducts().size());
        assertEquals("9002", customerResponse.getProductComparison().getProducts().get(1).getProductId());
        assertEquals("250000", customerResponse.getProductComparison().getProducts().get(1)
                .getFacts().get(0).getValueNumber().toPlainString());
        assertEquals("250000 元", customerResponse.getProductComparison().getProducts().get(1)
                .getFacts().get(0).getValueDisplay());
    }

    @Test
    public void rejectsComparisonWithDifferentFactColumns() throws Exception {
        String payload = "{\"answer\":\"产品对比\",\"product_comparison\":{"
                + "\"request_id\":\"11111111-1111-4111-8111-111111111111\","
                + "\"status\":\"COMPLETE\",\"category_id\":\"42\","
                + "\"catalog_version\":\"catalog-v1\",\"rule_version\":1,"
                + "\"products\":["
                + comparisonProduct("9001", "产品 A", "100000") + ","
                + "{\"product_id\":\"9002\",\"name\":\"产品 B\",\"product_version\":1,\"facts\":[]}],"
                + "\"unresolved_criteria\":[]}}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        try {
            AgnoChatResponseMapper.toCustomerResponse("client-session", response);
            fail("Expected inconsistent comparison columns to be rejected");
        } catch (ProjectException expected) {
            // Every product column must be drawn from the same published comparison definitions.
        }
    }

    @Test
    public void rejectsUnknownFactThatContainsAValue() throws Exception {
        String payload = "{\"answer\":\"比较结果\",\"recommendation_status\":\"MATCHED\","
                + "\"evaluation_completeness\":\"COMPLETE\",\"catalog_version\":\"catalog-v1\","
                + "\"rule_version\":1,\"recommendations\":[{\"product_id\":\"p-1\","
                + "\"name\":\"医疗险\",\"category_id\":\"42\",\"product_version\":1,"
                + "\"verification_status\":\"VERIFIED_MATCH\",\"facts\":[{"
                + "\"code\":\"waiting_days\",\"definition_version\":1,\"name\":\"等待期\","
                + "\"value_type\":\"NUMBER\",\"value_status\":\"NOT_PROVIDED\","
                + "\"value_number\":0}]}]}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        try {
            AgnoChatResponseMapper.toCustomerResponse("client-session", response);
            fail("Expected inconsistent unknown fact to be rejected");
        } catch (ProjectException expected) {
            // Unknown product facts must never carry values.
        }
    }

    @Test
    public void rejectsUnsupportedRecommendationStatus() throws Exception {
        AgnoChatResponse response = objectMapper.readValue(
                "{\"answer\":\"结果\",\"recommendation_status\":\"ALMOST_MATCHED\"}",
                AgnoChatResponse.class);

        try {
            AgnoChatResponseMapper.toCustomerResponse("client-session", response);
            fail("Expected an unsupported business status to be rejected");
        } catch (ProjectException expected) {
            // Only documented statuses may cross into the customer response.
        }
    }

    @Test
    public void rejectsMoreThanThreeUpstreamCandidates() throws Exception {
        String payload = "{\"answer\":\"目录结果\",\"recommendations\":["
                + candidate("1") + "," + candidate("2") + "," + candidate("3") + "," + candidate("4") + "]}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        try {
            AgnoChatResponseMapper.toCustomerResponse("client-session", response);
            fail("Expected invalid upstream response to be rejected");
        } catch (ProjectException expected) {
            // Expected: upstream must not return more than the Java limit.
        }
    }

    @Test
    public void rejectsDuplicateProductIds() throws Exception {
        String payload = "{\"answer\":\"目录结果\",\"recommendations\":["
                + candidate("same") + "," + candidate("same") + "]}";
        AgnoChatResponse response = objectMapper.readValue(payload, AgnoChatResponse.class);

        try {
            AgnoChatResponseMapper.toCustomerResponse("client-session", response);
            fail("Expected duplicate products to be rejected");
        } catch (ProjectException expected) {
            // Expected: upstream candidates must have unique product IDs.
        }
    }

    private String candidate(String productId) {
        return "{\"product_id\":\"" + productId + "\","
                + "\"name\":\"示例产品\","
                + "\"category_id\":\"42\","
                + "\"category_code\":\"001002003\","
                + "\"category_name\":\"意外险\"}";
    }

    private String comparisonProduct(String productId, String name, String amount) {
        return "{\"product_id\":\"" + productId + "\",\"name\":\"" + name
                + "\",\"product_version\":1,\"facts\":[{\"code\":\"coverage_amount\","
                + "\"definition_version\":1,\"name\":\"保障额度\",\"value_type\":\"NUMBER\","
                + "\"unit\":\"CNY\",\"value_number\":" + amount
                + ",\"value_display\":\"" + amount + " 元\",\"value_status\":\"KNOWN\"}]}";
    }
}
