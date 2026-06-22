package com.portfolio.rag;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the vector index from the live corpus (Phase C3). {@link #reindexAll()} chunks the public
 * snapshot, embeds every chunk in one batch, upserts them (keyed by source so re-runs replace, not
 * duplicate), and prunes managed-type rows whose source no longer exists. No-op when the embedding
 * client is unconfigured (no key) so the app degrades gracefully to full-context retrieval.
 */
@Service
public class CorpusIndexer {

    private static final Logger log = LoggerFactory.getLogger(CorpusIndexer.class);

    /** Corpus types owned by reindexAll (resume text now flows in via PortfolioContext. */
    private static final List<String> MANAGED_TYPES = List.of(
            IndexableChunk.TYPE_PROFILE, IndexableChunk.TYPE_PROJECT, IndexableChunk.TYPE_EXPERIENCE,
            IndexableChunk.TYPE_SKILLS, IndexableChunk.TYPE_SKILL_DIFF, IndexableChunk.TYPE_RESUME);

    private final PortfolioContextService contextService;
    private final CorpusChunker chunker;
    private final GeminiEmbeddingClient embeddingClient;
    private final EmbeddingRepository repository;

    public CorpusIndexer(PortfolioContextService contextService,
                         CorpusChunker chunker,
                         GeminiEmbeddingClient embeddingClient,
                         EmbeddingRepository repository) {
        this.contextService = contextService;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.repository = repository;
    }

    /** Re-embeds and upserts the whole corpus; prunes managed-type rows that disappeared. Returns chunks indexed. */
    public int reindexAll() {
        if (!embeddingClient.isConfigured()) {
            log.info("[rag] embedding client not configured (no GEMINI_API_KEY) — skipping reindex");
            return 0;
        }

        PortfolioContext ctx = contextService.getContext();
        List<IndexableChunk> chunks = chunker.chunk(ctx);
        if (chunks.isEmpty()) {
            log.warn("[rag] corpus produced no chunks — nothing to index");
            return 0;
        }

        List<float[]> vectors = embeddingClient.embedDocuments(chunks.stream().map(IndexableChunk::text).toList());
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException(
                    "Embedding count " + vectors.size() + " != chunk count " + chunks.size());
        }

        // Collect kept ids per managed type as we upsert, so we can prune removed sources afterwards.
        Map<String, List<String>> keepByType = new LinkedHashMap<>();
        MANAGED_TYPES.forEach(t -> keepByType.put(t, new java.util.ArrayList<>()));

        for (int i = 0; i < chunks.size(); i++) {
            IndexableChunk c = chunks.get(i);
            repository.upsert(c.sourceType(), c.sourceId(), c.text(), vectors.get(i));
            keepByType.computeIfAbsent(c.sourceType(), k -> new java.util.ArrayList<>()).add(c.sourceId());
        }

        int pruned = 0;
        for (String type : MANAGED_TYPES) {
            pruned += repository.deleteByTypeNotIn(type, keepByType.get(type));
        }

        log.info("[rag] reindexed {} chunks ({} stale rows pruned)", chunks.size(), pruned);
        return chunks.size();
    }
}
