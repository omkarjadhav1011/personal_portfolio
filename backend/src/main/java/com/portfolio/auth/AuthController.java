package com.portfolio.auth;

import com.portfolio.chatbot.RateLimiter;
import com.portfolio.security.JwtService;
import com.portfolio.security.JwtSessionGuard;
import com.portfolio.security.OneTimeCodeStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String RATE_LIMIT_KEY_PREFIX = "auth-login:";
    private static final String RATE_LIMIT_USER_PREFIX = "auth-login-user:";
    private static final String RATE_LIMIT_EXCHANGE_PREFIX = "auth-exchange:";

    private final JwtService jwtService;
    private final JwtSessionGuard sessionGuard;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final OneTimeCodeStore oneTimeCodeStore;
    private final String adminUsername;
    private final String adminPasswordHash;

    public AuthController(JwtService jwtService,
                          JwtSessionGuard sessionGuard,
                          PasswordEncoder passwordEncoder,
                          RateLimiter rateLimiter,
                          OneTimeCodeStore oneTimeCodeStore,
                          @Value("${ADMIN_USERNAME:}") String adminUsername,
                          @Value("${ADMIN_PASSWORD_HASH:}") String adminPasswordHash) {
        this.jwtService = jwtService;
        this.sessionGuard = sessionGuard;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.oneTimeCodeStore = oneTimeCodeStore;
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
        // Per-IP throttle (IP derived from the trusted X-Real-IP, not spoofable XFF).
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_KEY_PREFIX + RateLimiter.clientIp(request));
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
        boolean ok = req.username().equals(adminUsername)
                && passwordEncoder.matches(req.password(), adminPasswordHash);
        if (!ok) {
            throw unauthorized();
        }
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
        // Same per-IP throttle as login: an attacker shouldn't be able to brute-force codes.
        RateLimiter.Result limit = rateLimiter.check(RATE_LIMIT_EXCHANGE_PREFIX + RateLimiter.clientIp(request));
        if (!limit.ok()) {
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please slow down.");
        }

        String code = req == null ? null : req.code();
        return oneTimeCodeStore.redeem(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code"));
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
