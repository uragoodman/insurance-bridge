package com.itheima.bolee.insurance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.bolee.insurance.pojo.Insurance;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InsuranceServiceImplConditionFilterTest {

    @Test
    public void conditionFilterUsesCorrelatedExistsAndBoundValues() throws Exception {
        QueryWrapper<Insurance> queryWrapper = new QueryWrapper<>();
        Method appendConditionFilter = InsuranceServiceImpl.class.getDeclaredMethod(
                "appendConditionFilter", QueryWrapper.class, java.util.List.class);
        appendConditionFilter.setAccessible(true);
        appendConditionFilter.invoke(null, queryWrapper, Arrays.asList("18", "65"));

        String sql = queryWrapper.getSqlSegment();
        assertTrue(sql, sql.contains("EXISTS (SELECT 1 FROM tab_insurance_condition ic"));
        assertTrue(sql, sql.contains("ic.insurance_id = CAST(tab_insurance.id AS CHAR)"));
        assertTrue(sql, sql.contains("JSON_CONTAINS(ic.condition_val, JSON_ARRAY("));
        assertEquals(2, queryWrapper.getParamNameValuePairs().size());
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("18"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("65"));
    }
}
