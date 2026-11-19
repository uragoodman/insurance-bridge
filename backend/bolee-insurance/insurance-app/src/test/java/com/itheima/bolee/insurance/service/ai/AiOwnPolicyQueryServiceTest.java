package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyRecordDTO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyDetailResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicySummaryResponseVO;
import com.itheima.bolee.insurance.repository.ai.AiOwnPolicyRepository;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class AiOwnPolicyQueryServiceTest {

    private AiOwnPolicyRepository repository;
    private AiOwnPolicyQueryService service;

    @Before
    public void setUp() {
        repository = mock(AiOwnPolicyRepository.class);
        service = new AiOwnPolicyQueryService(repository);
    }

    @Test
    public void summaryIncludesReferenceNeededForSubsequentOwnerCheckedDetailLookup() {
        AiOwnPolicyRecordDTO record = policy("9001");
        when(repository.hasSelfProfile(1001L)).thenReturn(true);
        when(repository.findOwnActivePolicies(1001L, 21)).thenReturn(Collections.singletonList(record));

        AiOwnPolicySummaryResponseVO response = service.findOwnPolicies("1001");

        assertEquals("FOUND", response.getStatus());
        assertEquals("9001", response.getPolicies().get(0).getPolicyId());
        verify(repository).findOwnActivePolicies(1001L, 21);
    }

    @Test
    public void detailReturnsSameNotFoundForUnknownAndNotOwnedPolicy() {
        when(repository.findOwnActivePolicy(1001L, 9002L)).thenReturn(null);

        AiOwnPolicyDetailResponseVO response = service.findOwnPolicy("1001", "9002");

        assertEquals("NOT_FOUND", response.getStatus());
        assertNull(response.getPolicy());
        verify(repository).findOwnActivePolicy(1001L, 9002L);
    }

    @Test
    public void detailUsesGrantOwnerAndRequestedPolicyReference() {
        when(repository.findOwnActivePolicy(1001L, 9001L)).thenReturn(policy("9001"));

        AiOwnPolicyDetailResponseVO response = service.findOwnPolicy("1001", "9001");

        assertEquals("FOUND", response.getStatus());
        assertEquals("9001", response.getPolicy().getPolicyId());
        verify(repository).findOwnActivePolicy(1001L, 9001L);
    }

    @Test(expected = ResponseStatusException.class)
    public void detailRejectsNonNumericExternalPolicyIdentifiers() {
        service.findOwnPolicy("1001", "POLICY-2026");
    }

    private static AiOwnPolicyRecordDTO policy(String id) {
        AiOwnPolicyRecordDTO record = new AiOwnPolicyRecordDTO();
        record.setPolicyId(id);
        record.setProductName("测试意外险");
        record.setPolicyStatusCode("2");
        record.setCoverageStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        record.setCoverageEndTime(LocalDateTime.of(2026, 12, 31, 23, 59));
        record.setAutoExtensionFlag("1");
        return record;
    }
}
