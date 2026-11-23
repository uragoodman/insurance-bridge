package com.itheima.bolee.insurance.repository.ai.terms;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceAuditVO;
import com.itheima.bolee.insurance.mapper.ai.terms.InsuranceTermSourceAuditMapper;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceAuditDO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InsuranceTermSourceAuditRepository {
    private final InsuranceTermSourceAuditMapper mapper;
    private final IdentifierGenerator identifierGenerator;

    public InsuranceTermSourceAuditRepository(InsuranceTermSourceAuditMapper mapper,
                                              IdentifierGenerator identifierGenerator) {
        this.mapper = mapper;
        this.identifierGenerator = identifierGenerator;
    }

    public void append(Long sourceId, String action, String fromStatus, String toStatus,
                       Long actorId, String note) {
        InsuranceTermSourceAuditDO audit = new InsuranceTermSourceAuditDO();
        audit.setId(((Number) identifierGenerator.nextId(audit)).longValue());
        audit.setSourceId(sourceId);
        audit.setAction(action);
        audit.setFromStatus(fromStatus);
        audit.setToStatus(toStatus);
        audit.setActorId(actorId);
        audit.setNote(note);
        audit.setCreateTime(LocalDateTime.now());
        if (mapper.insert(audit) != 1) throw new IllegalStateException("Could not persist source audit event");
    }

    public List<InsuranceTermSourceAuditVO> list(Long sourceId) { return mapper.listBySourceId(sourceId); }
    public boolean hasEvents(Long sourceId) { return mapper.countBySourceId(sourceId) > 0; }
}
