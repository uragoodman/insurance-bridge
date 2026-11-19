package com.itheima.bolee.insurance.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Stable, versioned request fingerprint for AI chat idempotency; never persists message text. */
public final class AiRequestFingerprint {

    private static final byte[] CHAT_V1_PREFIX = "ai-chat:v1\n".getBytes(StandardCharsets.UTF_8);

    private AiRequestFingerprint() {
    }

    /** Hashes the exact UTF-8 message bytes. Whitespace and Unicode are intentionally preserved. */
    public static String sha256ChatMessage(String message) {
        Objects.requireNonNull(message, "message must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(CHAT_V1_PREFIX);
            byte[] hash = digest.digest(message.getBytes(StandardCharsets.UTF_8));
            return toLowerHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Binds selected-policy metadata to retries without storing either the ID or message. */
    public static String sha256ChatRequest(String message, String selectedPolicyId) {
        if (selectedPolicyId == null || selectedPolicyId.trim().isEmpty()) return sha256ChatMessage(message);
        Objects.requireNonNull(message, "message must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("ai-chat:v2\n".getBytes(StandardCharsets.UTF_8));
            digest.update(message.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(selectedPolicyId.getBytes(StandardCharsets.UTF_8));
            return toLowerHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toLowerHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }
}
