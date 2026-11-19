package com.itheima.bolee.insurance.mapper;

import com.itheima.bolee.insurance.pojo.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Description：保险分类Mapper接口
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT c.id, c.data_state AS dataState, c.category_no AS categoryNo, "
            + "c.category_name AS categoryName, c.category_type AS categoryType, c.leaf_node AS leafNode, "
            + "c.sort_no AS sortNo FROM tab_category c "
            + "WHERE c.data_state = #{dataState} AND c.category_type = #{categoryType} "
            + "AND NOT EXISTS (SELECT 1 FROM tab_category child "
            + "WHERE child.parent_category_no = c.category_no "
            + "AND child.category_type = c.category_type AND child.data_state = #{dataState}) "
            + "ORDER BY c.sort_no, c.id")
    List<Category> findActiveLeafProductCategories(
            @Param("dataState") String dataState, @Param("categoryType") String categoryType);

    @Select("SELECT c.id, c.data_state AS dataState, c.category_no AS categoryNo, "
            + "c.category_name AS categoryName, c.category_type AS categoryType, c.leaf_node AS leafNode "
            + "FROM tab_category c WHERE c.id = #{categoryId} AND c.data_state = #{dataState} "
            + "AND c.category_type = #{categoryType} "
            + "AND NOT EXISTS (SELECT 1 FROM tab_category child "
            + "WHERE child.parent_category_no = c.category_no "
            + "AND child.category_type = c.category_type AND child.data_state = #{dataState})")
    Category findActiveLeafProductCategory(
            @Param("categoryId") Long categoryId,
            @Param("dataState") String dataState,
            @Param("categoryType") String categoryType);

}
