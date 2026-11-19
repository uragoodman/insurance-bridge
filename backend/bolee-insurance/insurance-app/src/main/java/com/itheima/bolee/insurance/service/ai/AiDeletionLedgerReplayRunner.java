package com.itheima.bolee.insurance.service.ai;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Explicit offline restore step: replay tombstones against AgentOS, then the restored SQL database. */
@Component
@ConditionalOnExpression("'${ai.persistence.enabled:false}' == 'true' && " +
        "'${ai.deletion-ledger.replay-on-startup:false}' == 'true'")
public class AiDeletionLedgerReplayRunner implements ApplicationRunner {
    private final AiDeletionLedger ledger;
    private final AiAgentDataCleaner cleaner;
    private final AiRestoredDataPurger purger;

    public AiDeletionLedgerReplayRunner(AiDeletionLedger ledger, AiAgentDataCleaner cleaner,
                                        AiRestoredDataPurger purger) {
        this.ledger = ledger;
        this.cleaner = cleaner;
        this.purger = purger;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (String sessionId : ledger.readAll()) {
            cleaner.deleteSessionAndTraces(sessionId);
            purger.purge(sessionId);
        }
    }
}
