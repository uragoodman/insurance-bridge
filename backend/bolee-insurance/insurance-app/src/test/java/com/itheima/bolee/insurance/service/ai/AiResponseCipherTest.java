package com.itheima.bolee.insurance.service.ai;

import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.*;

public class AiResponseCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    public void encryptsAndDecryptsResponse() {
        AiResponseCipher cipher = new AiResponseCipher(KEY);
        String encrypted = cipher.encrypt("sensitive response", "owner:turn");

        assertTrue(encrypted.startsWith("v1:"));
        assertNotEquals("sensitive response", encrypted);
        assertEquals("sensitive response", cipher.decrypt(encrypted, "owner:turn"));
    }

    @Test
    public void rejectsInvalidKey() {
        try {
            new AiResponseCipher("not-a-key");
            fail("expected invalid key to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("base64-encoded 32-byte key"));
        }
    }

    @Test
    public void rejectsTamperedCiphertext() {
        AiResponseCipher cipher = new AiResponseCipher(KEY);
        String encrypted = cipher.encrypt("sensitive response", "owner:turn");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";
        try {
            cipher.decrypt(tampered, "owner:turn");
            fail("expected tampered ciphertext to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("decrypt"));
        }
    }

    @Test
    public void bindsCiphertextToTurnContext() {
        AiResponseCipher cipher = new AiResponseCipher(KEY);
        String encrypted = cipher.encrypt("sensitive response", "owner:turn-1");
        try {
            cipher.decrypt(encrypted, "owner:turn-2");
            fail("expected associated data mismatch to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("decrypt"));
        }
    }
}
