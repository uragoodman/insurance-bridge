package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiProductSearchTagMapper {
    @Select("SELECT id FROM tab_insurance WHERE id = #{productId} AND data_state = '0' FOR UPDATE")
    Long lockProduct(@Param("productId") Long productId);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM tab_insurance_ai_search_tag_set WHERE insurance_id = #{productId}")
    int findLatestVersion(@Param("productId") Long productId);

    @Select("SELECT id FROM tab_insurance_ai_search_tag_set WHERE insurance_id = #{productId} AND status = 'ACTIVE' LIMIT 1")
    Long findActiveTagSetId(@Param("productId") Long productId);

    @Update("UPDATE tab_insurance_ai_search_tag_set SET status = 'ARCHIVED' WHERE insurance_id = #{productId} AND status = 'ACTIVE'")
    int archiveActiveTagSets(@Param("productId") Long productId);

    @Insert("INSERT INTO tab_insurance_ai_search_tag_set "
            + "(id, insurance_id, version_no, status, taxonomy_version, source_type, model_id, prompt_version, create_by) "
            + "VALUES (#{id}, #{productId}, #{version}, 'ACTIVE', 'v1', #{sourceType}, #{modelId}, #{promptVersion}, #{actorId})")
    int insertTagSet(@Param("id") Long id, @Param("productId") Long productId,
                     @Param("version") Integer version, @Param("sourceType") String sourceType,
                     @Param("modelId") String modelId, @Param("promptVersion") String promptVersion,
                     @Param("actorId") Long actorId);

    @Insert({"<script>",
            "INSERT INTO tab_insurance_ai_search_tag (id, tag_set_id, tag_type, tag_value, normalized_value, evidence)",
            "VALUES",
            "<foreach collection='tags' item='tag' separator=','>",
            "(#{tag.id}, #{tag.tagSetId}, #{tag.tagType}, #{tag.tagValue}, #{tag.normalizedValue}, #{tag.evidence})",
            "</foreach>",
            "</script>"})
    int insertTags(@Param("tags") List<AiSearchTagRow> tags);

    @Select("SELECT s.id AS tagSetId, s.version_no AS versionNo, s.source_type AS sourceType, "
            + "s.model_id AS modelId, s.prompt_version AS promptVersion, "
            + "t.tag_type AS tagType, t.tag_value AS tagValue, t.evidence "
            + "FROM tab_insurance_ai_search_tag_set s "
            + "LEFT JOIN tab_insurance_ai_search_tag t ON t.tag_set_id = s.id "
            + "WHERE s.insurance_id = #{productId} AND s.status = 'ACTIVE' ORDER BY t.tag_type, t.normalized_value")
    List<AiSearchTagRow> findActiveTags(@Param("productId") Long productId);

    @Select({"<script>",
            "SELECT s.insurance_id AS productId, s.id AS tagSetId, s.version_no AS versionNo,",
            "s.source_type AS sourceType, s.model_id AS modelId, s.prompt_version AS promptVersion,",
            "t.tag_type AS tagType, t.tag_value AS tagValue, t.evidence",
            "FROM tab_insurance_ai_search_tag_set s",
            "JOIN tab_insurance_ai_search_tag t ON t.tag_set_id = s.id",
            "WHERE s.status = 'ACTIVE' AND s.insurance_id IN",
            "<foreach collection='productIds' item='productId' open='(' separator=',' close=')'>",
            "#{productId}",
            "</foreach>",
            "ORDER BY s.insurance_id, t.tag_type, t.normalized_value",
            "</script>"})
    List<AiSearchTagRow> findActiveTagsByProductIds(@Param("productIds") List<Long> productIds);

    @Select({"<script>",
            "SELECT DISTINCT s.insurance_id",
            "FROM tab_insurance_ai_search_tag_set s",
            "JOIN tab_insurance_ai_search_tag t ON t.tag_set_id = s.id",
            "WHERE s.status = 'ACTIVE' AND (",
            "<foreach collection='keywords' item='keyword' separator=' OR '>",
            "(t.normalized_value LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "OR LOWER(#{keyword}) LIKE CONCAT('%', t.normalized_value, '%'))",
            "</foreach>",
            ")",
            "</script>"})
    List<Long> findActiveProductIdsMatchingKeywords(@Param("keywords") List<String> keywords);
}
