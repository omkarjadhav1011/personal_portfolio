package com.portfolio.recruiter;

import com.portfolio.chatbot.PortfolioContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deterministic scoring core. The fit score must be pure arithmetic over the extraction and
 * the portfolio: same inputs ⇒ byte-identical outputs, strong JDs score above partial ones, and
 * the displayed gap skills can never disagree with the score.
 */
class MatchScoreCalculatorTest {

    private final MatchScoreCalculator calculator = new MatchScoreCalculator();

    /** Portfolio: skills Java, Spring Boot, PostgreSQL, React; project/experience tags Docker, Redis. */
    private static PortfolioContext sampleContext() {
        var profile = new PortfolioContext.ProfileSummary(
                "Omkar Jadhav", "omkarjadhav", "Full-Stack Dev", "Builder.",
                "main", "Available", true, "omkar@example.com", "Pune, India",
                List.of(), List.of(), List.of());
        var backend = new PortfolioContext.SkillBranchSummary("backend", List.of(
                new PortfolioContext.SkillSummary("Java", 5, "lang"),
                new PortfolioContext.SkillSummary("Spring Boot", 5, "framework"),
                new PortfolioContext.SkillSummary("PostgreSQL", 4, "db")));
        var frontend = new PortfolioContext.SkillBranchSummary("frontend", List.of(
                new PortfolioContext.SkillSummary("React", 4, "framework")));
        var project = new PortfolioContext.ProjectSummary(
                "vault", "secure-vault", "Encrypted store.", null, "Java",
                List.of("Docker", "Spring Boot"), 1, 0, 0, "active", true,
                null, null, "2026", "msg");
        var experience = new PortfolioContext.ExperienceSummary(
                "job", "Engineer", "Acme", "2024", null, List.of(), List.of("Redis"), "main", null);
        return new PortfolioContext(profile, List.of(project), List.of(experience),
                List.of(backend, frontend), List.of(), null);
    }

    private static JdExtraction.Requirement must(String skill) {
        return new JdExtraction.Requirement(skill, "must-have", "the JD requires " + skill);
    }

    private static JdExtraction.Requirement nice(String skill) {
        return new JdExtraction.Requirement(skill, "nice-to-have", "the JD prefers " + skill);
    }

    @Test
    void weightsMustHavesAtSeventyPercent() {
        // musts: Java 1.0, Spring Boot 1.0, PostgreSQL 1.0, Kubernetes 0 → coverage 0.75
        // nices: React 1.0, Docker 0.5 (tag only) → coverage 0.75
        var extraction = new JdExtraction(true, List.of(
                must("Java"), must("Spring Boot"), must("PostgreSQL"), must("Kubernetes"),
                nice("React"), nice("Docker")), List.of());

        var scored = calculator.score(extraction, sampleContext());

        assertEquals(75, (int) scored.result().fitScore()); // 100 * (0.7*0.75 + 0.3*0.75)
        assertEquals(0.75, scored.breakdown().mustCoverage());
        assertEquals(0.75, scored.breakdown().niceCoverage());
    }

    @Test
    void matchesSkillsAcrossSpellingVariants() {
        var extraction = new JdExtraction(true, List.of(
                must("spring-boot"), must("Postgres"), nice("React.js")), List.of());

        var scored = calculator.score(extraction, sampleContext());

        assertEquals(100, (int) scored.result().fitScore());
        // Matched skills report the portfolio's canonical names, not the JD's spelling.
        List<String> matched = scored.result().matchedSkills().stream()
                .map(MatchResult.MatchedSkill::name).toList();
        assertEquals(List.of("Spring Boot", "PostgreSQL", "React"), matched);
    }

    @Test
    void tagOnlyEvidenceEarnsPartialCreditButStaysAGap() {
        var extraction = new JdExtraction(true, List.of(must("Docker"), must("Redis")), List.of());

        var scored = calculator.score(extraction, sampleContext());

        assertEquals(50, (int) scored.result().fitScore()); // 0.5 credit each
        assertEquals(2, scored.result().gapSkills().size(), "tag-only matches still shown as gaps");
        assertTrue(scored.result().matchedSkills().isEmpty());
    }

