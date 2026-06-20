package com.portfolio.mfa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.portfolio.mfa.MfaDtos.MfaSetupResponse;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * TOTP MFA for the single admin: enrollment (secret + QR), enable/disable, and the login-time
 * second-factor check. The secret is encrypted at rest ({@link MfaSecretCipher}); recovery codes
 * are stored only as BCrypt hashes and are single-use. {@link #isEnabled()} drives the two-step
 * login — when false (no enrollment), login is unchanged, so MFA is optional at startup.
 */
@Service
public class MfaService {

    private static final String ISSUER = "Portfolio Admin";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String RECOVERY_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"; // no easily-confused chars

    private final AdminMfaRepository repository;
    private final MfaSecretCipher cipher;
    private final TotpVerifier totpVerifier;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthenticator credentialsFactory = new GoogleAuthenticator();
    private final SecureRandom random = new SecureRandom();

    public MfaService(AdminMfaRepository repository, MfaSecretCipher cipher,
                      TotpVerifier totpVerifier, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.cipher = cipher;
        this.totpVerifier = totpVerifier;
        this.passwordEncoder = passwordEncoder;
    }

    /** True once the owner has confirmed enrollment. Drives whether login demands a second factor. */
    public boolean isEnabled() {
        return repository.findSingleton().map(AdminMfa::isEnabled).orElse(false);
    }

    /** Generates a fresh secret (enabled=false) and returns the provisioning URI + QR for scanning. */
    @Transactional
    public MfaSetupResponse setup(String accountLabel) {
        String secret = credentialsFactory.createCredentials().getKey();
        AdminMfa row = repository.findSingleton().orElseGet(AdminMfa::new);
        row.setSecretEnc(cipher.encrypt(secret));
        row.setEnabled(false);
        row.setRecoveryCodesHash(new ArrayList<>());
        repository.save(row);

        String otpauthUri = buildOtpAuthUri(accountLabel, secret);
        return new MfaSetupResponse(secret, otpauthUri, qrDataUri(otpauthUri));
    }

    /** Verifies a live code against the pending secret, then enables MFA and mints recovery codes. */
    @Transactional
    public List<String> enable(String otp) {
        AdminMfa row = repository.findSingleton()
                .filter(r -> r.getSecretEnc() != null)
                .orElseThrow(() -> badRequest("No pending MFA setup. Call /setup first."));
        if (!totpVerifier.verify(cipher.decrypt(row.getSecretEnc()), otp)) {
            throw unauthorized();
        }
        List<String> plain = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            plain.add(code);
            hashes.add(passwordEncoder.encode(code));
        }
        row.setEnabled(true);
        row.setRecoveryCodesHash(hashes);
        repository.save(row);
        return plain;
    }

    /** Verifies a code (TOTP or recovery) then disables MFA and wipes the secret + recovery codes. */
    @Transactional
    public void disable(String code) {
        AdminMfa row = repository.findSingleton()
                .filter(AdminMfa::isEnabled)
                .orElseThrow(() -> badRequest("MFA is not enabled."));
        if (!verifyCodeOrRecovery(row, code)) {
            throw unauthorized();
        }
        row.setEnabled(false);
        row.setSecretEnc(null);
        row.setRecoveryCodesHash(new ArrayList<>());
        repository.save(row);
    }

    /**
     * Login-time second-factor check: a valid TOTP (±1 step, replay-protected) or a single-use
     * recovery code. Returns false when MFA isn't enabled or the code is wrong. A redeemed
     * recovery code is consumed (removed) here.
     */
    @Transactional
    public boolean verifyForLogin(String code) {
        return repository.findSingleton()
                .filter(AdminMfa::isEnabled)
                .map(row -> verifyCodeOrRecovery(row, code))
                .orElse(false);
    }

    private boolean verifyCodeOrRecovery(AdminMfa row, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        // A 6-digit code is a TOTP; anything else is treated as a recovery code.
        if (code.matches("\\d{6}") && row.getSecretEnc() != null
                && totpVerifier.verify(cipher.decrypt(row.getSecretEnc()), code)) {
            return true;
        }
        List<String> hashes = new ArrayList<>(row.getRecoveryCodesHash());
        for (int i = 0; i < hashes.size(); i++) {
            if (passwordEncoder.matches(code, hashes.get(i))) {
                hashes.remove(i); // single-use: consume on redemption
                row.setRecoveryCodesHash(hashes);
                repository.save(row);
                return true;
            }
        }
        return false;
    }

    private String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String buildOtpAuthUri(String account, String secret) {
        String label = enc(ISSUER + ":" + account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + enc(ISSUER)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String qrDataUri(String otpauthUri) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render MFA QR code", e);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid code");
    }
}
