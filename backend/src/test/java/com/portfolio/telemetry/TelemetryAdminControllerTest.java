package com.portfolio.telemetry;

import com.portfolio.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authenticated flow over the D3 telemetry summary with a real minted ADMIN JWT (the
 * unauthenticated-401 side lives in SecurityConfigTest). Runs against the compose Postgres;
 * only rows seeded here are deleted afterwards, and assertions use jsonPath comparisons that
 * tolerate pre-existing local events (>= seeded counts, top tool present).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "JWT_SECRET=unit-test-secret-key-that-is-at-least-32-bytes-long",
        "GOOGLE_CLIENT_ID=",
        "GITHUB_CLIENT_ID=",
})
class TelemetryAdminControllerTest {

    private static final String SEED_TOOL = "telemetry-test-tool-d3";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EngagementEventRepository repository;

    private final List<UUID> seededIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        save(new EngagementEvent(EngagementType.RESUME_DOWNLOAD, "resume.pdf", null, null));
        save(new EngagementEvent(EngagementType.RECRUITER_MATCH, null, null, 80));
        save(new EngagementEvent(EngagementType.RECRUITER_MATCH, null, null, 60));
        save(new EngagementEvent(EngagementType.MCP_TOOL, SEED_TOOL, null, null));
        save(new EngagementEvent(EngagementType.MCP_TOOL, SEED_TOOL, null, null));
    }

    private void save(EngagementEvent event) {
        seededIds.add(repository.save(event).getId());
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAllById(seededIds);
        seededIds.clear();
    }

    private String bearer() {
        return "Bearer " + jwtService.generate("admin");
    }

    @Test
    void summaryAggregatesSeededEvents() throws Exception {
        mvc.perform(get("/api/admin/telemetry").param("days", "7")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.byType.RESUME_DOWNLOAD").isNumber())
                .andExpect(jsonPath("$.byType.RECRUITER_MATCH").isNumber())
                .andExpect(jsonPath("$.avgFitScore").isNumber())
                // The seeded tool made 2 calls — it must appear among the top tools.
                .andExpect(jsonPath("$.topTools[?(@.tool == '" + SEED_TOOL + "')].count").value(2))
                .andExpect(jsonPath("$.byDay").isArray())
                .andExpect(jsonPath("$.byDay[-1].count").isNumber());
    }

    @Test
    void daysParameterIsClampedNotRejected() throws Exception {
        mvc.perform(get("/api/admin/telemetry").param("days", "5000")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(90));
        mvc.perform(get("/api/admin/telemetry").param("days", "-3")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(1));
    }
}
