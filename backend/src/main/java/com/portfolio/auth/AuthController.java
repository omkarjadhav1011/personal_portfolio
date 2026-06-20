package com.portfolio.auth;

import com.portfolio.chatbot.RateLimiter;
import com.portfolio.mfa.MfaService;
import com.portfolio.security.JwtService;
import com.portfolio.security.JwtSessionGuard;
import com.portfolio.security.LoginAttemptTracker;
import com.portfolio.security.OneTimeCodeStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Auth endpoints. Ports the Next.js {@code api/auth/login}: validates credentials against
 * {@code ADMIN_USERNAME} + {@code ADMIN_PASSWORD_HASH} (BCrypt). Per the locked decision it
 * returns {@code {token, expiresIn}} (Bearer, no cookie); logout is a client-side discard
 * (no-op endpoint).
 */
@Tag(name = "Auth", description = "Admin login / logout")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "auth-login:";
    private static final String RATE_LIMIT_USER_PREFIX = "auth-login-user:";
    private static final String RATE_LIMIT_EXCHANGE_PREFIX = "auth-exchange:";

    private final JwtService jwtService;
    private final JwtSessionGuard sessionGuard;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final LoginAttemptTracker attemptTracker;
    private final OneTimeCodeStore oneTimeCodeStore;
    private final MfaService mfaService;
    private final String adminUsername;
    private final String adminPasswordHash;

    public AuthController(JwtService jwtService,
                          JwtSessionGuard sessionGuard,
                          PasswordEncoder passwordEncoder,
                          RateLimiter rateLimiter,
                          LoginAttemptTracker attemptTracker,
                          OneTimeCodeStore oneTimeCodeStore,
                          MfaService mfaService,
                          @Value("${ADMIN_USERNAME:}") String adminUsername,
                          @Value("${ADMIN_PASSWORD_HASH:}") String adminPasswordHash) {
        this.jwtService = jwtService;
        this.sessionGuard = sessionGuard;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.attemptTracker = attemptTracker;
        this.oneTimeCodeStore = oneTimeCodeStore;
        this.mfaService = mfaService;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
    }

    @Operation(summary = "Admin login", description = "Returns a Bearer JWT on valid credentials")
    @ApiResponse(responseCode = "200", description = "Authenticated; token returned")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest req,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        String ip = RateLimiter.clientIp(request);

        // Per-IP throttle (IP derived from the trusted X-Real-IP, not spoofable XFF).
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_KEY_PREFIX + ip);
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please slow down.");
        }

        // Env guard: never authenticate when the admin credentials aren't configured.
        if (isBlank(adminUsername) || isBlank(adminPasswordHash)) {
            throw unauthorized();
        }
        if (req == null || isBlank(req.username()) || isBlank(req.password())) {
            throw unauthorized();
        }

        // Per-account throttle: caps attempts against a single username regardless of source
        // IP, so a distributed attempt can't brute-force one account (CWE-307).
        RateLimiter.Result userLimit = rateLimiter.check(RATE_LIMIT_USER_PREFIX + req.username());
        if (!userLimit.ok()) {
            response.setHeader("Retry-After", String.valueOf(userLimit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please slow down.");
        }

        // Progressive lockout: after repeated FAILURES for this identity, refuse further
        // attempts for a window (shared with the Phase 6 MFA step). Tripwire-logged.
        LoginAttemptTracker.Result lockout = attemptTracker.check(req.username());
        if (lockout.locked()) {
            response.setHeader("Retry-After", String.valueOf(lockout.retryAfterSeconds()));
            log.warn("Login blocked by lockout (too many failed attempts) ip={}", ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again later.");
        }

        boolean ok = req.username().equals(adminUsername)
                && passwordEncoder.matches(req.password(), adminPasswordHash);
        if (!ok) {
            attemptTracker.recordFailure(req.username());
            log.warn("Failed admin login attempt ip={}", ip);
            throw unauthorized();
        }
        attemptTracker.recordSuccess(req.username());

        // First factor passed. If MFA is enrolled, hand back ONLY a short-lived PRE_AUTH token —
        // no ADMIN access until the second factor is verified. Otherwise issue the full token.
        if (mfaService.isEnabled()) {
            log.info("Password factor OK; MFA required ip={}", ip);
            return new LoginResponse(jwtService.generatePreAuth(req.username()),
                    jwtService.getPreAuthExpirySeconds(), true);
        }
        log.info("Admin login succeeded (password) ip={}", ip);
        return new LoginResponse(jwtService.generate(req.username()), jwtService.getExpirySeconds());
    }

    @Operation(summary = "Exchange a one-time code for a JWT",
            description = "Redeems the single-use 60s code minted by a redirect-based login for a Bearer JWT. "
                    + "Keeps the token out of the redirect URL.")
    @ApiResponse(responseCode = "200", description = "Code valid; token returned")
    @ApiResponse(responseCode = "400", description = "Unknown or expired code")
    @PostMapping("/oauth/exchange")
    public LoginResponse exchange(@RequestBody(required = false) OAuthExchangeRequest req,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        String ip = RateLimiter.clientIp(request);

        // Same per-IP throttle as login: an attacker shouldn't be able to brute-force codes.
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_EXCHANGE_PREFIX + ip);
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please slow down.");
        }

        String code = req == null ? null : req.code();
        LoginResponse loginResponse = oneTimeCodeStore.redeem(code).orElse(null);
        if (loginResponse == null) {
            log.warn("OAuth code exchange failed (unknown/expired code) ip={}", ip);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        }
        log.info("OAuth code exchanged for token ip={}", ip);
        return loginResponse;
    }

    @Operation(summary = "Logout",
            description = "Revokes all tokens issued before now (server-side kill switch)")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping("/logout")
    public Map<String, Boolean> logout() {
        // Invalidate every previously-issued token so a discarded/leaked token can't be reused.
        sessionGuard.invalidateAll();
        return Map.of("ok", true);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
