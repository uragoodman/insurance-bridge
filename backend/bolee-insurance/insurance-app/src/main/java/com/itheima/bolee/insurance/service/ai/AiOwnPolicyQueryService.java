package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyRecordDTO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyDetailResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyPageResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicySummaryItemVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicySummaryResponseVO;
import com.itheima.bolee.insurance.repository.ai.AiOwnPolicyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** Use case for querying only policies owned by the user in the short-lived Java grant. */
@Service
public class AiOwnPolicyQueryService {

    private static final int MAX_RESULTS = 20;
    private final AiOwnPolicyRepository repository;

    public AiOwnPolicyQueryService(AiOwnPolicyRepository repository) {
        this.repository = repository;
    }

    public AiOwnPolicySummaryResponseVO findOwnPolicies(String grantUserId) {
        Long ownerUserId = parseOwnerUserId(grantUserId);
        if (!repository.hasSelfProfile(ownerUserId)) {
            return new AiOwnPolicySummaryResponseVO(
                    "PROFILE_NOT_LINKED", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    new ArrayList<AiOwnPolicySummaryItemVO>(), 0, false);
        }
        List<AiOwnPolicyRecordDTO> records = repository.findOwnActivePolicies(ownerUserId, MAX_RESULTS + 1);
        boolean truncated = records.size() > MAX_RESULTS;
        int end = Math.min(records.size(), MAX_RESULTS);
        List<AiOwnPolicySummaryItemVO> summaries = new ArrayList<AiOwnPolicySummaryItemVO>(end);
        for (int index = 0; index < end; index++) {
            summaries.add(toSummary(records.get(index)));
        }
        return new AiOwnPolicySummaryResponseVO(
                summaries.isEmpty() ? "NO_ACTIVE_POLICIES" : "FOUND",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                summaries,
                summaries.size(),
                truncated);
    }

    public AiOwnPolicyDetailResponseVO findOwnPolicy(String grantUserId, String policyId) {
        Long ownerUserId = parseOwnerUserId(grantUserId);
        Long parsedPolicyId = parsePolicyId(policyId);
        AiOwnPolicyRecordDTO record = repository.findOwnActivePolicy(ownerUserId, parsedPolicyId);
        String asOf = OffsetDateTime.now(ZoneOffset.UTC).toString();
        if (record == null) {
            return new AiOwnPolicyDetailResponseVO("NOT_FOUND", asOf, null);
        }
        return new AiOwnPolicyDetailResponseVO("FOUND", asOf, toSummary(record));
    }

    /** Customer detail endpoint includes historical statuses but keeps the same owner check and field whitelist. */
    public AiOwnPolicyDetailResponseVO findOwnCustomerPolicy(String userId, String policyId) {
        Long ownerUserId = parseOwnerUserId(userId);
        Long parsedPolicyId = parsePolicyId(policyId);
        AiOwnPolicyRecordDTO record = repository.findOwnCustomerPolicy(ownerUserId, parsedPolicyId);
        String asOf = OffsetDateTime.now(ZoneOffset.UTC).toString();
        return record == null
                ? new AiOwnPolicyDetailResponseVO("NOT_FOUND", asOf, null)
                : new AiOwnPolicyDetailResponseVO("FOUND", asOf, toSummary(record));
    }

