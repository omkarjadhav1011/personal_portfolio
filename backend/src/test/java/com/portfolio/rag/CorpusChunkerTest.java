package com.portfolio.rag;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContext.ExperienceSummary;
import com.portfolio.chatbot.PortfolioContext.ProfileSummary;
import com.portfolio.chatbot.PortfolioContext.ProjectSummary;
import com.portfolio.chatbot.PortfolioContext.SkillBranchSummary;
import com.portfolio.chatbot.PortfolioContext.SkillDiffSummary;
import com.portfolio.chatbot.PortfolioContext.SkillSummary;
import com.portfolio.profile.SocialLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusChunkerTest {

    private final CorpusChunker chunker = new CorpusChunker();

    @Test
    void buildsOneChunkPerItemWithStableIds() {
        List<IndexableChunk> chunks = chunker.chunk(sampleContext());

        // 1 profile + 2 projects + 1 experience + 2 skill branches + 1 skill-diff = 7
        assertEquals(7, chunks.size());

        // Every chunk has a non-blank id and text.
        for (IndexableChunk c : chunks) {
            assertFalse(c.sourceId() == null || c.sourceId().isBlank(), "sourceId present: " + c);
            assertFalse(c.text() == null || c.text().isBlank(), "text present: " + c);
        }

        IndexableChunk profile = findOne(chunks, IndexableChunk.TYPE_PROFILE);
        assertEquals("main", profile.sourceId());
        assertTrue(profile.text().contains("Omkar Jadhav"), "profile names the person");
        assertTrue(profile.text().contains("Available for work"), "profile states availability");

        // Project ids are the slugs (stable for re-indexing in Phase D).
        IndexableChunk proj = findById(chunks, IndexableChunk.TYPE_PROJECT, "vault");
        assertTrue(proj.text().contains("encrypted"), "project text carries the description");
        assertTrue(proj.text().contains("Java"), "project text carries the language");

        IndexableChunk exp = findOne(chunks, IndexableChunk.TYPE_EXPERIENCE);
        assertTrue(exp.text().contains("NonStop"), "experience names the org");

        long skillBranches = chunks.stream().filter(c -> c.sourceType().equals(IndexableChunk.TYPE_SKILLS)).count();
        assertEquals(2, skillBranches);
        IndexableChunk backend = findById(chunks, IndexableChunk.TYPE_SKILLS, "backend");
        assertTrue(backend.text().contains("Spring Boot"), "skill branch lists its skills");

        IndexableChunk diff = findOne(chunks, IndexableChunk.TYPE_SKILL_DIFF);
        assertTrue(diff.text().contains("Rust"), "skill-diff carries the diff entries");
    }

    @Test
    void emptyContextYieldsOnlyProfile() {
        var profile = new ProfileSummary("Solo Dev", "solo", "Builder", "Bio", "main", "OK", false,
                "s@x.com", "Earth", List.of(), List.of(), List.of());
        List<IndexableChunk> chunks = chunker.chunk(
                new PortfolioContext(profile, List.of(), List.of(), List.of(), List.of()));
        assertEquals(1, chunks.size());
        assertEquals(IndexableChunk.TYPE_PROFILE, chunks.get(0).sourceType());
    }

    private static IndexableChunk findOne(List<IndexableChunk> chunks, String type) {
        return chunks.stream().filter(c -> c.sourceType().equals(type)).findFirst().orElseThrow();
    }

    private static IndexableChunk findById(List<IndexableChunk> chunks, String type, String id) {
        return chunks.stream()
                .filter(c -> c.sourceType().equals(type) && c.sourceId().equals(id))
                .findFirst().orElseThrow();
    }

    private PortfolioContext sampleContext() {
        var profile = new ProfileSummary(
                "Omkar Jadhav", "omkarjadhav", "Full-Stack Dev", "Loves building.",
                "main", "Shipping features", true, "omkar@example.com", "Pune, India",
                List.of(new SocialLink("GitHub", "https://github.com/x", "github")),
                List.of("Plays chess"), List.of());
        var p1 = new ProjectSummary("vault", "secure-vault", "An encrypted document store.",
                "Uses envelope encryption with AES-256-GCM.", "Java", List.of("Spring Boot", "Postgres"),
                12, 2, 340, "active", true, "https://live", "https://repo", "2026-06", "init vault");
        var p2 = new ProjectSummary("portfolio", "portfolio", "A terminal-themed portfolio.",
                null, "TypeScript", List.of("React", "Vite"), 5, 0, 120, "active", true, null, null, "2026-05", "tweak");
        var exp = new ExperienceSummary("work", "Backend Engineer", "NonStop io", "2024", null,
                List.of("Built APIs.", "Owned auth."), List.of("Java", "Spring"), "main", "https://co");
        var backend = new SkillBranchSummary("backend",
                List.of(new SkillSummary("Spring Boot", 5, "daily"), new SkillSummary("Postgres", 4, "daily")));
        var frontend = new SkillBranchSummary("frontend",
                List.of(new SkillSummary("React", 4, "daily")));
        var diff = List.of(new SkillDiffSummary("Rust", "learning", "exploring systems programming"));
        return new PortfolioContext(profile, List.of(p1, p2), List.of(exp), List.of(backend, frontend), diff);
    }
}
