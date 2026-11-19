package com.itheima.bolee.insurance.service.impl;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class InsuranceConditionServiceImplIdBindingTest {

    @Test
    public void batchConditionLookupBindsProductIdsAsVarcharValues() throws Exception {
        Method converter = InsuranceConditionServiceImpl.class
                .getDeclaredMethod("toInsuranceIdStrings", Long[].class);
        converter.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> boundIds = (List<String>) converter.invoke(null,
                (Object) new Long[]{8800000000000000001L});

        assertTrue(boundIds.get(0) instanceof String);
        assertTrue("8800000000000000001".equals(boundIds.get(0)));
    }
}
