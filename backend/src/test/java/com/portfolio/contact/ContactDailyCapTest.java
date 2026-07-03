package com.portfolio.contact;

import com.portfolio.common.counter.DailyCounterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H3 — the global contact-form daily cap, with the cap forced to 1: the first real submission
 * stores + succeeds, the second is a 429 that stores nothing, and honeypot drops never consume
 * the cap. Note: {@code ContactDailyCap} caches its count in memory per context, so the
 * counter row is reset BEFORE the context-bound cap first loads it (this class gets its own
 * context because of the property override).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "JWT_SECRET=unit-test-secret-key-that-is-at-least-32-bytes-long",
        "GOOGLE_CLIENT_ID=",
        "GITHUB_CLIENT_ID=",
        "CONTACT_DAILY_CAP=1",
})
class ContactDailyCapTest {

    private static final String EMAIL = "daily-cap-test@example.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ContactMessageRepository messageRepository;

    @Autowired
    private DailyCounterRepository counterRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        messageRepository.findAll().stream()
                .filter(m -> EMAIL.equals(m.getEmail()))
                .forEach(m -> messageRepository.deleteById(m.getId()));
        counterRepository.deleteById("contact-form");
    }

    private String body(String honeypot) {
        return "{\"name\":\"Cap Test\",\"email\":\"" + EMAIL + "\","
                + "\"message\":\"Daily cap verification message body.\","
                + "\"honeypot\":\"" + honeypot + "\"}";
    }

    private long storedRows() {
        return messageRepository.findAll().stream()
                .filter(m -> EMAIL.equals(m.getEmail()))
                .count();
    }

    @Test
    void secondSubmissionOverTheCapIs429AndStoresNothing() throws Exception {
        // Honeypot drop first: silent success, must NOT consume the cap of 1.
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content(body("bot-filled")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(0, storedRows(), "honeypot drop must not store a row");

        // First real submission consumes the whole cap and stores.
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(1, storedRows());

        // Second real submission: over cap → 429 envelope, nothing stored.
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.message").value(
                        "Daily message limit reached — please email me directly."));
        assertEquals(1, storedRows(), "an over-cap submission must not store a row");
    }
}