    @Test
    void emptyBucketShiftsWeightToTheOther() {
        // Only must-haves: score is pure must coverage, not 70% of it.
        var onlyMusts = new JdExtraction(true, List.of(must("Java"), must("Spring Boot")), List.of());
        assertEquals(100, (int) calculator.score(onlyMusts, sampleContext()).result().fitScore());

        // Only nice-to-haves: same, on the nice bucket.
        var onlyNices = new JdExtraction(true, List.of(nice("Java"), nice("Fortran")), List.of());
        assertEquals(50, (int) calculator.score(onlyNices, sampleContext()).result().fitScore());
    }

    @Test
    void nonJdOrEmptyExtractionScoresZero() {
        var spam = new JdExtraction(false, List.of(must("Java")), List.of());
        assertEquals(0, (int) calculator.score(spam, sampleContext()).result().fitScore());

        var empty = new JdExtraction(true, List.of(), List.of());
        assertEquals(0, (int) calculator.score(empty, sampleContext()).result().fitScore());

        var nullReqs = new JdExtraction(true, null, null);
        assertEquals(0, (int) calculator.score(nullReqs, sampleContext()).result().fitScore());
    }

    @Test
    void compoundAndVersionedNamesResolveViaTokenFallback() {
        // Extraction quirks that survive the atomic-skill prompt rule must still credit correctly.
        var extraction = new JdExtraction(true, List.of(
                must("Java (17 or 21)"), must("React with TypeScript"), must("Docker experience")), List.of());

        var scored = calculator.score(extraction, sampleContext());

        List<String> matched = scored.result().matchedSkills().stream()
                .map(MatchResult.MatchedSkill::name).toList();
        assertEquals(List.of("Java", "React"), matched, "resolved to canonical portfolio skills");
        // Java 1.0 + React 1.0 + Docker 0.5 (tag token) → 2.5/3
        assertEquals(83, (int) scored.result().fitScore());
    }

    @Test
    void versionedDuplicateOfAMatchedSkillCountsOnce() {
        var extraction = new JdExtraction(true, List.of(
                must("Java"), must("Java (17 or 21)"), must("Fortran")), List.of());

        var scored = calculator.score(extraction, sampleContext());

        assertEquals(2, scored.breakdown().mustTotal(), "both Java spellings resolve to one requirement");
        assertEquals(50, (int) scored.result().fitScore());
    }

    @Test
    void duplicateRequirementsCountOnce() {
        var extraction = new JdExtraction(true, List.of(
                must("Java"), must("java"), must("JAVA"), must("Fortran")), List.of());

        var scored = calculator.score(extraction, sampleContext());

        assertEquals(2, scored.breakdown().mustTotal());
        assertEquals(50, (int) scored.result().fitScore());
    }

    @Test
    void inventedProjectSlugsAreDropped() {
        var extraction = new JdExtraction(true, List.of(must("Java")), List.of(
                new MatchResult.MatchedProject("vault", "real project", List.of("Java")),
                new MatchResult.MatchedProject("made-up-slug", "hallucinated", null)));

        var scored = calculator.score(extraction, sampleContext());

        assertEquals(1, scored.result().matchedProjects().size());
        assertEquals("vault", scored.result().matchedProjects().get(0).slug());
    }

    @Test
    void strongPartialAndPoorJdsOrderCorrectly() {
        var strong = new JdExtraction(true, List.of(
                must("Java"), must("Spring Boot"), must("PostgreSQL"), nice("React")), List.of());
        var partial = new JdExtraction(true, List.of(
                must("Java"), must("Kubernetes"), must("Terraform"), nice("React")), List.of());
        var poor = new JdExtraction(true, List.of(
                must("Embedded C"), must("RTOS"), must("VHDL")), List.of());

        double strongScore = calculator.score(strong, sampleContext()).result().fitScore();
        double partialScore = calculator.score(partial, sampleContext()).result().fitScore();
        double poorScore = calculator.score(poor, sampleContext()).result().fitScore();

        assertTrue(strongScore > partialScore, "strong > partial");
        assertTrue(partialScore > poorScore, "partial > poor");
        assertEquals(0, (int) poorScore);
    }

    @Test
    void identicalInputProducesIdenticalOutputEveryTime() {
        var extraction = new JdExtraction(true, List.of(
                must("Java"), must("Kubernetes"), nice("Docker"), nice("GraphQL")), List.of());

        var first = calculator.score(extraction, sampleContext());
        for (int i = 0; i < 5; i++) {
            var again = calculator.score(extraction, sampleContext());
            assertEquals(first.result(), again.result());
            assertEquals(first.breakdown(), again.breakdown());
        }
    }
}
