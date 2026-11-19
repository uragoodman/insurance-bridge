package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyRecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Owner-scoped, read-only projection for Agent policy summaries. */
@Mapper
public interface AiOwnPolicyMapper {

    @Select("SELECT CASE WHEN EXISTS (SELECT 1 FROM tab_customer_relation self_relation "
            + "WHERE self_relation.customer_id = CAST(#{ownerUserId} AS CHAR) "
            + "AND self_relation.relation = #{selfRelation} "
            + "AND self_relation.data_state = #{dataState} "
            + "AND self_relation.identity_card IS NOT NULL "
            + "AND self_relation.identity_card <> '') THEN 1 ELSE 0 END")
    boolean hasSelfProfile(
            @Param("ownerUserId") Long ownerUserId,
            @Param("selfRelation") String selfRelation,
            @Param("dataState") String dataState);

    @Select("SELECT CAST(w.id AS CHAR) AS policyId, w.insurance_name AS productName, "
            + "w.warranty_state AS policyStatusCode, "
            + "w.safeguard_start_time AS coverageStartTime, "
            + "w.safeguard_end_time AS coverageEndTime, "
            + "w.auto_warranty_extension AS autoExtensionFlag "
            + "FROM tab_warranty w "
            + "WHERE w.data_state = #{dataState} "
            + "AND w.warranty_state IN ('0', '1', '2') "
            + "AND EXISTS (SELECT 1 FROM tab_customer_relation self_relation "
            + "WHERE self_relation.customer_id = CAST(#{ownerUserId} AS CHAR) "
            + "AND self_relation.relation = #{selfRelation} "
            + "AND self_relation.data_state = #{dataState} "
            + "AND self_relation.identity_card = w.applicant_identity_card) "
            + "ORDER BY w.create_time DESC, w.id DESC LIMIT #{limit}")
    List<AiOwnPolicyRecordDTO> findOwnActivePolicies(
            @Param("ownerUserId") Long ownerUserId,
            @Param("selfRelation") String selfRelation,
            @Param("dataState") String dataState,
            @Param("limit") int limit);

    @Select("SELECT CAST(w.id AS CHAR) AS policyId, w.insurance_name AS productName, "
            + "w.warranty_state AS policyStatusCode, "
            + "w.safeguard_start_time AS coverageStartTime, "
            + "w.safeguard_end_time AS coverageEndTime, "
            + "w.auto_warranty_extension AS autoExtensionFlag "
            + "FROM tab_warranty w "
            + "WHERE w.id = #{policyId} AND w.data_state = #{dataState} "
            + "AND EXISTS (SELECT 1 FROM tab_customer_relation self_relation "
            + "WHERE self_relation.customer_id = CAST(#{ownerUserId} AS CHAR) "
            + "AND self_relation.relation = #{selfRelation} "
            + "AND self_relation.data_state = #{dataState} "
            + "AND self_relation.identity_card = w.applicant_identity_card) LIMIT 1")
    AiOwnPolicyRecordDTO findOwnCustomerPolicy(
            @Param("ownerUserId") Long ownerUserId,
            @Param("policyId") Long policyId,
            @Param("selfRelation") String selfRelation,
            @Param("dataState") String dataState);

    @Select("SELECT CAST(w.id AS CHAR) AS policyId, w.insurance_name AS productName, "
            + "w.warranty_state AS policyStatusCode, "
            + "w.safeguard_start_time AS coverageStartTime, "
            + "w.safeguard_end_time AS coverageEndTime, "
            + "w.auto_warranty_extension AS autoExtensionFlag, w.create_time AS createTime "
            + "FROM tab_warranty w "
            + "WHERE w.data_state = #{dataState} "
            + "AND EXISTS (SELECT 1 FROM tab_customer_relation self_relation "
            + "WHERE self_relation.customer_id = CAST(#{ownerUserId} AS CHAR) "
            + "AND self_relation.relation = #{selfRelation} "
            + "AND self_relation.data_state = #{dataState} "
            + "AND self_relation.identity_card = w.applicant_identity_card) "
            + "AND (#{cursorCreateTime} IS NULL OR w.create_time < #{cursorCreateTime} "
            + "OR (w.create_time = #{cursorCreateTime} AND w.id < #{cursorPolicyId})) "
            + "ORDER BY w.create_time DESC, w.id DESC LIMIT #{limit}")
    List<AiOwnPolicyRecordDTO> findOwnPoliciesPage(
            @Param("ownerUserId") Long ownerUserId,
            @Param("selfRelation") String selfRelation,
            @Param("dataState") String dataState,
            @Param("cursorCreateTime") java.time.LocalDateTime cursorCreateTime,
            @Param("cursorPolicyId") Long cursorPolicyId,
            @Param("limit") int limit);

    @Select("SELECT CAST(w.id AS CHAR) AS policyId, w.insurance_name AS productName, "
            + "w.warranty_state AS policyStatusCode, "
            + "w.safeguard_start_time AS coverageStartTime, "
            + "w.safeguard_end_time AS coverageEndTime, "
            + "w.auto_warranty_extension AS autoExtensionFlag "
            + "FROM tab_warranty w "
            + "WHERE w.id = #{policyId} "
            + "AND w.data_state = #{dataState} "
            + "AND EXISTS (SELECT 1 FROM tab_customer_relation self_relation "
            + "WHERE self_relation.customer_id = CAST(#{ownerUserId} AS CHAR) "
            + "AND self_relation.relation = #{selfRelation} "
            + "AND self_relation.data_state = #{dataState} "
            + "AND self_relation.identity_card = w.applicant_identity_card) "
            + "LIMIT 1")
    AiOwnPolicyRecordDTO findOwnActivePolicy(
            @Param("ownerUserId") Long ownerUserId,
            @Param("policyId") Long policyId,
            @Param("selfRelation") String selfRelation,
            @Param("dataState") String dataState);
}
