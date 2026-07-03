package com.portfolio.recruiter;

import com.portfolio.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authenticated flow over the C3 leads admin API with a real minted ADMIN JWT (the
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
class RecruiterLeadAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RecruiterLeadRepository repository;

    private UUID savedId;

    @AfterEach
    void cleanUp() {
        if (savedId != null) {
            repository.deleteById(savedId);
            savedId = null;
        }
    }

    private RecruiterLead seedLead() {
        RecruiterLead saved = repository.save(new RecruiterLead(
                "lead-admin-test@example.com", "Test Co", "Looks like a fit.",
                82, List.of("Java", "Spring Boot"), "Senior backend engineer...",
                "0000000000000000000000000000000000000000000000000000000000000000"));
        savedId = saved.getId();
        return saved;
    }

    private String bearer() {
        return "Bearer " + jwtService.generate("admin");
    }

    @Test
    void listReturnsSeededLeadWithMatchContext() throws Exception {
        seedLead();
        mvc.perform(get("/api/admin/leads").param("status", "NEW")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("lead-admin-test@example.com"))
                .andExpect(jsonPath("$.content[0].company").value("Test Co"))
                .andExpect(jsonPath("$.content[0].fitScore").value(82))
                .andExpect(jsonPath("$.content[0].matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void patchFlipsStatusToReplied() throws Exception {
        RecruiterLead saved = seedLead();
        mvc.perform(patch("/api/admin/leads/" + saved.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REPLIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPLIED"));
    }

    @Test
    void patchWithUnknownStatusIsCleanBadRequest() throws Exception {
        RecruiterLead saved = seedLead();
        mvc.perform(patch("/api/admin/leads/" + saved.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NONSENSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void patchUnknownIdIsNotFound() throws Exception {
        mvc.perform(patch("/api/admin/leads/" + UUID.randomUUID())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READ\"}"))
                .andExpect(status().isNotFound());
    }
}
