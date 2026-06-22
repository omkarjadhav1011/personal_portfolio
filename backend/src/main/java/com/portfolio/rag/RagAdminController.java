package com.portfolio.rag;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * ADMIN-only RAG maintenance (Phase C3). {@code /api/admin/**} is ADMIN-gated by SecurityConfig
 * (matched before the public GET catch-all), so both endpoints require a valid JWT.
 */
@Tag(name = "RAG admin", description = "Vector-index maintenance (ADMIN)")
@RestController
@RequestMapping("/api/admin/rag")
public class RagAdminController {

    private final CorpusIndexer indexer;
    private final EmbeddingRepository repository;

    public RagAdminController(CorpusIndexer indexer, EmbeddingRepository repository) {
        this.indexer = indexer;
        this.repository = repository;
    }

    @Operation(summary = "Re-embed and upsert the whole corpus")
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        try {
            int indexed = indexer.reindexAll();
            return Map.of("indexed", indexed, "total", repository.count());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Reindex failed (embedding provider error): " + e.getMessage());
        }
    }

    @Operation(summary = "Vector-index size")
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("total", repository.count());
    }
}
