package com.portfolio.rag;

import com.portfolio.chatbot.PortfolioContextService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReindexTriggerTest {

    private final PortfolioContextService contextService = mock(PortfolioContextService.class);
    private final CorpusIndexer indexer = mock(CorpusIndexer.class);
    private final ReindexTrigger trigger = new ReindexTrigger(contextService, indexer);

    @Test
    void resetsCacheBeforeReindexing() {
        when(indexer.reindexAll()).thenReturn(7);
        trigger.reindexAsync();

        // Cache must be cleared BEFORE reindex so the new embeddings reflect the just-saved data.
        InOrder order = inOrder(contextService, indexer);
        order.verify(contextService).resetCache();
        order.verify(indexer).reindexAll();
    }

    @Test
    void swallowsReindexFailures() {
        doThrow(new RuntimeException("429 Too Many Requests")).when(indexer).reindexAll();
        trigger.reindexAsync(); // must not throw — admin save already succeeded
        verify(contextService).resetCache();
        verify(indexer).reindexAll();
    }
}
