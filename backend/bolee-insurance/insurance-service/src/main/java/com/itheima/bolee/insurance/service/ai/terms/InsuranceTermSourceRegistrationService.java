package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceRegistrationService {
    private final InsuranceTermSourceRepository sourceRepository;
    private final InsuranceTermSourceAuditRepository auditRepository;

    public InsuranceTermSourceRegistrationService(InsuranceTermSourceRepository sourceRepository,
                                                  InsuranceTermSourceAuditRepository auditRepository) {
        this.sourceRepository = sourceRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(InsuranceTermSourceDO source) {
        sourceRepository.insert(source);
        auditRepository.append(source.getId(), "UPLOADED", null, "DRAFT",
                source.getUploadedBy(), "Source file uploaded");
    }
}