    /** Returns a stable keyset page; a cursor is only a position and never grants access. */
    public AiOwnPolicyPageResponseVO findOwnPoliciesPage(String userId, String cursor, int requestedLimit) {
        Long ownerUserId = parseOwnerUserId(userId);
        if (requestedLimit < 1 || requestedLimit > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 50");
        }
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (StringUtils.hasText(cursor)) {
            if (cursor.length() > 256) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid policy page cursor");
            }
            String decoded;
            try {
                decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = decoded.lastIndexOf('|');
                if (separator < 1) throw new IllegalArgumentException();
                cursorTime = LocalDateTime.parse(decoded.substring(0, separator));
                cursorId = parsePolicyId(decoded.substring(separator + 1));
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid policy page cursor");
            }
        }
        if (!repository.hasSelfProfile(ownerUserId)) {
            return new AiOwnPolicyPageResponseVO("PROFILE_NOT_LINKED", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    new ArrayList<AiOwnPolicySummaryItemVO>(), null, false);
        }
        List<AiOwnPolicyRecordDTO> records = repository.findOwnPoliciesPage(
                ownerUserId, cursorTime, cursorId, requestedLimit + 1);
        boolean hasMore = records.size() > requestedLimit;
        int count = Math.min(records.size(), requestedLimit);
        List<AiOwnPolicySummaryItemVO> policies = new ArrayList<AiOwnPolicySummaryItemVO>(count);
        for (int index = 0; index < count; index++) policies.add(toSummary(records.get(index)));
        String nextCursor = null;
        if (hasMore && count > 0) {
            AiOwnPolicyRecordDTO last = records.get(count - 1);
            String value = last.getCreateTime().toString() + "|" + last.getPolicyId();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
        return new AiOwnPolicyPageResponseVO(policies.isEmpty() ? "NO_POLICIES" : "FOUND",
                OffsetDateTime.now(ZoneOffset.UTC).toString(), policies, nextCursor, hasMore);
    }

    private static Long parseOwnerUserId(String userId) {
        if (!StringUtils.hasText(userId) || !userId.matches("[0-9]{1,19}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent user context");
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent user context");
        }
    }

    private static Long parsePolicyId(String policyId) {
        if (!StringUtils.hasText(policyId) || !policyId.matches("[0-9]{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid policy reference");
        }
        try {
            return Long.valueOf(policyId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid policy reference");
        }
    }

    private static AiOwnPolicySummaryItemVO toSummary(AiOwnPolicyRecordDTO record) {
        String status = record.getPolicyStatusCode();
        String statusCode;
        String statusLabel;
        if ("0".equals(status)) {
            statusCode = "PENDING_PAYMENT";
            statusLabel = "待付款";
        } else if ("1".equals(status)) {
            statusCode = "PENDING_EFFECTIVE";
            statusLabel = "待生效";
        } else if ("2".equals(status)) {
            statusCode = "IN_FORCE";
            statusLabel = "保障中";
        } else if ("3".equals(status)) {
            statusCode = "OVERDUE_SUSPENDED";
            statusLabel = "逾期中止";
        } else if ("4".equals(status)) {
            statusCode = "CLAIM_TERMINATED";
            statusLabel = "理赔终止";
        } else if ("5".equals(status)) {
            statusCode = "REINSTATEMENT_SUSPENDED";
            statusLabel = "复效中止";
        } else if ("6".equals(status)) {
            statusCode = "REINSTATEMENT_TERMINATED";
            statusLabel = "复效终止";
        } else if ("7".equals(status)) {
            statusCode = "MATURED";
            statusLabel = "满期终止";
        } else if ("8".equals(status)) {
            statusCode = "DECLINED";
            statusLabel = "拒保";
        } else if ("9".equals(status)) {
            statusCode = "COOLING_OFF_SURRENDER";
            statusLabel = "犹豫期退保";
        } else if ("10".equals(status)) {
            statusCode = "SURRENDERED";
            statusLabel = "协议退保";
        } else {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unsupported policy status");
        }
        return new AiOwnPolicySummaryItemVO(
                record.getPolicyId(),
                record.getProductName(),
                statusCode,
                statusLabel,
                format(record.getCoverageStartTime()),
                format(record.getCoverageEndTime()),
                autoExtensionEnabled(record.getAutoExtensionFlag()));
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static Boolean autoExtensionEnabled(String value) {
        if ("0".equals(value)) return Boolean.TRUE;
        if ("1".equals(value)) return Boolean.FALSE;
        return null;
    }
}
