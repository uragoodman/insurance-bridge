package com.itheima.bolee.insurance.service.ai;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceChannelUpsertDTO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceChannelAdminVO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceChannelVO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestCreateDTO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestVO;
import com.itheima.bolee.insurance.dto.ai.handoff.CustomerServiceRequestAdminVO;
import com.itheima.bolee.insurance.mapper.ai.CustomerServiceHandoffMapper;
import com.itheima.bolee.insurance.pojo.ai.CustomerServiceChannelDO;
import com.itheima.bolee.insurance.pojo.ai.CustomerServiceRequestDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Java-owned, tenant and customer scoped service-request queue. */
@Service
public class CustomerServiceHandoffService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> REQUEST_TYPES = Arrays.asList(
            "POLICY", "CLAIM", "PURCHASE", "PRODUCT_QUESTION", "OTHER");
    private static final List<String> CONTACT_METHODS = Arrays.asList("PHONE", "IN_APP");
    private static final List<String> STATUSES = Arrays.asList("SUBMITTED", "ACCEPTED", "IN_PROGRESS",
            "RESOLVED", "CANCELLED", "EXPIRED");

    private final CustomerServiceHandoffMapper mapper;
    private final int retentionDays;

    public CustomerServiceHandoffService(CustomerServiceHandoffMapper mapper,
            @Value("${customer-service.request-retention-days:90}") int retentionDays) {
        this.mapper = mapper;
        this.retentionDays = retentionDays;
    }

    public List<CustomerServiceChannelVO> listAvailableChannels(String companyNo) {
        requireTenant(companyNo);
        List<CustomerServiceChannelDO> rows = mapper.listAvailableChannels(companyNo, LocalDateTime.now());
        List<CustomerServiceChannelVO> result = new ArrayList<>();
        for (CustomerServiceChannelDO row : rows) {
            CustomerServiceChannelVO vo = new CustomerServiceChannelVO();
            vo.setId(row.getId());
            vo.setChannelCode(row.getChannelCode());
            vo.setChannelType(row.getChannelType());
            vo.setDisplayName(row.getDisplayName());
            vo.setServiceHoursDesc(row.getServiceHoursDesc());
            vo.setTimezoneId(row.getTimezoneId());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public CustomerServiceRequestVO create(String companyNo, Long ownerUserId,
                                           CustomerServiceRequestCreateDTO input) {
        requireTenant(companyNo);
        requireOwner(ownerUserId);
        if (input == null) throw new IllegalArgumentException("request is required");
        requireUuid(input.getClientRequestId());
        requireChoice(input.getRequestType(), REQUEST_TYPES, "requestType");
        requireChoice(input.getPreferredContactMethod(), CONTACT_METHODS, "preferredContactMethod");
        if (input.getReasonCode() != null && input.getReasonCode().length() > 64) {
            throw new IllegalArgumentException("reasonCode is invalid");
        }
        if (input.getReasonCode() != null && !input.getReasonCode().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("reasonCode is invalid");
        }
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalStateException("customer-service.request-retention-days must be between 1 and 3650");
        }

        String requestHash = hash(String.join("|", input.getRequestType(),
                nullToEmpty(input.getReasonCode()), input.getPreferredContactMethod(),
                input.getChannelId() == null ? "" : input.getChannelId().toString()));
        CustomerServiceRequestDO existing = mapper.findByIdempotency(companyNo, ownerUserId,
                input.getClientRequestId());
        if (existing != null) return sameRequest(existing, requestHash);

        LocalDateTime now = LocalDateTime.now();
        if (input.getChannelId() != null
                && mapper.findAvailableChannel(companyNo, input.getChannelId(), now) == null) {
            throw new IllegalArgumentException("selected customer-service channel is unavailable");
        }
        CustomerServiceRequestDO request = new CustomerServiceRequestDO();
        request.setId(IdWorker.getId());
        request.setRequestNo(UUID.randomUUID().toString());
        request.setCompanyNo(companyNo);
        request.setOwnerUserId(ownerUserId);
        request.setClientRequestId(input.getClientRequestId());
        request.setRequestHash(requestHash);
        request.setSourceType("CUSTOMER_PORTAL");
        request.setChannelId(input.getChannelId());
        request.setRequestType(input.getRequestType());
        request.setReasonCode(input.getReasonCode());
        request.setPreferredContactMethod(input.getPreferredContactMethod());
        request.setStatus("SUBMITTED");
        request.setSubmittedAt(now);
        request.setExpiresAt(now.plusDays(retentionDays));
        request.setCreateTime(now);
        request.setUpdateTime(now);
        request.setCreateBy(ownerUserId);
        request.setUpdateBy(ownerUserId);
        if (mapper.insertRequest(request) == 0) {
            existing = mapper.findByIdempotencyForUpdate(companyNo, ownerUserId, input.getClientRequestId());
            if (existing == null) throw new IllegalStateException("idempotent request could not be recovered");
            return sameRequest(existing, requestHash);
        }
        mapper.insertEvent(request.getId(), companyNo, request.getId(), ownerUserId,
                "CUSTOMER", ownerUserId, "SUBMITTED", null, "SUBMITTED", now);
        return toVO(request);
    }

    public List<CustomerServiceRequestVO> listMine(String companyNo, Long ownerUserId, int limit) {
        requireTenant(companyNo);
        requireOwner(ownerUserId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return toVOs(mapper.listOwned(companyNo, ownerUserId, safeLimit));
    }

    public CustomerServiceRequestVO getMine(String companyNo, Long ownerUserId, Long requestId) {
        requireTenant(companyNo);
        requireOwner(ownerUserId);
        CustomerServiceRequestDO row = mapper.findOwned(companyNo, ownerUserId, requestId);
        return row == null ? null : toVO(row);
    }

    @Transactional
    public boolean cancelMine(String companyNo, Long ownerUserId, Long requestId) {
        requireTenant(companyNo);
        requireOwner(ownerUserId);
        if (requestId == null) throw new IllegalArgumentException("requestId is required");
        LocalDateTime now = LocalDateTime.now();
        CustomerServiceRequestDO request = mapper.findOwned(companyNo, ownerUserId, requestId);
        if (request == null || !"SUBMITTED".equals(request.getStatus())) return false;
        if (mapper.cancelOwned(companyNo, ownerUserId, requestId, now) != 1) return false;
        mapper.insertEvent(IdWorker.getId(), companyNo, requestId, ownerUserId,
                "CUSTOMER", ownerUserId, "CANCELLED", "SUBMITTED", "CANCELLED", now);
        return true;
    }

    public List<CustomerServiceRequestAdminVO> listQueue(String companyNo, String status, int limit) {
        requireTenant(companyNo);
        if (status != null && !STATUSES.contains(status)) throw new IllegalArgumentException("status is invalid");
        List<CustomerServiceRequestAdminVO> result = new ArrayList<>();
        List<CustomerServiceRequestDO> rows = status == null
                ? mapper.listQueue(companyNo, Math.max(1, Math.min(limit, MAX_PAGE_SIZE)))
                : mapper.listQueueByStatus(companyNo, status, Math.max(1, Math.min(limit, MAX_PAGE_SIZE)));
        for (CustomerServiceRequestDO row : rows) {
            CustomerServiceRequestAdminVO vo = new CustomerServiceRequestAdminVO();
            vo.setId(row.getId()); vo.setRequestNo(row.getRequestNo()); vo.setOwnerUserId(row.getOwnerUserId());
            vo.setRequestType(row.getRequestType()); vo.setReasonCode(row.getReasonCode());
            vo.setPreferredContactMethod(row.getPreferredContactMethod()); vo.setChannelId(row.getChannelId());
            vo.setStatus(row.getStatus()); vo.setAssignedOperatorId(row.getAssignedOperatorId());
            vo.setSubmittedAt(row.getSubmittedAt()); vo.setAcceptedAt(row.getAcceptedAt());
            vo.setResolvedAt(row.getResolvedAt()); vo.setExpiresAt(row.getExpiresAt());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public boolean transition(String companyNo, Long operatorId, Long requestId, String toStatus) {
        requireTenant(companyNo);
        requireOwner(operatorId);
        if (requestId == null) throw new IllegalArgumentException("requestId is required");
        if (!("ACCEPTED".equals(toStatus) || "IN_PROGRESS".equals(toStatus) || "RESOLVED".equals(toStatus))) {
            throw new IllegalArgumentException("target status is invalid");
        }
        CustomerServiceRequestDO request = mapper.findQueueRequest(companyNo, requestId);
        if (request == null) return false;
        String from = request.getStatus();
        boolean allowed = ("SUBMITTED".equals(from) && "ACCEPTED".equals(toStatus))
                || ("ACCEPTED".equals(from) && "IN_PROGRESS".equals(toStatus))
                || ("IN_PROGRESS".equals(from) && "RESOLVED".equals(toStatus));
        if (!allowed) return false;
        LocalDateTime now = LocalDateTime.now();
        if (mapper.transition(companyNo, requestId, from, toStatus, operatorId, now) != 1) return false;
        mapper.insertEvent(IdWorker.getId(), companyNo, requestId, request.getOwnerUserId(),
                "OPERATOR", operatorId, toStatus, from, toStatus, now);
        return true;
    }

    @Transactional
    public void upsertChannel(String companyNo, Long operatorId, CustomerServiceChannelUpsertDTO input) {
        requireTenant(companyNo);
        requireOwner(operatorId);
        if (input == null) throw new IllegalArgumentException("channel is required");
        requireText(input.getChannelCode(), 64, "channelCode");
        requireChoice(input.getChannelType(), Arrays.asList("PHONE", "IN_APP", "EMAIL", "WEB"), "channelType");
        requireText(input.getDisplayName(), 100, "displayName");
        requireText(input.getTargetValue(), 512, "targetValue");
        requireText(input.getTimezoneId(), 64, "timezoneId");
        requireChoice(input.getStatus(), Arrays.asList("ENABLED", "DISABLED"), "status");
        if (!input.getChannelCode().matches("[A-Z0-9_-]{2,64}")) {
            throw new IllegalArgumentException("channelCode is invalid");
        }
        if (input.getServiceHoursDesc() != null && input.getServiceHoursDesc().length() > 512) {
            throw new IllegalArgumentException("serviceHoursDesc is invalid");
        }
        LocalDateTime now = LocalDateTime.now();
        CustomerServiceChannelDO channel = new CustomerServiceChannelDO();
        channel.setId(IdWorker.getId());
        channel.setCompanyNo(companyNo);
        channel.setChannelCode(input.getChannelCode());
        channel.setChannelType(input.getChannelType());
        channel.setDisplayName(input.getDisplayName());
        channel.setTargetValue(input.getTargetValue());
        channel.setServiceHoursDesc(input.getServiceHoursDesc());
        channel.setTimezoneId(input.getTimezoneId());
        channel.setStatus(input.getStatus());
        channel.setSortNo(input.getSortNo() == null ? 0 : input.getSortNo());
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        channel.setCreateBy(operatorId);
        channel.setUpdateBy(operatorId);
        mapper.upsertChannel(channel);
    }

    public List<CustomerServiceChannelAdminVO> listChannelsForAdmin(String companyNo) {
        requireTenant(companyNo);
        List<CustomerServiceChannelAdminVO> result = new ArrayList<>();
        for (CustomerServiceChannelDO row : mapper.listChannelsForAdmin(companyNo)) {
            CustomerServiceChannelAdminVO vo = new CustomerServiceChannelAdminVO();
            vo.setId(row.getId()); vo.setChannelCode(row.getChannelCode());
            vo.setChannelType(row.getChannelType()); vo.setDisplayName(row.getDisplayName());
            vo.setTargetValue(row.getTargetValue()); vo.setServiceHoursDesc(row.getServiceHoursDesc());
            vo.setTimezoneId(row.getTimezoneId()); vo.setStatus(row.getStatus()); vo.setSortNo(row.getSortNo());
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public int purgeExpired(int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<CustomerServiceRequestDO> expired = mapper.findExpired(now, Math.max(1, Math.min(limit, 500)));
        if (expired.isEmpty()) return 0;
        Map<String, Boolean> tenants = new LinkedHashMap<>();
        for (CustomerServiceRequestDO row : expired) {
            tenants.put(row.getCompanyNo(), Boolean.TRUE);
        }
        int deleted = 0;
        for (String companyNo : tenants.keySet()) {
            mapper.deleteExpiredEvents(companyNo, now);
            deleted += mapper.deleteExpiredRequests(companyNo, now);
        }
        return deleted;
    }

    private CustomerServiceRequestVO sameRequest(CustomerServiceRequestDO request, String requestHash) {
        if (!requestHash.equals(request.getRequestHash())) {
            throw new IllegalArgumentException("clientRequestId was already used with a different request");
        }
        return toVO(request);
    }

    private List<CustomerServiceRequestVO> toVOs(List<CustomerServiceRequestDO> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<CustomerServiceRequestVO> result = new ArrayList<>();
        for (CustomerServiceRequestDO row : rows) result.add(toVO(row));
        return result;
    }

    private CustomerServiceRequestVO toVO(CustomerServiceRequestDO row) {
        CustomerServiceRequestVO vo = new CustomerServiceRequestVO();
        vo.setId(row.getId());
        vo.setRequestNo(row.getRequestNo());
        vo.setRequestType(row.getRequestType());
        vo.setReasonCode(row.getReasonCode());
        vo.setPreferredContactMethod(row.getPreferredContactMethod());
        vo.setStatus(row.getStatus());
        vo.setSubmittedAt(row.getSubmittedAt());
        vo.setAcceptedAt(row.getAcceptedAt());
        vo.setResolvedAt(row.getResolvedAt());
        vo.setCancelledAt(row.getCancelledAt());
        return vo;
    }

    private static void requireTenant(String companyNo) {
        if (companyNo == null || companyNo.trim().isEmpty()) throw new IllegalArgumentException("companyNo is required");
    }
    private static void requireOwner(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("authenticated user is required");
    }
    private static void requireChoice(String value, List<String> allowed, String name) {
        if (!allowed.contains(value)) throw new IllegalArgumentException(name + " is invalid");
    }
    private static void requireText(String value, int maxLength, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
    private static void requireUuid(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("clientRequestId must be a UUID");
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("clientRequestId must be a UUID");
        }
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
