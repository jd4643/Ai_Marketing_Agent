package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;

import com.marketing.analytics.platform.TokenEncryptor;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenEncryptorTest {

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test void encryptDecryptRoundTrip() {
        TokenEncryptor enc = new TokenEncryptor(randomKey());
        String token = "EAABsbGc9kZCABANx2ZAtoken12345";
        String encrypted = enc.encrypt(token);
        assertTrue(encrypted.startsWith("ENC:"));
        assertEquals(token, enc.decrypt(encrypted));
    }

    @Test void noKeyPlaintextFallback() {
        TokenEncryptor enc = new TokenEncryptor("");
        assertFalse(enc.isEncryptionEnabled());
        String encrypted = enc.encrypt("my-token");
        assertTrue(encrypted.startsWith("PLAINTEXT:"));
        assertEquals("my-token", enc.decrypt(encrypted));
    }

    @Test void nullHandling() {
        TokenEncryptor enc = new TokenEncryptor(randomKey());
        assertNull(enc.encrypt(null));
        assertNull(enc.decrypt(null));
    }

    @Test void invalidKeyLengthFails() {
        String badKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> new TokenEncryptor(badKey));
    }

    @Test void differentEncryptionsProduceDifferentCiphertext() {
        TokenEncryptor enc = new TokenEncryptor(randomKey());
        String a = enc.encrypt("same-token");
        String b = enc.encrypt("same-token");
        assertNotEquals(a, b); // different IV each time
        assertEquals(enc.decrypt(a), enc.decrypt(b));
    }
}
