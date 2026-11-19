package com.itheima.bolee.insurance.service.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.DefaultApplicationArguments;
import org.mockito.InOrder;

import java.nio.file.Path;

import static org.mockito.Mockito.*;

public class AiDeletionLedgerReplayRunnerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void remoteDeletionPrecedesLocalPurgeForEveryTombstone() throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve("deletions.log");
        AiDeletionLedger ledger = new AiDeletionLedger(path.toString());
        String first = "8c1f27f4-02ee-4dd3-8f8c-5314fab0da55";
        String second = "b54b5540-595d-4eb5-a122-cc967c70bb9f";
        ledger.record(first);
        ledger.record(second);
        AiAgentDataCleaner cleaner = mock(AiAgentDataCleaner.class);
        AiRestoredDataPurger purger = mock(AiRestoredDataPurger.class);

        new AiDeletionLedgerReplayRunner(ledger, cleaner, purger).run(new DefaultApplicationArguments());

        InOrder order = inOrder(cleaner, purger);
        order.verify(cleaner).deleteSessionAndTraces(first);
        order.verify(purger).purge(first);
        order.verify(cleaner).deleteSessionAndTraces(second);
        order.verify(purger).purge(second);
    }

    @Test
    public void remoteFailureStopsReplayBeforeLocalPurge() throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve("deletions.log");
        AiDeletionLedger ledger = new AiDeletionLedger(path.toString());
        String sessionId = "8c1f27f4-02ee-4dd3-8f8c-5314fab0da55";
        ledger.record(sessionId);
        AiAgentDataCleaner cleaner = mock(AiAgentDataCleaner.class);
        AiRestoredDataPurger purger = mock(AiRestoredDataPurger.class);
        RuntimeException failure = new RuntimeException("remote unavailable");
        doThrow(failure).when(cleaner).deleteSessionAndTraces(sessionId);

        try {
            new AiDeletionLedgerReplayRunner(ledger, cleaner, purger).run(new DefaultApplicationArguments());
            org.junit.Assert.fail("remote failure must abort restore replay");
        } catch (RuntimeException actual) {
            org.junit.Assert.assertSame(failure, actual);
        }
        verifyNoInteractions(purger);
    }
}
