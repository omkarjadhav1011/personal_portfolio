package com.portfolio.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full app with NO OAuth keys and asserts the hardened authorization surface:
 * public reads pass, every admin mutation is rejected unauthenticated, and the public auth
 * endpoints are reachable. Also proves the "optional at startup" requirement (no OAuth bean).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "JWT_SECRET=unit-test-secret-key-that-is-at-least-32-bytes-long",
        // Force OAuth off regardless of any local .env (imported via application.yml) so this
        // test is deterministic on every machine. Test-source precedence beats the .env import.
        "GOOGLE_CLIENT_ID=",
        "GITHUB_CLIENT_ID=",
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Test
    void contextLoadsWithoutOAuthKeys() {
        assertEquals(0, context.getBeanNamesForType(ClientRegistrationRepository.class).length,
                "no OAuth keys configured → ClientRegistrationRepository must be absent (password-only boot)");
    }

    @Test
    void publicPortfolioReadsArePermitted() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedAdminMutationsAreRejected() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/reorder").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedDriveAccessIsRejected() throws Exception {
        // The keystone: drive reads AND writes are ADMIN-only — a GET must not slip through the
        // public GET /** catch-all. (No controller is wired here, but security runs first → 401.)
        mvc.perform(get("/api/drive/folders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/drive/folders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void driveDownloadTokenRouteIsPublic() throws Exception {
        // The single-use token IS the auth, so this route is permitAll and placed above the ADMIN
        // matcher. It passed the security layer iff it is NOT blocked (401/403). The exact code
        // depends on whether the env-gated vault is wired: with no STORAGE_ENDPOINT there is no
        // controller → 404; when a developer's local .env enables the vault, an unknown token →
        // 410 Gone. Either way the matcher ordering is correct.
        mvc.perform(get("/api/drive/download/some-token")).andExpect(result -> {
            int status = result.getResponse().getStatus();
            assertTrue(status == 404 || status == 410,
                    "public download route should pass security (expected 404 or 410) but was " + status);
        });
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        // Always-on hardening headers (HSTS is emitted only over HTTPS, so not asserted here).
        mvc.perform(get("/api/projects"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void preAuthTokenCannotReachAdminRoutes() throws Exception {
        // A PRE_AUTH token (first factor passed, MFA pending) grants ONLY ROLE_PRE_AUTH, so an
        // admin route returns 403 (authenticated but forbidden) — not access.
        String preAuth = jwtService.generatePreAuth("admin");
        mvc.perform(post("/api/projects").header("Authorization", "Bearer " + preAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mfaVerifyIsReachableByPreAuthToken() throws Exception {
        // The matcher permits a PRE_AUTH token through to the controller; with no MFA enrolled the
        // code is invalid → 401 (reached the controller), proving it's not blocked at 403.
        String preAuth = jwtService.generatePreAuth("admin");
        mvc.perform(post("/api/auth/mfa/verify").header("Authorization", "Bearer " + preAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEndpointIsPublicAndRejectsBadCredentials() throws Exception {
        // A non-permitted route would be stopped at the security entry point ("Unauthorized").
        // Reaching the controller's uniform "Invalid credentials" body proves login is public.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid credentials"));
    }
}
