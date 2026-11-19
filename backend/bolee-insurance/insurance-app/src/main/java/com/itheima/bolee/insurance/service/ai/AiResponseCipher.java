package com.itheima.bolee.insurance.service.ai;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encrypts customer-facing AI responses before they are persisted for idempotent replay. */
final class AiResponseCipher {
    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    AiResponseCipher(String base64Key) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
            if (decoded.length != 32) throw new IllegalArgumentException("AES-256 key must contain 32 bytes");
            this.key = new SecretKeySpec(decoded, "AES");
            Arrays.fill(decoded, (byte) 0);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ai.idempotency.encryption-key must be a base64-encoded 32-byte key", exception);
        }
    }

    String encrypt(String plaintext, String associatedData) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt AI response", exception);
        }
    }

    String decrypt(String value, String associatedData) {
        try {
            if (value == null || !value.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported ciphertext version");
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= NONCE_BYTES) throw new IllegalArgumentException("Ciphertext is truncated");
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt persisted AI response", exception);
        }
    }
}
