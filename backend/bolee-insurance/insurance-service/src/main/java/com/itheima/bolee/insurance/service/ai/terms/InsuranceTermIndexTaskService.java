package com.itheima.bolee.insurance.service.ai.terms;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermIndexTaskVO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermIndexTaskDO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermIndexTaskRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermIndexTaskService {
    private static final int MAX_ATTEMPTS = 8;
    private static final int CLAIM_LEASE_SECONDS = 180;
    private static final int MAX_CLAIM_CONFLICT_RETRIES = 32;

    private final InsuranceTermIndexTaskRepository taskRepository;
    private final InsuranceTermSourceRepository sourceRepository;
    private final InsuranceTermSourceContentReader contentReader;
    private final InsuranceTermSourceAuditRepository auditRepository;
    private final IdentifierGenerator identifierGenerator;

    public InsuranceTermIndexTaskService(InsuranceTermIndexTaskRepository taskRepository,
                                         InsuranceTermSourceRepository sourceRepository,
                                         InsuranceTermSourceContentReader contentReader,
                                         InsuranceTermSourceAuditRepository auditRepository,
                                         IdentifierGenerator identifierGenerator) {
        this.taskRepository = taskRepository;
        this.sourceRepository = sourceRepository;
        this.contentReader = contentReader;
        this.auditRepository = auditRepository;
        this.identifierGenerator = identifierGenerator;
    }

    public void enqueue(InsuranceTermSourceDO source, String action) {
        if (!"INDEX".equals(action) && !"REMOVE".equals(action)) {
            throw new IllegalArgumentException("Unsupported term index task action");
        }
        LocalDateTime now = LocalDateTime.now();
        InsuranceTermIndexTaskDO task = new InsuranceTermIndexTaskDO();
        task.setId(((Number) identifierGenerator.nextId(task)).longValue());
        task.setSourceId(source.getId());
        task.setAction(action);
        task.setSourceSha256(source.getSha256());
        task.setAttempts(0);
        task.setNextAttemptAt(now);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskRepository.insert(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InsuranceTermIndexTaskVO claimNext() {
        for (int attempt = 0; attempt < MAX_CLAIM_CONFLICT_RETRIES; attempt++) {
            LocalDateTime now = LocalDateTime.now();
            InsuranceTermIndexTaskDO exhausted = taskRepository.findNextExhaustedLease(now, MAX_ATTEMPTS);
            if (exhausted != null) {
                InsuranceTermIndexTaskDO locked = taskRepository.findByIdForUpdate(exhausted.getId());
                if (locked != null && "PROCESSING".equals(locked.getTaskStatus())
                        && locked.getLeaseUntil() != null && locked.getLeaseUntil().isBefore(now)
                        && locked.getAttempts() >= MAX_ATTEMPTS
                        && taskRepository.failExpiredLease(locked.getId(), now, MAX_ATTEMPTS,
                        "Task lease expired after the maximum number of attempts")) {
                    if ("INDEX".equals(locked.getAction())) {
                        sourceRepository.markIndexFailed(locked.getSourceId(), locked.getSourceSha256(),
                                "Indexer lease expired after the maximum number of attempts", now);
                    } else if ("REMOVE".equals(locked.getAction())) {
                        sourceRepository.markIndexRemovalFailed(locked.getSourceId(), locked.getSourceSha256(),
                                "Indexer lease expired after the maximum number of attempts", now);
                    }
                }
                continue;
            }
            InsuranceTermIndexTaskDO candidate = taskRepository.findNextClaimable(now);
            if (candidate == null) return null;
            String leaseToken = UUID.randomUUID().toString();
            if (!taskRepository.claim(candidate.getId(), now, now.plusSeconds(CLAIM_LEASE_SECONDS), leaseToken)) continue;
            candidate.setLeaseToken(leaseToken);
            candidate.setTaskStatus("PROCESSING");
            candidate.setLeaseUntil(now.plusSeconds(CLAIM_LEASE_SECONDS));
            InsuranceTermSourceDO source = sourceRepository.findById(candidate.getSourceId());
            if (!isCurrentTask(candidate, source)) {
                taskRepository.complete(candidate.getId(), leaseToken, LocalDateTime.now());
                continue;
            }
            return toView(candidate, source);
        }
        return null;
    }

    public InputStream downloadTaskContent(Long taskId, String leaseToken) {
        InsuranceTermIndexTaskDO task = requireProcessingTask(taskId, leaseToken);
        if (!"INDEX".equals(task.getAction())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This task does not require source content");
        }
        InsuranceTermSourceDO source = sourceRepository.findById(task.getSourceId());
        if (!isCurrentTask(task, source) || !"PUBLISHING".equals(source.getStatus())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Source is no longer eligible for indexing");
        }
        try {
            if (!task.getSourceSha256().equalsIgnoreCase(source.getSha256())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Source integrity check failed");
            }
            return contentReader.openVerified(source);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve source document");
        }
    }

    public InsuranceTermIndexTaskVO getLeasedTask(Long taskId, String leaseToken) {
        InsuranceTermIndexTaskDO task = requireProcessingTask(taskId, leaseToken);
        InsuranceTermSourceDO source = sourceRepository.findById(task.getSourceId());
        if (!isCurrentTask(task, source)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Source is no longer eligible for indexing");
        }
        return toView(task, source);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long taskId, String leaseToken, String indexedSha256) {
        InsuranceTermIndexTaskDO task = taskRepository.findByIdForUpdate(taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Index task was not found");
        if ("DONE".equals(task.getTaskStatus())) {
            if (task.getLeaseToken() != null && task.getLeaseToken().equals(leaseToken)
                    && task.getSourceSha256().equalsIgnoreCase(indexedSha256)) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed task hash does not match");
        }
        if (!isValidLease(task, leaseToken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not currently leased");
        }
        if (!StringUtils.hasText(indexedSha256) || !task.getSourceSha256().equalsIgnoreCase(indexedSha256.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Indexed source hash does not match task hash");
        }
        InsuranceTermSourceDO source = sourceRepository.findById(task.getSourceId());
        if ("INDEX".equals(task.getAction())) {
            if (source != null && "PUBLISHING".equals(source.getStatus())
                    && task.getSourceSha256().equalsIgnoreCase(source.getSha256())) {
                LocalDateTime now = LocalDateTime.now();
                if (!sourceRepository.markIndexed(source.getId(), task.getSourceSha256(), now)) {
                    if (!sourceRepository.withdrawInvalidPublishing(source.getId(), task.getSourceSha256(), now)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Could not finalize indexed source version");
                    }
                    InsuranceTermSourceDO withdrawn = sourceRepository.findById(source.getId());
                    enqueue(withdrawn, "REMOVE");
                    auditRepository.append(source.getId(), "EXPIRED_DURING_INDEXING", "PUBLISHING",
                            "WITHDRAWN", null, "Source or product version expired during indexing");
                }
            } else if (source != null && "WITHDRAWN".equals(source.getStatus())
                    && task.getSourceSha256().equalsIgnoreCase(source.getSha256())) {
                // The old INDEX worker may have committed vectors after an earlier REMOVE.
                // Queue a newer REMOVE after its completion callback to restore the final state.
                enqueue(source, "REMOVE");
            }
        } else if ("REMOVE".equals(task.getAction())) {
            if (source != null && "WITHDRAWN".equals(source.getStatus())
                    && task.getSourceSha256().equalsIgnoreCase(source.getSha256())
                    && !sourceRepository.markIndexRemoved(source.getId(), task.getSourceSha256(), LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not finalize source removal");
            }
        }
        if (!taskRepository.complete(taskId, leaseToken, LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task lease changed before completion");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void fail(Long taskId, String leaseToken, String reason, Boolean retryable) {
        InsuranceTermIndexTaskDO task = taskRepository.findByIdForUpdate(taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Index task was not found");
        if (!isValidLease(task, leaseToken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task lease is not active or has changed");
        }
        String normalizedReason = reason.trim().replaceAll("[\\r\\n\\t]", " ");
        if (normalizedReason.length() > 1000) normalizedReason = normalizedReason.substring(0, 1000);
        LocalDateTime now = LocalDateTime.now();
        boolean terminal = Boolean.FALSE.equals(retryable) || task.getAttempts() >= MAX_ATTEMPTS;
        int delaySeconds = (int) Math.min(300, Math.pow(2, Math.min(task.getAttempts(), 8)) * 5);
        if (!taskRepository.fail(taskId, terminal ? "FAILED" : "RETRY",
                terminal ? now : now.plusSeconds(delaySeconds), normalizedReason, leaseToken, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task lease changed before failure was recorded");
        }
        if (terminal) {
            if ("INDEX".equals(task.getAction())) {
                sourceRepository.markIndexFailed(task.getSourceId(), task.getSourceSha256(), normalizedReason, now);
            } else if ("REMOVE".equals(task.getAction())) {
                sourceRepository.markIndexRemovalFailed(task.getSourceId(), task.getSourceSha256(), normalizedReason, now);
            }
        }
    }

    public void heartbeat(Long taskId, String leaseToken) {
        LocalDateTime now = LocalDateTime.now();
        if (!taskRepository.heartbeat(taskId, leaseToken, now, now.plusSeconds(CLAIM_LEASE_SECONDS))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task lease is not active or has changed");
        }
    }

    private InsuranceTermIndexTaskDO requireTask(Long id) {
        InsuranceTermIndexTaskDO task = taskRepository.findById(id);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Index task was not found");
        return task;
    }

    private InsuranceTermIndexTaskDO requireProcessingTask(Long id, String leaseToken) {
        InsuranceTermIndexTaskDO task = requireTask(id);
        if (!isValidLease(task, leaseToken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task lease is not active");
        }
        return task;
    }

    private boolean isValidLease(InsuranceTermIndexTaskDO task, String leaseToken) {
        return "PROCESSING".equals(task.getTaskStatus()) && task.getLeaseUntil() != null
                && task.getLeaseUntil().isAfter(LocalDateTime.now())
                && StringUtils.hasText(leaseToken) && leaseToken.equals(task.getLeaseToken());
    }

    private boolean isCurrentTask(InsuranceTermIndexTaskDO task, InsuranceTermSourceDO source) {
        if (source == null || !task.getSourceSha256().equalsIgnoreCase(source.getSha256())) return false;
        return "INDEX".equals(task.getAction()) ? "PUBLISHING".equals(source.getStatus())
                : "REMOVE".equals(task.getAction()) && "WITHDRAWN".equals(source.getStatus());
    }

    private InsuranceTermIndexTaskVO toView(InsuranceTermIndexTaskDO task, InsuranceTermSourceDO source) {
        InsuranceTermIndexTaskVO view = new InsuranceTermIndexTaskVO();
        view.setTaskId(task.getId());
        view.setSourceId(source.getId());
        view.setProductId(source.getInsuranceId());
        view.setProfileVersionId(source.getProfileVersionId());
        view.setProductVersion(source.getProductVersion());
        view.setSourceType(source.getSourceType());
        view.setAction(task.getAction());
        view.setSourceSha256(task.getSourceSha256());
        view.setTitle(source.getTitle());
        view.setOriginalFileName(source.getOriginalFileName());
        view.setContentType(source.getContentType());
        view.setFileSize(source.getFileSize());
        view.setLeaseToken(task.getLeaseToken());
        view.setLeaseDurationSeconds(CLAIM_LEASE_SECONDS);
        view.setLeaseUntil(task.getLeaseUntil());
        if ("INDEX".equals(task.getAction())) {
            view.setContentPath("/internal/agent-index/v1/tasks/" + task.getId() + "/content");
        }
        return view;
    }

}
