package com.itheima.bolee.insurance.service.impl;

import com.itheima.bolee.insurance.dto.PlanEarningsVO;
import com.itheima.bolee.insurance.pojo.PlanEarnings;
import com.itheima.bolee.insurance.service.IPlanEarningsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InsurancePlanServiceImplPlanEarningsTest {

    @Mock
    private IPlanEarningsService planEarningsService;

    @InjectMocks
    private InsurancePlanServiceImpl service;

    @Test
    public void skipsSaveWhenPlanEarningsListIsEmpty() {
        service.savePlanEarningsIfPresent(Collections.<PlanEarningsVO>emptyList());

        verify(planEarningsService, never()).saveBatch(anyList());
    }

    @Test
    public void skipsSaveWhenPlanEarningsListIsNull() {
        service.savePlanEarningsIfPresent(null);

        verify(planEarningsService, never()).saveBatch(anyList());
    }

    @Test
    public void skipsSaveWhenPlanEarningsEntriesAreNull() {
        service.savePlanEarningsIfPresent(Collections.<PlanEarningsVO>singletonList(null));

        verify(planEarningsService, never()).saveBatch(anyList());
    }

    @Test
    public void skipsSaveWhenAdminFormSendsAnEmptyPayoutTemplate() {
        PlanEarningsVO emptyTemplate = earnings(null, "0", 2,
                "[{\"name\":\"\",\"val\":\"\"}]");

        service.savePlanEarningsIfPresent(Collections.singletonList(emptyTemplate));

        verify(planEarningsService, never()).saveBatch(anyList());
    }

    @Test
    public void savesOnlyNonNullEntriesWhenListContainsOptionalAndPresentPlans() {
        PlanEarningsVO earnings = earnings(101L, "1", 12,
                "[{\"name\":\"第1期\",\"val\":\"25\"}]");
        when(planEarningsService.saveBatch(anyList())).thenReturn(true);

        service.savePlanEarningsIfPresent(Arrays.asList(null, earnings, null));

        ArgumentCaptor<List<PlanEarnings>> captor = listCaptor();
        verify(planEarningsService).saveBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(Long.valueOf(101L), captor.getValue().get(0).getPalnId());
        assertEquals("1", captor.getValue().get(0).getEarningsType());
        assertEquals(Integer.valueOf(12), captor.getValue().get(0).getPeriods());
        assertEquals("[{\"name\":\"第1期\",\"val\":\"25\"}]",
                captor.getValue().get(0).getEarningsJson());
    }

    @Test
    public void savesEveryPresentPlanEarningsEntry() {
        PlanEarningsVO first = earnings(201L, "0", 0,
                "[{\"name\":\"每年\",\"val\":\"10\"}]");
        PlanEarningsVO second = earnings(202L, "1", 6,
                "[{\"name\":\"第1期\",\"val\":\"25\"}]");
        when(planEarningsService.saveBatch(anyList())).thenReturn(true);

        service.savePlanEarningsIfPresent(Arrays.asList(first, second));

        ArgumentCaptor<List<PlanEarnings>> captor = listCaptor();
        verify(planEarningsService).saveBatch(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(Long.valueOf(201L), captor.getValue().get(0).getPalnId());
        assertEquals(Long.valueOf(202L), captor.getValue().get(1).getPalnId());
    }

    @Test
    public void reportsFailureWhenNonEmptyPlanEarningsBatchCannotBeSaved() {
        when(planEarningsService.saveBatch(anyList())).thenReturn(false);

        try {
            service.savePlanEarningsIfPresent(Collections.singletonList(earnings(301L, "1", 12,
                    "[{\"name\":\"第1期\",\"val\":\"20\"}]")));
            fail("Expected the failed batch save to be reported");
        } catch (RuntimeException exception) {
            assertEquals("保存保险方案给付失败", exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<PlanEarnings>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private PlanEarningsVO earnings(Long planId, String type, int periods, String json) {
        PlanEarningsVO earnings = new PlanEarningsVO();
        earnings.setPalnId(planId);
        earnings.setEarningsType(type);
        earnings.setPeriods(periods);
        earnings.setEarningsJson(json);
        return earnings;
    }
}
