package com.itheima.bolee.insurance.service.ai;

import org.junit.Test;

import static org.junit.Assert.*;

public class AiRequestFingerprintTest {

    @Test
    public void fingerprintUsesStableVersionedUtf8Hash() {
        assertEquals("c4674d99b9f019a5e3d34fb3db16d85c272c79e558856c8de39e54b96b0a3c0e",
                AiRequestFingerprint.sha256ChatMessage("hello"));
    }

    @Test
    public void fingerprintPreservesWhitespaceAndUnicodeExactly() {
        assertNotEquals(AiRequestFingerprint.sha256ChatMessage("hello"),
                AiRequestFingerprint.sha256ChatMessage(" hello"));
        assertNotEquals(AiRequestFingerprint.sha256ChatMessage("保障"),
                AiRequestFingerprint.sha256ChatMessage("保障 "));
    }

    @Test(expected = NullPointerException.class)
    public void fingerprintRejectsNullMessage() {
        AiRequestFingerprint.sha256ChatMessage(null);
    }
}
