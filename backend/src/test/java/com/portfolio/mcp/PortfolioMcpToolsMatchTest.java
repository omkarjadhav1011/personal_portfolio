package com.portfolio.mcp;

import com.portfolio.query.PortfolioQueryService;
import com.portfolio.recruiter.MatchProgressListener;
import com.portfolio.recruiter.MatchResult;
import com.portfolio.recruiter.RecruiterMatchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The MCP front door of the shared match implementation. The tool must delegate to the single
 * {@link RecruiterMatchService} (never a parallel path) and must work with no MCP exchange in the
 * tool context (falls back to the no-op progress listener rather than failing the match).
 */
class PortfolioMcpToolsMatchTest {

    private final PortfolioQueryService queryService = mock(PortfolioQueryService.class);
    private final RecruiterMatchService matchService = mock(RecruiterMatchService.class);
    private final PortfolioMcpTools tools = new PortfolioMcpTools(queryService, matchService);

    @Test
    void delegatesToSharedServiceWithoutAnExchange() {
        MatchResult canned = new MatchResult(75, List.of(), List.of(), List.of());
        when(matchService.match(eq("A Java role."), any(MatchProgressListener.class))).thenReturn(canned);

        // ToolContext without an MCP exchange (and the null case) must both fall back to NOOP.
        assertEquals(canned, tools.matchAgainstJd("A Java role.", new ToolContext(Map.of())));
        assertEquals(canned, tools.matchAgainstJd("A Java role.", null));
    }

    @Test
    void rejectsBlankAndOversizedJd() {
        assertThrows(IllegalArgumentException.class, () -> tools.matchAgainstJd("  ", null));
        assertThrows(IllegalArgumentException.class,
                () -> tools.matchAgainstJd("x".repeat(8001), null));
    }
}
