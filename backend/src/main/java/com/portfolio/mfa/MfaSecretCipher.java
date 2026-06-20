package com.portfolio.mfa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the TOTP secret at rest with AES-256-GCM so a database leak alone never exposes a
 * usable second factor (CWE-312). The key is derived from {@code JWT_SECRET} via SHA-256 with a
 * domain-separation tag — reusing the existing required master secret (no extra env var) while
 * keeping the AES key distinct from the JWT HMAC key. A random 12-byte IV is prepended to each
 * ciphertext; the whole blob is Base64-encoded for storage.
 */
@Component
public class MfaSecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String DOMAIN = "::mfa-secret-encryption::v1";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MfaSecretCipher(@Value("${JWT_SECRET:}") String jwtSecret) {
        this.key = new SecretKeySpec(deriveKey(jwtSecret), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt MFA secret", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] blob = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(blob, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] pt = cipher.doFinal(blob, IV_LENGTH, blob.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt MFA secret", e);
        }
    }

    private static byte[] deriveKey(String jwtSecret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest((jwtSecret + DOMAIN).getBytes(StandardCharsets.UTF_8)); // 32 bytes
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive MFA encryption key", e);
        }
    }
}
