package com.portfolio.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG retrieval (Phase C4): embed the visitor's question and fetch the top-k nearest chunks from
 * the vector store, to ground the answer in only the relevant slice of the corpus (fewer tokens,
 * lighter on quota, more on-topic than stuffing everything in).
 *
 * <p>Fail-soft: returns {@link Optional#empty()} — signalling the caller to fall back to the
 * full-context snapshot — whenever embeddings are unconfigured, the index is empty, or the
 * embedding call fails (e.g. a transient 429). Chat must never break because retrieval is down.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    public static final int DEFAULT_K = 5;

    private final GeminiEmbeddingClient embeddingClient;
    private final EmbeddingRepository repository;

    public RetrievalService(GeminiEmbeddingClient embeddingClient, EmbeddingRepository repository) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
    }

    /** Top-k chunk texts joined for the {@code <reference_data>} block, or empty to fall back. */
    public Optional<String> retrieve(String query, int k) {
        if (!embeddingClient.isConfigured() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        try {
            if (repository.count() == 0) {
                return Optional.empty();
            }
            float[] queryVector = embeddingClient.embedQuery(query);
            List<EmbeddingRepository.Neighbor> hits = repository.findNearest(queryVector, k);
            if (hits.isEmpty()) {
                return Optional.empty();
            }
            if (log.isDebugEnabled()) {
                log.debug("[rag] retrieved {} chunks for query: {}", hits.size(),
                        hits.stream().map(h -> h.sourceType() + "/" + h.sourceId()).toList());
            }
            String joined = hits.stream()
                    .map(EmbeddingRepository.Neighbor::chunkText)
                    .collect(Collectors.joining("\n\n"));
            return Optional.of(joined);
        } catch (Exception e) {
            log.warn("[rag] retrieval failed; falling back to full context: {}", e.toString());
            return Optional.empty();
        }
    }
}
