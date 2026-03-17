package com.marketing.analytics.platform;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM token encryption for storing platform access tokens at rest.
 * The encryption key is provided via PLATFORM_TOKEN_ENCRYPTION_KEY env var (base64-encoded 32 bytes).
 * When no key is configured, tokens are stored with a "PLAINTEXT:" prefix as a clear signal
 * that encryption is not active — this allows local development without a key.
 */
@Component
public class TokenEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENC_PREFIX = "ENC:";
    private static final String PLAIN_PREFIX = "PLAINTEXT:";

    private final SecretKey secretKey;
    private final boolean encryptionEnabled;

    public TokenEncryptor(@Value("${platform.token.encryption-key:}") String keyBase64) {
        if (keyBase64 != null && !keyBase64.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("PLATFORM_TOKEN_ENCRYPTION_KEY must be 32 bytes (base64-encoded)");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            this.encryptionEnabled = true;
        } else {
            this.secretKey = null;
            this.encryptionEnabled = false;
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (!encryptionEnabled) return PLAIN_PREFIX + plaintext;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) return null;
        if (stored.startsWith(PLAIN_PREFIX)) return stored.substring(PLAIN_PREFIX.length());
        if (!stored.startsWith(ENC_PREFIX)) throw new IllegalArgumentException("Unknown token format");
        if (!encryptionEnabled) throw new IllegalStateException("Encrypted token found but no encryption key configured");
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(combined, iv.length, combined.length - iv.length);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Token decryption failed", e);
        }
    }

    public boolean isEncryptionEnabled() { return encryptionEnabled; }
}
