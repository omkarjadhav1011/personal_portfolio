package com.portfolio.auth;

import com.portfolio.chatbot.RateLimiter;
import com.portfolio.mfa.MfaService;
import com.portfolio.security.JwtService;
import com.portfolio.security.JwtSessionGuard;
import com.portfolio.security.LoginAttemptTracker;
import com.portfolio.security.OneTimeCodeStore;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final JwtService jwtService =
            new JwtService("unit-test-secret-key-that-is-at-least-32-bytes-long", 8L);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final MfaService mfaDisabled = mock(MfaService.class); // isEnabled() -> false by default

    /** Controller with no admin creds configured — used for the exchange (code) tests. */
    private AuthController controllerWith(OneTimeCodeStore store) {
        return new AuthController(jwtService, new JwtSessionGuard(), encoder,
                new RateLimiter(), new LoginAttemptTracker(), store, mfaDisabled, "admin", encoder.encode("secret"));
    }

    /** Controller with admin=admin/secret configured — used for the password-login tests. */
    private AuthController loginController() {
        return new AuthController(jwtService, new JwtSessionGuard(), encoder,
                new RateLimiter(), new LoginAttemptTracker(), new OneTimeCodeStore(),
                mfaDisabled, "admin", encoder.encode("secret"));
    }

    /** Controller with MFA enabled — login should return a PRE_AUTH token, not the full token. */
    private AuthController loginControllerMfaEnabled() {
        MfaService mfaOn = mock(MfaService.class);
        when(mfaOn.isEnabled()).thenReturn(true);
        return new AuthController(jwtService, new JwtSessionGuard(), encoder,
                new RateLimiter(), new LoginAttemptTracker(), new OneTimeCodeStore(),
                mfaOn, "admin", encoder.encode("secret"));
    }

    private ResponseStatusException attemptLogin(AuthController c, String user, String pass,
                                                 HttpServletResponse response) {
        return assertThrows(ResponseStatusException.class, () ->
                c.login(new LoginRequest(user, pass), new MockHttpServletRequest(), response));
    }

    // ── Exchange (one-time code) ────────────────────────────────────────────

    @Test
    void exchangeValidCodeReturnsToken() {
        OneTimeCodeStore store = new OneTimeCodeStore();
        String code = store.issue("the-jwt", 28_800);
        AuthController controller = controllerWith(store);

        LoginResponse res = controller.exchange(new OAuthExchangeRequest(code),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertEquals("the-jwt", res.token());
        assertEquals(28_800, res.expiresIn());
    }

    @Test
    void exchangeUnknownCodeIsRejected() {
        AuthController controller = controllerWith(new OneTimeCodeStore());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.exchange(new OAuthExchangeRequest("bogus"),
                        new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertTrue(ex.getStatusCode().is4xxClientError());
    }

    @Test
    void exchangeNullBodyIsRejected() {
        AuthController controller = controllerWith(new OneTimeCodeStore());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.exchange(null, new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertTrue(ex.getStatusCode().is4xxClientError());
    }

    @Test
    void exchangeAlreadyUsedCodeIsRejected() {
        OneTimeCodeStore store = new OneTimeCodeStore();
        String code = store.issue("jwt", 10);
        AuthController controller = controllerWith(store);

        controller.exchange(new OAuthExchangeRequest(code),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.exchange(new OAuthExchangeRequest(code),
                        new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertTrue(ex.getStatusCode().is4xxClientError());
    }

    // ── Password login: lockout + error hygiene ─────────────────────────────

    @Test
    void validCredentialsReturnToken() {
        AuthController controller = loginController();
        LoginResponse res = controller.login(new LoginRequest("admin", "secret"),
                new MockHttpServletRequest(), new MockHttpServletResponse());
        assertNotNull(res.token());
        assertFalse(res.mfaRequired(), "no MFA enrolled → full token, no second factor");
        assertEquals(jwtService.getExpirySeconds(), res.expiresIn());
    }

    @Test
    void loginWithMfaEnabledReturnsPreAuthTokenOnly() {
        AuthController controller = loginControllerMfaEnabled();
        LoginResponse res = controller.login(new LoginRequest("admin", "secret"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertTrue(res.mfaRequired(), "MFA-enrolled login must demand a second factor");
        assertEquals(jwtService.getPreAuthExpirySeconds(), res.expiresIn());
        // The token must be PRE_AUTH (not a full ADMIN token).
        assertEquals(JwtService.ROLE_PRE_AUTH,
                jwtService.parseClaims(res.token()).get(JwtService.ROLE_CLAIM, String.class));
    }

    @Test
    void sixthFailedLoginIsLockedOutWithRetryAfter() {
        AuthController controller = loginController();

        // 5 wrong-password attempts each return 401 (invalid credentials).
        for (int i = 1; i <= 5; i++) {
            ResponseStatusException ex = attemptLogin(controller, "admin", "wrong", new MockHttpServletResponse());
            assertEquals(401, ex.getStatusCode().value(), "attempt " + i + " should be 401");
        }

        // The 6th is locked out: 429 with a Retry-After hint.
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseStatusException sixth = attemptLogin(controller, "admin", "wrong", response);
        assertEquals(429, sixth.getStatusCode().value());
        assertNotNull(response.getHeader("Retry-After"), "429 must carry a Retry-After header");
    }

    @Test
    void wrongPasswordAndUnknownUserReturnIdenticalError() {
        AuthController controller = loginController();

        ResponseStatusException wrongPassword =
                attemptLogin(controller, "admin", "wrong", new MockHttpServletResponse());
        ResponseStatusException unknownUser =
                attemptLogin(controller, "ghost", "wrong", new MockHttpServletResponse());

        // No user enumeration: same status and same body for both cases.
        assertEquals(401, wrongPassword.getStatusCode().value());
        assertEquals(401, unknownUser.getStatusCode().value());
        assertEquals("Invalid credentials", wrongPassword.getReason());
        assertEquals(wrongPassword.getReason(), unknownUser.getReason());
    }
}
