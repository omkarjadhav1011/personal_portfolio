package com.portfolio.telemetry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests on the D1 recorder (constructed directly — no Spring, no @Async proxy).
 * The contract under test: telemetry is fail-open (a throwing repository never propagates)
 * and privacy-preserving (raw IP never reaches the row).
 */
class EngagementRecorderTest {

    private final EngagementEventRepository repository = mock(EngagementEventRepository.class);
    private final EngagementRecorder recorder = new EngagementRecorder(repository);

    @Test
    void failingInsertDoesNotPropagate() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() ->
                recorder.record(EngagementType.RESUME_DOWNLOAD, null, "203.0.113.9", null));
    }

    @Test
    void storesHashedIpNeverRaw() {
        recorder.record(EngagementType.RECRUITER_MATCH, null, "203.0.113.9", 82);
        ArgumentCaptor<EngagementEvent> captor = ArgumentCaptor.forClass(EngagementEvent.class);
        verify(repository).save(captor.capture());
        EngagementEvent saved = captor.getValue();
        assertNotEquals("203.0.113.9", saved.getClientIpHash(), "raw IP must never be stored");
        assertEquals(64, saved.getClientIpHash().length(), "expected a SHA-256 hex hash");
        assertEquals(82, saved.getScore());
    }

    @Test
    void nullIpIsStoredAsNullHash() {
        recorder.record(EngagementType.CHAT_SESSION, null, null, null);
        ArgumentCaptor<EngagementEvent> captor = ArgumentCaptor.forClass(EngagementEvent.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getClientIpHash());
    }

    @Test
    void overlongDetailIsTruncatedToColumnLimit() {
        recorder.record(EngagementType.MCP_TOOL, "x".repeat(500), "203.0.113.9", null);
        ArgumentCaptor<EngagementEvent> captor = ArgumentCaptor.forClass(EngagementEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(200, captor.getValue().getDetail().length());
    }
}
