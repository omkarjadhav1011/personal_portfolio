package com.portfolio.mfa;

import com.portfolio.mfa.MfaDtos.MfaSetupResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MfaServiceTest {

    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final AdminMfaRepository repo = mock(AdminMfaRepository.class);
    private final MfaSecretCipher cipher =
            new MfaSecretCipher("unit-test-secret-key-that-is-at-least-32-bytes-long");
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    private MfaService serviceWith(TotpVerifier verifier) {
        when(repo.save(any(AdminMfa.class))).thenAnswer(inv -> inv.getArgument(0));
        return new MfaService(repo, cipher, verifier, encoder);
    }

    @Test
    void setupPersistsEncryptedSecretDisabledAndReturnsQr() {
        when(repo.findSingleton()).thenReturn(Optional.empty());
        MfaService service = serviceWith(new TotpVerifier());

        MfaSetupResponse resp = service.setup("admin");

        assertNotNull(resp.secret());
        assertTrue(resp.otpauthUri().startsWith("otpauth://totp/"));
        assertTrue(resp.qrDataUri().startsWith("data:image/png;base64,"));

        ArgumentCaptor<AdminMfa> saved = ArgumentCaptor.forClass(AdminMfa.class);
        org.mockito.Mockito.verify(repo).save(saved.capture());
        assertFalse(saved.getValue().isEnabled(), "setup must not enable yet");
        assertNotNull(saved.getValue().getSecretEnc());
        // Stored secret is encrypted, not the raw Base32.
        assertEquals(resp.secret(), cipher.decrypt(saved.getValue().getSecretEnc()));
    }

    @Test
    void enableWithValidCodeReturnsTenRecoveryCodesAndEnables() {
        AdminMfa row = new AdminMfa();
        row.setSecretEnc(cipher.encrypt(SECRET));
        when(repo.findSingleton()).thenReturn(Optional.of(row));

        TotpVerifier verifier = new TotpVerifier(() -> 60_000L);
        String code = String.format("%06d", verifier.currentCode(SECRET));
        MfaService service = serviceWith(verifier);

        List<String> recoveryCodes = service.enable(code);

        assertEquals(10, recoveryCodes.size());
        assertTrue(row.isEnabled());
        assertEquals(10, row.getRecoveryCodesHash().size());
        // Recovery codes are stored only as hashes, never plaintext.
        assertFalse(row.getRecoveryCodesHash().contains(recoveryCodes.get(0)));
    }

    @Test
    void enableWithWrongCodeIsRejected() {
        AdminMfa row = new AdminMfa();
        row.setSecretEnc(cipher.encrypt(SECRET));
        when(repo.findSingleton()).thenReturn(Optional.of(row));
        MfaService service = serviceWith(new TotpVerifier(() -> 60_000L));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.enable("000000"));
        assertFalse(row.isEnabled());
    }

    @Test
    void recoveryCodeIsSingleUse() {
        AdminMfa row = new AdminMfa();
        row.setEnabled(true);
        row.setSecretEnc(cipher.encrypt(SECRET));
        String recovery = "abcd-efgh";
        row.setRecoveryCodesHash(new java.util.ArrayList<>(List.of(encoder.encode(recovery))));
        when(repo.findSingleton()).thenReturn(Optional.of(row));
        MfaService service = serviceWith(new TotpVerifier());

        assertTrue(service.verifyForLogin(recovery), "first use of a recovery code succeeds");
        assertFalse(service.verifyForLogin(recovery), "the same recovery code cannot be reused");
        assertTrue(row.getRecoveryCodesHash().isEmpty(), "consumed code is removed");
    }

    @Test
    void verifyReturnsFalseWhenMfaNotEnabled() {
        when(repo.findSingleton()).thenReturn(Optional.empty());
        MfaService service = serviceWith(new TotpVerifier());
        assertFalse(service.verifyForLogin("123456"));
    }
}
