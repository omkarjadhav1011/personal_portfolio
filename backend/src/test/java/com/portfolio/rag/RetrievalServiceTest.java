package com.portfolio.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final GeminiEmbeddingClient embed = mock(GeminiEmbeddingClient.class);
    private final EmbeddingRepository repo = mock(EmbeddingRepository.class);
    private final RetrievalService service = new RetrievalService(embed, repo);

    @Test
    void emptyWhenEmbeddingNotConfigured() {
        when(embed.isConfigured()).thenReturn(false);
        assertTrue(service.retrieve("What are his skills?", 5).isEmpty());
        verify(embed, never()).embedQuery(any());
    }

    @Test
    void emptyWhenQueryBlank() {
        when(embed.isConfigured()).thenReturn(true);
        assertTrue(service.retrieve("   ", 5).isEmpty());
        verify(repo, never()).count();
    }

    @Test
    void emptyWhenIndexEmpty() {
        when(embed.isConfigured()).thenReturn(true);
        when(repo.count()).thenReturn(0L);
        assertTrue(service.retrieve("q", 5).isEmpty());
        verify(embed, never()).embedQuery(any());
    }

    @Test
    void returnsJoinedChunksOnHit() {
        when(embed.isConfigured()).thenReturn(true);
        when(repo.count()).thenReturn(3L);
        when(embed.embedQuery("postgres project")).thenReturn(new float[]{0.1f, 0.2f});
        when(repo.findNearest(any(), anyInt())).thenReturn(List.of(
                new EmbeddingRepository.Neighbor("project", "vault", "Project: vault uses Postgres.", 0.12),
                new EmbeddingRepository.Neighbor("skills", "backend", "Skill group backend: Postgres.", 0.20)));

        Optional<String> result = service.retrieve("postgres project", 5);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Project: vault uses Postgres."));
        assertTrue(result.get().contains("Skill group backend: Postgres."));
    }

    @Test
    void fallsBackWhenEmbeddingThrows() {
        when(embed.isConfigured()).thenReturn(true);
        when(repo.count()).thenReturn(3L);
        when(embed.embedQuery(any())).thenThrow(new RuntimeException("429 Too Many Requests"));
        assertTrue(service.retrieve("q", 5).isEmpty(), "embedding failure → fall back to full context");
    }

    @Test
    void emptyWhenNoHits() {
        when(embed.isConfigured()).thenReturn(true);
        when(repo.count()).thenReturn(3L);
        when(embed.embedQuery(any())).thenReturn(new float[]{0.1f});
        when(repo.findNearest(any(), anyInt())).thenReturn(List.of());
        assertTrue(service.retrieve("q", 5).isEmpty());
    }
}
