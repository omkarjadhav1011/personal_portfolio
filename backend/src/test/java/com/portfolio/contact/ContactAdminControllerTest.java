package com.portfolio.contact;

import com.portfolio.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authenticated CRUD flow over the A2 admin inbox with a real minted ADMIN JWT (the
 * unauthenticated-401 side lives in SecurityConfigTest). Runs against the compose Postgres;
 * rows created here are cleaned up per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "JWT_SECRET=unit-test-secret-key-that-is-at-least-32-bytes-long",
        "GOOGLE_CLIENT_ID=",
        "GITHUB_CLIENT_ID=",
})
class ContactAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ContactMessageRepository repository;

    private UUID savedId;

    @AfterEach
    void cleanUp() {
        if (savedId != null) {
            repository.deleteById(savedId);
            savedId = null;
        }
    }

    private ContactMessage seedMessage() {
        ContactMessage saved = repository.save(new ContactMessage(
                "Inbox Test", "inbox-test@example.com", "A2 admin API test message body.",
                MessageSource.WEB, null));
        savedId = saved.getId();
        return saved;
    }

    private String bearer() {
        return "Bearer " + jwtService.generate("admin");
    }

    @Test
    void listReturnsSeededMessageNewestFirst() throws Exception {
        seedMessage();
        mvc.perform(get("/api/admin/messages").param("status", "NEW")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Inbox Test"))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].source").value("WEB"))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void patchFlipsStatusToRead() throws Exception {
        ContactMessage saved = seedMessage();
        mvc.perform(patch("/api/admin/messages/" + saved.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
    }

    @Test
    void patchWithUnknownStatusIsCleanBadRequest() throws Exception {
        ContactMessage saved = seedMessage();
        mvc.perform(patch("/api/admin/messages/" + saved.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NONSENSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void patchUnknownIdIsNotFound() throws Exception {
        mvc.perform(patch("/api/admin/messages/" + UUID.randomUUID())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READ\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTheRow() throws Exception {
        ContactMessage saved = seedMessage();
        mvc.perform(delete("/api/admin/messages/" + saved.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        assertFalse(repository.existsById(saved.getId()), "row should be gone after DELETE");
        savedId = null;
    }
}
