package com.portfolio.query;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContext.ExperienceSummary;
import com.portfolio.chatbot.PortfolioContext.ProfileSummary;
import com.portfolio.chatbot.PortfolioContext.ProjectSummary;
import com.portfolio.chatbot.PortfolioContext.SkillBranchSummary;
import com.portfolio.chatbot.PortfolioContext.SkillSummary;
import com.portfolio.chatbot.PortfolioContextService;
import com.portfolio.profile.SocialLink;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase A1 — verifies the shared {@link PortfolioQueryService} facade: (1) {@link ProfileView}'s
 * shape exposes no sensitive field (defense by construction, mirroring
 * {@code chatbot.CorpusBoundaryTest}), and (2) {@code getProfile()} maps only the curated public
 * fields out of the underlying context, dropping bio/email/icon. A pure unit test — the context
 * service is mocked, so no Spring context or DB is needed.
 */
class PortfolioQueryServiceTest {

    /** Lowercased field-name fragments that must NEVER appear anywhere in a tool's output shape. */
    private static final List<String> FORBIDDEN = List.of(
            "password", "secret", "hash", "apikey", "token", "credential",
            "avatardata", "resumedata", "rawbytes", "bytes",
            "createdat", "updatedat", "deletedat", "internal");

    @Test
    void profileViewExposesNoSensitiveFields() {
        List<String> names = new ArrayList<>();
        collectComponentNames(ProfileView.class, names, new HashSet<>());
        assertTrue(names.size() >= 5, "should have discovered the ProfileView record components");

        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String bad : FORBIDDEN) {
                assertFalse(lower.contains(bad),
                        "ProfileView field '" + name + "' matches forbidden fragment '" + bad
                                + "' — a tool output must never expose sensitive data.");
            }
            assertNotEquals("id", lower, "raw entity id must not be exposed by a tool view");
            assertNotEquals("uuid", lower, "raw uuid must not be exposed by a tool view");
        }
    }

    @Test
    void getProfileMapsOnlyPublicFields() {
        // bio, email, fun facts, stash, and the link icon are present in the source summary but
        // must NOT survive into the public ProfileView.
        ProfileSummary summary = new ProfileSummary(
                "Omkar Jadhav", "omkarj", "Backend Engineer", "private-ish bio text",
                "main", "shipping features", true,
                "omkar@example.com", "Pune, India",
                List.of(new SocialLink("GitHub", "https://github.com/omkar", "github-icon")),
                List.of("fun fact"), List.of("stash item"));
        PortfolioContext ctx = new PortfolioContext(
                summary, List.of(), List.of(), List.of(), List.of(), "extracted resume text");

        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(ctx);

        ProfileView view = new PortfolioQueryService(contextService).getProfile();

        assertEquals("Omkar Jadhav", view.name());
        assertEquals("Backend Engineer", view.headline());
        assertEquals("main", view.currentBranch());
        assertEquals("shipping features", view.currentStatus());
        assertTrue(view.availableForWork());
        assertEquals("Pune, India", view.location());
        assertEquals(1, view.links().size());
        assertEquals("GitHub", view.links().get(0).label());
        assertEquals("https://github.com/omkar", view.links().get(0).url());
    }

    @Test
    void listProjectsMapsPublicFieldsAndDefaultsToAll() {
        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(contextWith(
                project("portfolio", "Portfolio", "Spring Boot API", "Java", List.of("Spring Boot", "Postgres")),
                project("game", "Game", "A WebGL game", "TypeScript", List.of("WebGL"))));

        List<ProjectView> all = new PortfolioQueryService(contextService).listProjects(null);

        assertEquals(2, all.size(), "a null filter returns every project");
        ProjectView first = all.get(0);
        assertEquals("portfolio", first.slug());
        assertEquals("Portfolio", first.name());
        assertEquals("Spring Boot API", first.description());
        assertEquals("Java", first.language());
        assertEquals(List.of("Spring Boot", "Postgres"), first.tags());
        assertEquals("https://repo/portfolio", first.repoUrl());
    }

    @Test
    void listProjectsFiltersByKeywordAcrossFields() {
        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(contextWith(
                project("portfolio", "Portfolio", "Spring Boot API", "Java", List.of("Spring Boot", "Postgres")),
                project("game", "Game", "A WebGL game", "TypeScript", List.of("WebGL"))));
        PortfolioQueryService svc = new PortfolioQueryService(contextService);

        assertEquals(1, svc.listProjects("postgres").size(), "tag match, case-insensitive");
        assertEquals("portfolio", svc.listProjects("java").get(0).slug(), "language match");
        assertEquals(1, svc.listProjects("webgl").size(), "matches the second project");
        assertEquals(0, svc.listProjects("rust").size(), "no match → empty");
        assertEquals(2, svc.listProjects("   ").size(), "blank filter returns all");
    }

    @Test
    void getExperienceComposesMatchingSkillsRolesAndProjects() {
        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(fullContext("resume"));

        ExperienceView ev = new PortfolioQueryService(contextService).getExperience("postgres");

        assertEquals("postgres", ev.query());
        assertEquals(1, ev.skills().size(), "only the Postgres skill matches");
        assertEquals("Postgres", ev.skills().get(0).name());
        assertEquals(4, ev.skills().get(0).level());
        assertEquals("Backend", ev.skills().get(0).branch());
        assertEquals(1, ev.roles().size(), "only the role tagged/described with Postgres matches");
        assertEquals("Backend Intern", ev.roles().get(0).title());
        assertEquals(1, ev.relatedProjects().size());
        assertEquals("api", ev.relatedProjects().get(0).slug());
    }

    @Test
    void getExperienceWithNoSkillReturnsEverythingButNoProjectList() {
        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(fullContext("resume"));

        ExperienceView ev = new PortfolioQueryService(contextService).getExperience(null);

        assertEquals(3, ev.skills().size(), "all skills");
        assertEquals(2, ev.roles().size(), "all roles");
        assertTrue(ev.relatedProjects().isEmpty(), "no project list without a skill filter");
    }

    @Test
    void getResumeSummaryComposesHighlightsAndExposesText() {
        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(fullContext("Experienced backend developer."));

        ResumeSummaryView rv = new PortfolioQueryService(contextService).getResumeSummary();

        assertEquals("Backend Developer", rv.headline());
        assertTrue(rv.resumeAvailable());
        assertEquals("Experienced backend developer.", rv.resumeText());
        assertEquals("Java", rv.topSkills().get(0), "top skills are sorted by level (Java=5 highest)");
        assertEquals(2, rv.keyRoles().size());
    }

    @Test
    void getResumeSummaryWhenNoResumeUploaded() {
        PortfolioContextService contextService = mock(PortfolioContextService.class);
        when(contextService.getContext()).thenReturn(fullContext(null));

        ResumeSummaryView rv = new PortfolioQueryService(contextService).getResumeSummary();

        assertFalse(rv.resumeAvailable(), "no résumé text → not available");
        assertNull(rv.resumeText());
        assertEquals("Backend Developer", rv.headline(), "structured highlights still returned");
        assertEquals(3, rv.topSkills().size());
    }

    private static PortfolioContext fullContext(String resumeText) {
        ProfileSummary profile = new ProfileSummary(
                "Omkar", "omkarj", "Backend Developer", "bio", "main", "status", true,
                "e@example.com", "Loc", List.of(), List.of(), List.of());
        List<ProjectSummary> projects = List.of(
                project("api", "API Service", "Spring Boot REST API", "Java", List.of("Spring Boot", "Postgres")),
                project("ui", "UI", "React app", "TypeScript", List.of("React")));
        List<ExperienceSummary> experience = List.of(
                new ExperienceSummary("work", "Backend Intern", "Acme", "2024", "2025",
                        List.of("Built services with Postgres"), List.of("Java", "Postgres"), "main", "https://acme"),
                new ExperienceSummary("work", "Frontend Dev", "Beta", "2023", "2024",
                        List.of("Built React UI"), List.of("React"), "main", "https://beta"));
        List<SkillBranchSummary> branches = List.of(
                new SkillBranchSummary("Backend",
                        List.of(new SkillSummary("Postgres", 4, "db"), new SkillSummary("Java", 5, "lang"))),
                new SkillBranchSummary("Frontend", List.of(new SkillSummary("React", 3, "lib"))));
        return new PortfolioContext(profile, projects, experience, branches, List.of(), resumeText);
    }

    private static PortfolioContext contextWith(ProjectSummary... projects) {
        ProfileSummary profile = new ProfileSummary(
                "Name", "handle", "Headline", "bio", "main", "status", true,
                "e@example.com", "Loc", List.of(), List.of(), List.of());
        return new PortfolioContext(profile, List.of(projects), List.of(), List.of(), List.of(), null);
    }

    private static ProjectSummary project(String slug, String repoName, String description,
                                          String language, List<String> tags) {
        return new ProjectSummary(slug, repoName, description, "long: " + description, language, tags,
                0, 0, 0, "active", false, "https://live/" + slug, "https://repo/" + slug, "abc123", "init");
    }

    /** Recursively gathers record-component names, descending into nested records and List&lt;Record&gt;. */
    private static void collectComponentNames(Class<?> type, List<String> out, Set<Class<?>> seen) {
        if (type == null || !type.isRecord() || !seen.add(type)) {
            return;
        }
        for (RecordComponent rc : type.getRecordComponents()) {
            out.add(rc.getName());
            Class<?> rcType = rc.getType();
            if (rcType.isRecord()) {
                collectComponentNames(rcType, out, seen);
            } else if (List.class.isAssignableFrom(rcType)
                    && rc.getGenericType() instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> argClass) {
                    collectComponentNames(argClass, out, seen);
                }
            }
        }
    }
}
