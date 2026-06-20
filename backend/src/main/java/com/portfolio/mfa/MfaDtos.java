package com.portfolio.mfa;

import java.util.List;

/** Request/response payloads for the MFA endpoints. */
public final class MfaDtos {

    private MfaDtos() {
    }

    /** {@code /setup} response: the secret (shown once), the provisioning URI, and a QR data-URI. */
    public record MfaSetupResponse(String secret, String otpauthUri, String qrDataUri) {
    }

    /** {@code /enable} response: the 10 single-use recovery codes, returned exactly once. */
    public record MfaEnableResponse(List<String> recoveryCodes) {
    }

    /** Body for {@code /enable}, {@code /disable}, {@code /verify}: a TOTP or a recovery code. */
    public record CodeRequest(String code) {
    }
}
