package com.itheima.bolee.insurance.service.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class AiDeletionLedgerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recordsSessionIdsAndDeduplicatesThemWhenReopened() throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve("ops/deletions.log");
        AiDeletionLedger ledger = new AiDeletionLedger(path.toString());
        String sessionId = "8c1f27f4-02ee-4dd3-8f8c-5314fab0da55";

        ledger.record(sessionId);
        ledger.record(sessionId);

        assertEquals(Collections.singletonList(sessionId), new AiDeletionLedger(path.toString()).readAll());
        assertEquals(sessionId + "\n" + sessionId + "\n", new String(Files.readAllBytes(path), "US-ASCII"));
    }

    @Test
    public void rejectsMissingPathAndMalformedJournal() throws Exception {
        try {
            new AiDeletionLedger(" ");
            fail("missing path must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("AI_DELETION_LEDGER_PATH"));
        }

        Path path = temporaryFolder.getRoot().toPath().resolve("broken.log");
        Files.write(path, Arrays.asList("not-a-uuid"));
        try {
            new AiDeletionLedger(path.toString()).readAll();
            fail("malformed journal must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("valid AI deletion ledger"));
        }
    }
}
