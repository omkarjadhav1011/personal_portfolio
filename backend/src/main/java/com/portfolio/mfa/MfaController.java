package com.portfolio.mfa;

import com.portfolio.auth.LoginResponse;
import com.portfolio.chatbot.RateLimiter;
import com.portfolio.mfa.MfaDtos.CodeRequest;
import com.portfolio.mfa.MfaDtos.MfaEnableResponse;
import com.portfolio.mfa.MfaDtos.MfaSetupResponse;
import com.portfolio.security.JwtService;
import com.portfolio.security.LoginAttemptTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * MFA endpoints. Enrollment ({@code setup}/{@code enable}/{@code disable}) requires a full ADMIN
 * token. {@code verify} is the second-factor gate: it is reachable only by a PRE_AUTH token (see
 * SecurityConfig) and, on a valid code, mints the final ADMIN JWT. The verify step is rate-limited
 * and shares the per-identity lockout counter with password login (Phase 5).
 */
@Tag(name = "MFA", description = "TOTP multi-factor authentication")
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private static final Logger log = LoggerFactory.getLogger(MfaController.class);
    private static final String RATE_LIMIT_VERIFY_PREFIX = "auth-mfa-verify:";

    private final MfaService mfaService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final LoginAttemptTracker attemptTracker;

    public MfaController(MfaService mfaService, JwtService jwtService,
                         RateLimiter rateLimiter, LoginAttemptTracker attemptTracker) {
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.attemptTracker = attemptTracker;
    }

    @Operation(summary = "Begin MFA enrollment", description = "Generates a secret + QR (ADMIN)")
    @PostMapping("/setup")
    public MfaSetupResponse setup(Authentication authentication) {
        log.info("MFA setup initiated by {}", authentication.getName());
        return mfaService.setup(authentication.getName());
    }

    @Operation(summary = "Enable MFA", description = "Confirms a live code and returns recovery codes (ADMIN)")
    @PostMapping("/enable")
    public MfaEnableResponse enable(@RequestBody(required = false) CodeRequest req) {
        List<String> recoveryCodes = mfaService.enable(req == null ? null : req.code());
        log.info("MFA enabled");
        return new MfaEnableResponse(recoveryCodes);
    }

    @Operation(summary = "Disable MFA", description = "Verifies a code/recovery code then disables (ADMIN)")
    @PostMapping("/disable")
    public java.util.Map<String, Boolean> disable(@RequestBody(required = false) CodeRequest req) {
        mfaService.disable(req == null ? null : req.code());
        log.warn("MFA disabled");
        return java.util.Map.of("ok", true);
    }

    @Operation(summary = "Verify second factor",
            description = "PRE_AUTH only: validates TOTP/recovery code and mints the final ADMIN JWT")
    @PostMapping("/verify")
    public LoginResponse verify(@RequestBody(required = false) CodeRequest req,
                                Authentication authentication,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        String ip = RateLimiter.clientIp(request);
        String identity = authentication.getName();

        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_VERIFY_PREFIX + ip);
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please slow down.");
        }

        // Shared lockout with password login: failed OTPs count against the same identity.
        LoginAttemptTracker.Result lockout = attemptTracker.check(identity);
        if (lockout.locked()) {
            response.setHeader("Retry-After", String.valueOf(lockout.retryAfterSeconds()));
            log.warn("MFA verify blocked by lockout ip={}", ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again later.");
        }

        boolean ok = mfaService.verifyForLogin(req == null ? null : req.code());
        if (!ok) {
            attemptTracker.recordFailure(identity);
            log.warn("Failed MFA verification ip={}", ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid code");
        }
        attemptTracker.recordSuccess(identity);
        log.info("MFA verification succeeded; ADMIN token issued ip={}", ip);
        return new LoginResponse(jwtService.generate(identity), jwtService.getExpirySeconds());
    }
}
