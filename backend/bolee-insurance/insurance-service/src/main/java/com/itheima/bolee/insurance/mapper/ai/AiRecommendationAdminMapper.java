package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.dto.ai.admin.AiRecommendationAdminProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiRecommendationAdminMapper {

    @Select({"<script>",
            "SELECT p.id AS productId, p.insurance_name AS productName, p.publish_number AS publishNumber,",
            "c.id AS categoryId, c.category_name AS categoryName, p.sort_no AS sortNo,",
            "p.ai_recommend_enabled AS aiRecommendEnabled, p.insurance_state AS insuranceState",
            "FROM tab_insurance p JOIN tab_category c ON c.category_no = p.category_no",
            "AND c.category_type = '1' AND c.data_state = '0'",
            "AND NOT EXISTS (SELECT 1 FROM tab_category child WHERE child.parent_category_no = c.category_no",
            "AND child.category_type = c.category_type AND child.data_state = '0')",
            "WHERE p.data_state = '0'",
            "AND (#{categoryId} IS NULL OR c.id = #{categoryId})",
            "AND (#{keyword} IS NULL OR p.insurance_name LIKE CONCAT('%', #{keyword}, '%'))",
            "<if test='recommendedOnly'>AND p.ai_recommend_enabled = 1</if>",
            "ORDER BY c.sort_no, p.sort_no, p.id LIMIT #{limit}",
            "</script>"})
    List<AiRecommendationAdminProductVO> findActiveProducts(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("recommendedOnly") boolean recommendedOnly,
            @Param("limit") int limit);

    @Update("UPDATE tab_insurance SET ai_recommend_enabled = #{enabled}, update_time = NOW(3), "
            + "update_by = #{actorId} WHERE id = #{productId} AND data_state = '0' "
            + "AND (insurance_state = '0' OR #{enabled} = 0)")
    int updateRecommendationFlag(@Param("productId") Long productId,
                                 @Param("enabled") boolean enabled,
                                 @Param("actorId") Long actorId);
}
