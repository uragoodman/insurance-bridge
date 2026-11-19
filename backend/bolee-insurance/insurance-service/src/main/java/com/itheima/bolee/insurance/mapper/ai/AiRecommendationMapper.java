package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.pojo.ai.AiRecommendationAttributeRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductFactRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationMultiValueRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOperatorRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOptionRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationRuleRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationSnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiRecommendationMapper {

    @Select("SELECT s.id, s.category_id AS categoryId, s.catalog_version AS catalogVersion, "
            + "s.rule_set_id AS ruleSetId, rs.category_id AS ruleSetCategoryId, "
            + "rs.rule_set_version AS ruleSetVersion, "
            + "rs.score_strategy AS scoreStrategy "
            + "FROM tab_ai_recommendation_catalog_current c "
            + "JOIN tab_ai_recommendation_catalog_snapshot s ON s.id = c.snapshot_id "
            + "JOIN tab_ai_recommendation_rule_set rs ON rs.id = s.rule_set_id "
            + "WHERE c.category_id = #{categoryId} AND s.category_id = c.category_id AND s.status = 'PUBLISHED' "
            + "AND rs.status = 'PUBLISHED' AND rs.data_state = '0' "
            + "AND (rs.effective_from IS NULL OR rs.effective_from <= #{now}) "
            + "AND (rs.effective_to IS NULL OR rs.effective_to > #{now}) LIMIT 1")
    AiRecommendationSnapshotRow findCurrentSnapshot(
            @Param("categoryId") Long categoryId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM tab_ai_recommendation_catalog_current WHERE category_id = #{categoryId}")
    int countSnapshotPointers(@Param("categoryId") Long categoryId);

    @Select("SELECT d.id, d.category_id AS categoryId, d.attribute_code AS attributeCode, "
            + "d.definition_version AS definitionVersion, "
            + "d.display_name AS displayName, d.value_type AS valueType, d.unit_code AS unitCode, "
            + "d.allow_hard_filter AS allowHardFilter, d.allow_preference AS allowPreference, "
            + "d.allow_compare AS allowCompare, d.ai_enabled AS aiEnabled, "
            + "d.sensitivity_level AS sensitivityLevel, d.status, d.data_state AS dataState, "
            + "d.effective_from AS effectiveFrom, d.effective_to AS effectiveTo "
            + "FROM tab_ai_recommendation_snapshot_attribute sa "
            + "JOIN tab_ai_rec_attribute_definition d ON d.id = sa.definition_id "
            + "WHERE sa.snapshot_id = #{snapshotId} "
            + "ORDER BY d.attribute_code, d.definition_version")
    List<AiRecommendationAttributeRow> findSnapshotAttributes(@Param("snapshotId") Long snapshotId);

    @Select("SELECT o.definition_id AS definitionId, o.operator_code AS operatorCode "
            + "FROM tab_ai_recommendation_snapshot_attribute sa "
            + "JOIN tab_ai_recommendation_catalog_snapshot s ON s.id = sa.snapshot_id "
            + "JOIN tab_ai_rec_attribute_operator o ON o.definition_id = sa.definition_id "
            + "WHERE sa.snapshot_id = #{snapshotId} ORDER BY o.definition_id, o.operator_code")
    List<AiRecommendationOperatorRow> findSnapshotOperators(@Param("snapshotId") Long snapshotId);

    @Select("SELECT o.definition_id AS definitionId, o.value_code AS valueCode, "
            + "o.display_name AS displayName, o.sort_no AS sortNo, o.data_state AS dataState "
            + "FROM tab_ai_recommendation_snapshot_attribute sa "
            + "JOIN tab_ai_recommendation_catalog_snapshot s ON s.id = sa.snapshot_id "
            + "JOIN tab_ai_rec_attribute_option o ON o.definition_id = sa.definition_id "
            + "WHERE sa.snapshot_id = #{snapshotId} "
            + "ORDER BY o.definition_id, o.sort_no, o.value_code")
    List<AiRecommendationOptionRow> findSnapshotOptions(@Param("snapshotId") Long snapshotId);

    @Select("SELECT r.id, r.definition_id AS definitionId, d.category_id AS definitionCategoryId, "
            + "r.rule_mode AS ruleMode, "
            + "r.required_for_clarification AS requiredForClarification, r.score_weight AS scoreWeight, "
            + "r.priority, r.reason_code AS reasonCode, r.reason_message AS reasonMessage, "
            + "r.missing_policy AS missingPolicy "
            + "FROM tab_ai_recommendation_rule r "
            + "JOIN tab_ai_recommendation_catalog_snapshot s ON s.rule_set_id = r.rule_set_id "
            + "JOIN tab_ai_rec_attribute_definition d ON d.id = r.definition_id "
            + "WHERE s.id = #{snapshotId} ORDER BY r.priority, r.id")
    List<AiRecommendationRuleRow> findSnapshotRules(@Param("snapshotId") Long snapshotId);

    @Select("SELECT pv.product_id AS productId, pv.id AS profileVersionId, "
            + "pv.category_id AS profileCategoryId, pv.product_version AS productVersion, "
            + "actual_category.id AS actualCategoryId, "
            + "pv.product_name_snapshot AS productName, pv.sort_no AS sortNo, "
            + "pv.status AS profileStatus, pv.data_state AS profileDataState, "
            + "pv.effective_from AS profileEffectiveFrom, pv.effective_to AS profileEffectiveTo "
            + "FROM tab_ai_recommendation_snapshot_product sp "
            + "JOIN tab_ai_recommendation_catalog_snapshot s ON s.id = sp.snapshot_id "
            + "JOIN tab_ai_product_profile_version pv ON pv.id = sp.profile_version_id "
            + "JOIN tab_insurance p ON p.id = pv.product_id "
            + "LEFT JOIN tab_category actual_category ON actual_category.id = pv.category_id "
            + "AND actual_category.category_no = p.category_no "
            + "AND actual_category.data_state = '0' AND actual_category.category_type = '1' "
            + "WHERE sp.snapshot_id = #{snapshotId} "
            + "AND p.data_state = '0' AND p.insurance_state = '0' "
            + "ORDER BY pv.sort_no, pv.product_id")
    List<AiRecommendationProductRow> findSnapshotProducts(@Param("snapshotId") Long snapshotId);

    @Select({"<script>",
            "SELECT pv.product_id AS productId, pv.id AS profileVersionId,",
            "pv.category_id AS profileCategoryId, pv.product_version AS productVersion,",
            "actual_category.id AS actualCategoryId, pv.product_name_snapshot AS productName,",
            "pv.sort_no AS sortNo, pv.status AS profileStatus, pv.data_state AS profileDataState,",
            "pv.effective_from AS profileEffectiveFrom, pv.effective_to AS profileEffectiveTo",
            "FROM tab_ai_recommendation_snapshot_product sp",
            "JOIN tab_ai_product_profile_version pv ON pv.id = sp.profile_version_id",
            "JOIN tab_insurance p ON p.id = pv.product_id",
            "LEFT JOIN tab_category actual_category ON actual_category.id = pv.category_id",
            "AND actual_category.category_no = p.category_no",
            "AND actual_category.data_state = '0' AND actual_category.category_type = '1'",
            "WHERE sp.snapshot_id = #{snapshotId}",
            "AND p.data_state = '0' AND p.insurance_state = '0'",
            "AND pv.product_id IN",
            "<foreach collection='productIds' item='productId' open='(' separator=',' close=')'>",
            "#{productId}",
            "</foreach>",
            "ORDER BY pv.sort_no, pv.product_id",
            "</script>"})
    List<AiRecommendationProductRow> findSnapshotProductsByIds(
            @Param("snapshotId") Long snapshotId, @Param("productIds") List<Long> productIds);

    @Select({"<script>",
            "SELECT v.id, v.profile_version_id AS profileVersionId, v.definition_id AS definitionId,",
            "d.attribute_code AS attributeCode, d.definition_version AS definitionVersion,",
            "v.value_status AS valueStatus, v.value_code AS valueCode, v.value_number AS valueNumber,",
            "v.value_min AS valueMin, v.value_max AS valueMax",
            "FROM tab_ai_product_attribute_value v",
            "JOIN tab_ai_recommendation_snapshot_attribute sa ON sa.definition_id = v.definition_id",
            "JOIN tab_ai_rec_attribute_definition d ON d.id = v.definition_id",
            "WHERE sa.snapshot_id = #{snapshotId} AND v.profile_version_id IN",
            "<foreach collection='profileVersionIds' item='profileId' open='(' separator=',' close=')'>",
            "#{profileId}",
            "</foreach>",
            "AND (v.effective_from IS NULL OR v.effective_from &lt;= #{now})",
            "AND (v.effective_to IS NULL OR v.effective_to &gt; #{now})",
            "ORDER BY v.profile_version_id, v.definition_id",
            "</script>"})
    List<AiRecommendationProductFactRow> findSnapshotProductFacts(
            @Param("snapshotId") Long snapshotId,
            @Param("profileVersionIds") List<Long> profileVersionIds,
            @Param("now") LocalDateTime now);

    @Select({"<script>",
            "SELECT mv.attribute_value_id AS attributeValueId, mv.value_code AS valueCode",
            "FROM tab_ai_product_attribute_multi_value mv",
            "JOIN tab_ai_product_attribute_value v ON v.id = mv.attribute_value_id",
            "WHERE v.profile_version_id IN",
            "<foreach collection='profileVersionIds' item='profileId' open='(' separator=',' close=')'>",
            "#{profileId}",
            "</foreach>",
            "</script>"})
    List<AiRecommendationMultiValueRow> findSnapshotMultiValues(
            @Param("profileVersionIds") List<Long> profileVersionIds);
}
