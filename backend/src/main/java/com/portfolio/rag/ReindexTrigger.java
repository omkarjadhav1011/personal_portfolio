package com.portfolio.rag;

import com.portfolio.chatbot.PortfolioContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs a full corpus re-index off the request thread after an admin write (Phase D1), so a save
 * stays fast and the next question reflects the change with no restart. Resets the 60s context
 * cache first so the re-index embeds the just-saved data, not a stale snapshot.
 *
 * <p>{@code synchronized} serializes overlapping triggers (e.g. drag-reordering fires many writes)
 * so concurrent re-indexes can't race on the embedding table. Failures are logged and swallowed —
 * a transient embedding error must never surface on the admin's save response.
 */
@Component
public class ReindexTrigger {

    private static final Logger log = LoggerFactory.getLogger(ReindexTrigger.class);

    private final PortfolioContextService contextService;
    private final CorpusIndexer indexer;

    public ReindexTrigger(PortfolioContextService contextService, CorpusIndexer indexer) {
        this.contextService = contextService;
        this.indexer = indexer;
    }

    @Async
    public synchronized void reindexAsync() {
        try {
            contextService.resetCache();
            int chunks = indexer.reindexAll();
            log.info("[rag] auto-reindex after admin write: {} chunks", chunks);
        } catch (Exception e) {
            log.warn("[rag] auto-reindex failed (retries on next change): {}", e.toString());
        }
    }
}
