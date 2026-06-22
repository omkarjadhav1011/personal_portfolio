package com.portfolio.drive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Envelope encryption for vault documents (AES-256-GCM), using the JDK {@code javax.crypto} — no
 * new dependency. Each file gets a fresh random 256-bit data key (DEK) that encrypts its bytes;
 * the DEK is then wrapped under the long-lived master key (KEK) from {@code DRIVE_MASTER_KEY}.
 * Only the wrapped DEK + IV are stored (DB), and only the ciphertext is stored (object storage),
 * so a full MinIO + Postgres dump yields ciphertext plus a DEK that is itself ciphertext — useless
 * without the KEK, which lives only in app env.
 *
 * <p>Mirrors the fail-fast key guard of {@code JwtService} and the AES-GCM idioms of
 * {@code MfaSecretCipher}. Gated on {@code STORAGE_ENDPOINT} (the single drive feature flag, like
 * {@code DriveStorageConfig}): when the vault is enabled, {@code DRIVE_MASTER_KEY} is mandatory and
 * validated at startup; when it is disabled, this bean isn't created and no key is required (so the
 * non-drive app and the test suite boot without one).
 */
@Service
@ConditionalOnProperty(name = "STORAGE_ENDPOINT")
public class EnvelopeCryptoService {

    private static final int IV_LENGTH = 12;          // 96-bit GCM nonce (recommended size)
    private static final int TAG_LENGTH_BITS = 128;   // full 128-bit GCM auth tag
    private static final int DEK_LENGTH_BITS = 256;   // AES-256 per-file data key
    private static final int KEK_LENGTH_BYTES = 32;   // AES-256 master key
    private static final String AES = "AES";
    private static final String GCM = "AES/GCM/NoPadding";

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public EnvelopeCryptoService(@Value("${DRIVE_MASTER_KEY:}") String masterKeyBase64) {
        this.masterKey = loadMasterKey(masterKeyBase64);
    }

    private static SecretKey loadMasterKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(
                    "DRIVE_MASTER_KEY environment variable is required when the document vault is "
                    + "enabled (STORAGE_ENDPOINT set). Generate one with: openssl rand -base64 32");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "DRIVE_MASTER_KEY must be valid base64. Generate one with: openssl rand -base64 32", e);
        }
        if (key.length != KEK_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "DRIVE_MASTER_KEY must decode to exactly 32 bytes (AES-256); got " + key.length
                    + ". Generate one with: openssl rand -base64 32");
        }
        return new SecretKeySpec(key, AES);
    }

    /**
     * Result of encrypting one file. {@code ciphertext} (GCM tag appended) goes to object storage;
     * {@code iv} and {@code wrappedKey} go to the DB ({@code enc_iv}, {@code enc_wrapped_key}).
     */
    public record EncryptedPayload(byte[] ciphertext, byte[] iv, byte[] wrappedKey) {
    }

    /**
     * Envelope-encrypts {@code plain}: a fresh 256-bit DEK encrypts the bytes with AES-256-GCM, then
     * the DEK is wrapped (also AES-256-GCM) under the KEK. The JDK appends the GCM auth tag to each
     * ciphertext, so no separate tag is stored.
     */
    public EncryptedPayload encrypt(byte[] plain) {
        try {
            SecretKey dek = generateDek();
            byte[] iv = randomIv();
            byte[] ciphertext = gcm(Cipher.ENCRYPT_MODE, dek, iv, plain);
            byte[] wrappedKey = wrapDek(dek);
            return new EncryptedPayload(ciphertext, iv, wrappedKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt document", e);
        }
    }

    /** Reverses {@link #encrypt}: unwraps the DEK with the KEK, then GCM-decrypts the ciphertext. */
    public byte[] decrypt(byte[] ciphertext, byte[] iv, byte[] wrappedKey) {
        try {
            SecretKey dek = unwrapDek(wrappedKey);
            return gcm(Cipher.DECRYPT_MODE, dek, iv, ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt document (wrong key or tampered data)", e);
        }
    }

    private SecretKey generateDek() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(AES);
        kg.init(DEK_LENGTH_BITS, random);
        return kg.generateKey();
    }

    private byte[] randomIv() {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        return iv;
    }

    /** Wraps the DEK as {@code wrapIv(12) || GCM(KEK, wrapIv, DEK-bytes)} (tag appended). */
    private byte[] wrapDek(SecretKey dek) throws Exception {
        byte[] wrapIv = randomIv();
        byte[] wrapped = gcm(Cipher.ENCRYPT_MODE, masterKey, wrapIv, dek.getEncoded());
        byte[] out = new byte[wrapIv.length + wrapped.length];
        System.arraycopy(wrapIv, 0, out, 0, wrapIv.length);
        System.arraycopy(wrapped, 0, out, wrapIv.length, wrapped.length);
        return out;
    }

    private SecretKey unwrapDek(byte[] wrappedKey) throws Exception {
        byte[] wrapIv = new byte[IV_LENGTH];
        System.arraycopy(wrappedKey, 0, wrapIv, 0, IV_LENGTH);
        byte[] wrapped = new byte[wrappedKey.length - IV_LENGTH];
        System.arraycopy(wrappedKey, IV_LENGTH, wrapped, 0, wrapped.length);
        byte[] dekBytes = gcm(Cipher.DECRYPT_MODE, masterKey, wrapIv, wrapped);
        return new SecretKeySpec(dekBytes, AES);
    }

    private byte[] gcm(int mode, SecretKey key, byte[] iv, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance(GCM);
        cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(input);
    }
}
