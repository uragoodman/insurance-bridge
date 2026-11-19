package com.itheima.bolee.insurance.repository.ai;

import com.itheima.bolee.framework.commons.constant.basic.SuperConstant;
import com.itheima.bolee.framework.commons.enums.relations.RelationEnum;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyRecordDTO;
import com.itheima.bolee.insurance.mapper.ai.AiOwnPolicyMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Database adapter that only returns current-user applicant policy summaries. */
@Repository
public class AiOwnPolicyRepository {

    private final AiOwnPolicyMapper mapper;

    public AiOwnPolicyRepository(AiOwnPolicyMapper mapper) {
        this.mapper = mapper;
    }

    public List<AiOwnPolicyRecordDTO> findOwnActivePolicies(Long ownerUserId, int limit) {
        return mapper.findOwnActivePolicies(
                ownerUserId,
                RelationEnum.SELF.getRelation(),
                SuperConstant.DATA_STATE_0,
                limit);
    }

    public List<AiOwnPolicyRecordDTO> findOwnPoliciesPage(
            Long ownerUserId, java.time.LocalDateTime cursorCreateTime, Long cursorPolicyId, int limit) {
        return mapper.findOwnPoliciesPage(
                ownerUserId,
                RelationEnum.SELF.getRelation(),
                SuperConstant.DATA_STATE_0,
                cursorCreateTime,
                cursorPolicyId,
                limit);
    }

    public AiOwnPolicyRecordDTO findOwnActivePolicy(Long ownerUserId, Long policyId) {
        return mapper.findOwnActivePolicy(
                ownerUserId,
                policyId,
                RelationEnum.SELF.getRelation(),
                SuperConstant.DATA_STATE_0);
    }

    public AiOwnPolicyRecordDTO findOwnCustomerPolicy(Long ownerUserId, Long policyId) {
        return mapper.findOwnCustomerPolicy(
                ownerUserId,
                policyId,
                RelationEnum.SELF.getRelation(),
                SuperConstant.DATA_STATE_0);
    }

    public boolean hasSelfProfile(Long ownerUserId) {
        return mapper.hasSelfProfile(
                ownerUserId,
                RelationEnum.SELF.getRelation(),
                SuperConstant.DATA_STATE_0);
    }
}
