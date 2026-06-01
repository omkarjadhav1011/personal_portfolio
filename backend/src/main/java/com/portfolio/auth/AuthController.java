package com.portfolio.auth;

import com.portfolio.chatbot.RateLimiter;
import com.portfolio.security.JwtService;
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

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final String adminUsername;
    private final String adminPasswordHash;

    public AuthController(JwtService jwtService,
                          PasswordEncoder passwordEncoder,
                          RateLimiter rateLimiter,
                          @Value("${ADMIN_USERNAME:}") String adminUsername,
                          @Value("${ADMIN_PASSWORD_HASH:}") String adminPasswordHash) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
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
        boolean ok = req.username().equals(adminUsername)
                && passwordEncoder.matches(req.password(), adminPasswordHash);
        if (!ok) {
            throw unauthorized();
        }
        return new LoginResponse(jwtService.generate(req.username()), jwtService.getExpirySeconds());
    }

    @Operation(summary = "Logout", description = "No-op; the client discards its token")
    @ApiResponse(responseCode = "200", description = "OK")
    @PostMapping("/logout")
    public Map<String, Boolean> logout() {
        return Map.of("ok", true);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
