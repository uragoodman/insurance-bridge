package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.insurance.dto.CustomerRelationVO;
import com.itheima.bolee.insurance.dto.WarrantyOrderVO;
import com.itheima.bolee.insurance.dto.WarrantyVO;
import com.itheima.bolee.insurance.mapper.WarrantyMapper;
import com.itheima.bolee.insurance.pojo.Warranty;
import com.itheima.bolee.insurance.service.ICustomerRelationService;
import com.itheima.bolee.insurance.service.IWarrantyOrderService;
import com.itheima.bolee.insurance.service.impl.WarrantyServiceImpl;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class WarrantyServiceOwnerQueryTest {

    private WarrantyMapper warrantyMapper;
    private IWarrantyOrderService orderService;
    private ICustomerRelationService relationService;
    private WarrantyServiceImpl service;
    private UserVO user;

    @Before
    public void setUp() throws Exception {
        warrantyMapper = mock(WarrantyMapper.class);
        orderService = mock(IWarrantyOrderService.class);
        relationService = mock(ICustomerRelationService.class);
        service = new WarrantyServiceImpl();
        setField(service, "warrantyMapper", warrantyMapper);
        setField(service, "warrantyOrderService", orderService);
        setField(service, "customerRelationService", relationService);
        user = UserVO.builder().id(1001L).build();
    }

    @Test
    public void h5DetailPassesCurrentCustomerToOwnerScopedMapper() {
        Warranty warranty = new Warranty();
        warranty.setId(9001L);
        warranty.setWarrantyNo("W-9001");
        warranty.setInsuranceName("个人意外险");
        when(warrantyMapper.findOwnedByApplicant(9001L, 1001L, "0", "0")).thenReturn(warranty);
        when(orderService.findList(any(WarrantyOrderVO.class))).thenReturn(Collections.<WarrantyOrderVO>emptyList());

        WarrantyVO result = service.findByIdForOwner("9001", user);

        assertEquals("W-9001", result.getWarrantyNo());
        verify(warrantyMapper).findOwnedByApplicant(9001L, 1001L, "0", "0");
    }

    @Test(expected = ProjectException.class)
    public void h5DetailRejectsARecordNotReturnedByOwnerScopedMapper() {
        when(warrantyMapper.findOwnedByApplicant(9002L, 1001L, "0", "0")).thenReturn(null);

        service.findByIdForOwner("9002", user);
    }

    @Test
    public void optionalRelationshipBodyWithNoSelfProfileReturnsEmptyList() {
        when(relationService.findOne(1001L)).thenReturn(null);

        assertTrue(service.myWarrantyVOs(user, null).isEmpty());
        verify(relationService).findOne(1001L);
        verify(relationService, never()).findById(anyString());
    }

    @Test(expected = ProjectException.class)
    public void listRejectsRelationshipOutsideCurrentCustomersHousehold() {
        when(relationService.findHomeList(user)).thenReturn(Collections.<CustomerRelationVO>emptyList());

        service.myWarrantyVOs(user, CustomerRelationVO.builder().id(77L).build());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
