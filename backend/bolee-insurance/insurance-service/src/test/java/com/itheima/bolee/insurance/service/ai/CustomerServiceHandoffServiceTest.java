package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestCreateDTO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestVO;
import com.itheima.bolee.insurance.mapper.ai.CustomerServiceHandoffMapper;
import com.itheima.bolee.insurance.pojo.ai.CustomerServiceRequestDO;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class CustomerServiceHandoffServiceTest {
    private CustomerServiceHandoffMapper mapper;
    private CustomerServiceHandoffService service;

    @Before
    public void setUp() {
        mapper = mock(CustomerServiceHandoffMapper.class);
        service = new CustomerServiceHandoffService(mapper, 90);
    }

    @Test
    public void createPersistsCustomerScopedRequestAndSubmissionEvent() {
        CustomerServiceRequestCreateDTO input = request(UUID.randomUUID().toString());
        when(mapper.insertRequest(any(CustomerServiceRequestDO.class))).thenReturn(1);

        CustomerServiceRequestVO result = service.create("tenant-1", 27L, input);

        assertNotNull(result.getId());
        assertNotNull(result.getRequestNo());
        assertEquals("SUBMITTED", result.getStatus());
        verify(mapper).insertRequest(argThat(row -> "tenant-1".equals(row.getCompanyNo())
                && Long.valueOf(27L).equals(row.getOwnerUserId())
                && "CUSTOMER_PORTAL".equals(row.getSourceType())
                && "POLICY".equals(row.getRequestType())));
        verify(mapper).insertEvent(any(Long.class), eq("tenant-1"), any(Long.class), eq(27L),
                eq("CUSTOMER"), eq(27L), eq("SUBMITTED"), eq(null), eq("SUBMITTED"), any());
    }

    @Test
    public void duplicateRequestReplaysSameRequestWithoutInsertingAgain() {
        String key = UUID.randomUUID().toString();
        CustomerServiceRequestCreateDTO input = request(key);
        CustomerServiceRequestDO existing = new CustomerServiceRequestDO();
        existing.setId(910L);
        existing.setRequestNo("existing-request");
        existing.setCompanyNo("tenant-1");
        existing.setOwnerUserId(27L);
        existing.setClientRequestId(key);
        existing.setRequestType("POLICY");
        existing.setPreferredContactMethod("IN_APP");
        existing.setStatus("SUBMITTED");
        existing.setRequestHash("2d0e1e51c8c7e0c8aef6648a50da80b4ed2d91a3bebd16a2dd64f6c46ae50d08");
        // Request hash is SHA-256("POLICY||IN_APP|"); compute through a first successful request to avoid
        // duplicating the production hashing implementation in this test.
        when(mapper.findByIdempotency("tenant-1", 27L, key)).thenReturn(null);
        when(mapper.insertRequest(any(CustomerServiceRequestDO.class))).thenAnswer(invocation -> {
            CustomerServiceRequestDO inserted = invocation.getArgument(0);
            existing.setId(inserted.getId());
            existing.setRequestNo(inserted.getRequestNo());
            existing.setRequestHash(inserted.getRequestHash());
            existing.setSubmittedAt(inserted.getSubmittedAt());
            existing.setExpiresAt(inserted.getExpiresAt());
            return 1;
        });
        CustomerServiceRequestVO first = service.create("tenant-1", 27L, input);
        reset(mapper);
        when(mapper.findByIdempotency("tenant-1", 27L, key)).thenReturn(existing);

        CustomerServiceRequestVO replay = service.create("tenant-1", 27L, input);

        assertEquals(first.getId(), replay.getId());
        assertEquals(first.getRequestNo(), replay.getRequestNo());
        verify(mapper, never()).insertRequest(any(CustomerServiceRequestDO.class));
        verify(mapper, never()).insertEvent(any(Long.class), anyString(), any(Long.class), any(Long.class),
                anyString(), any(Long.class), anyString(), any(), anyString(), any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRequiresAuthenticatedCustomer() {
        service.create("tenant-1", null, request(UUID.randomUUID().toString()));
    }

    private CustomerServiceRequestCreateDTO request(String clientRequestId) {
        CustomerServiceRequestCreateDTO input = new CustomerServiceRequestCreateDTO();
        input.setClientRequestId(clientRequestId);
        input.setRequestType("POLICY");
        input.setPreferredContactMethod("IN_APP");
        return input;
    }
}
