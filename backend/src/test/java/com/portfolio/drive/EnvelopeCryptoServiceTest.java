package com.portfolio.drive;

import com.portfolio.drive.EnvelopeCryptoService.EncryptedPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvelopeCryptoServiceTest {

    private static String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    private final EnvelopeCryptoService crypto = new EnvelopeCryptoService(randomKey());

    @Test
    void roundTripReturnsOriginal() {
        byte[] plain = "the quick brown fox 🦊 jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

        EncryptedPayload enc = crypto.encrypt(plain);

        assertArrayEquals(plain, crypto.decrypt(enc.ciphertext(), enc.iv(), enc.wrappedKey()));
    }

    @Test
    void emptyInputRoundTrips() {
        byte[] plain = new byte[0];

        EncryptedPayload enc = crypto.encrypt(plain);

        assertArrayEquals(plain, crypto.decrypt(enc.ciphertext(), enc.iv(), enc.wrappedKey()));
    }

    @Test
    void freshKeyAndIvPerCallSoCiphertextNeverRepeats() {
        byte[] plain = "secret".getBytes(StandardCharsets.UTF_8);

        EncryptedPayload a = crypto.encrypt(plain);
        EncryptedPayload b = crypto.encrypt(plain);

        // Ciphertext is not the plaintext...
        assertFalse(Arrays.equals(plain, a.ciphertext()));
        // ...and the same plaintext yields different ciphertext, IV and wrapped DEK each time.
        assertFalse(Arrays.equals(a.ciphertext(), b.ciphertext()));
        assertFalse(Arrays.equals(a.iv(), b.iv()));
        assertFalse(Arrays.equals(a.wrappedKey(), b.wrappedKey()));
    }

    @Test
    void wrongMasterKeyCannotDecrypt() {
        EncryptedPayload enc = crypto.encrypt("data".getBytes(StandardCharsets.UTF_8));
        EnvelopeCryptoService other = new EnvelopeCryptoService(randomKey());

        // A different KEK cannot unwrap the DEK → GCM auth failure.
        assertThrows(IllegalStateException.class,
                () -> other.decrypt(enc.ciphertext(), enc.iv(), enc.wrappedKey()));
    }

    @Test
    void tamperedCiphertextFailsAuthTag() {
        EncryptedPayload enc = crypto.encrypt("data".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = enc.ciphertext().clone();
        tampered[0] ^= 0x01;

        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(tampered, enc.iv(), enc.wrappedKey()));
    }

    @Test
    void tamperedWrappedKeyFailsAuthTag() {
        EncryptedPayload enc = crypto.encrypt("data".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = enc.wrappedKey().clone();
        tampered[tampered.length - 1] ^= 0x01;

        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(enc.ciphertext(), enc.iv(), tampered));
    }

    @Test
    void missingKeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> new EnvelopeCryptoService(""));
        assertThrows(IllegalStateException.class, () -> new EnvelopeCryptoService(null));
    }

    @Test
    void wrongSizeKeyFailsFast() {
        String key128 = Base64.getEncoder().encodeToString(new byte[16]); // 128-bit, not 256
        assertThrows(IllegalStateException.class, () -> new EnvelopeCryptoService(key128));
    }

    @Test
    void nonBase64KeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> new EnvelopeCryptoService("not valid base64 !!!"));
    }
}
