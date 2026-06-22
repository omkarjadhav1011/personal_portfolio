package com.portfolio.rag;

import com.portfolio.chatbot.PortfolioContext;
import com.portfolio.chatbot.PortfolioContext.ExperienceSummary;
import com.portfolio.chatbot.PortfolioContext.ProjectSummary;
import com.portfolio.chatbot.PortfolioContext.SkillBranchSummary;
import com.portfolio.chatbot.PortfolioContext.SkillDiffSummary;
import com.portfolio.chatbot.PortfolioContext.SkillSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Turns a {@link PortfolioContext} into retrievable {@link IndexableChunk}s - one per
 * project, one per experience entry, one per skill branch, one for the profile summary, and one
 * for the skill diff. Each chunk is a compact natural-language rendering (better embeddings than
 * raw JSON) and carries a stable {@code sourceType}+{@code sourceId} so Phase D can re-index just
 * the changed item. Resume sections are added in Phase F.
 */
@Component
public class CorpusChunker {

    public List<IndexableChunk> chunk(PortfolioContext ctx) {
        List<IndexableChunk> chunks = new ArrayList<>();
        if (ctx == null) {
            return chunks;
        }

        chunks.add(profileChunk(ctx.profile()));

        for (ProjectSummary p : orEmpty(ctx.projects())) {
            chunks.add(projectChunk(p));
        }
        for (ExperienceSummary e : orEmpty(ctx.experience())) {
            chunks.add(experienceChunk(e));
        }
        for (SkillBranchSummary b : orEmpty(ctx.skillBranches())) {
            chunks.add(skillBranchChunk(b));
        }
        IndexableChunk diff = skillDiffChunk(orEmpty(ctx.skillDiff()));
        if (diff != null) {
            chunks.add(diff);
        }
        return chunks;
    }

    private IndexableChunk profileChunk(PortfolioContext.ProfileSummary p) {
        List<String> parts = new ArrayList<>();
        parts.add("Profile of " + p.name() + (notBlank(p.handle()) ? " (@" + p.handle() + ")" : "") + ".");
        addIf(parts, p.headline(), "Headline: " + p.headline() + ".");
        addIf(parts, p.bio(), "Bio: " + p.bio());
        addIf(parts, p.currentStatus(), "Current status: " + p.currentStatus() + ".");
        addIf(parts, p.currentBranch(), "Current focus/branch: " + p.currentBranch() + ".");
        parts.add(p.availableForWork() ? "Available for work." : "Not currently looking for work.");
        addIf(parts, p.location(), "Location: " + p.location() + ".");
        addIf(parts, p.email(), "Contact email: " + p.email() + ".");
        if (!orEmpty(p.funFacts()).isEmpty()) {
            parts.add("Fun facts: " + String.join("; ", p.funFacts()) + ".");
        }
        if (!orEmpty(p.stash()).isEmpty()) {
            parts.add("Also: " + String.join("; ", p.stash()) + ".");
        }
        return new IndexableChunk(IndexableChunk.TYPE_PROFILE, "main", joinParts(parts));
    }

    private IndexableChunk projectChunk(ProjectSummary p) {
        String name = notBlank(p.repoName()) ? p.repoName() : p.slug();
        List<String> parts = new ArrayList<>();
        parts.add("Project: " + name + ".");
        addIf(parts, p.description(), p.description());
        addIf(parts, p.longDescription(), p.longDescription());
        addIf(parts, p.language(), "Primary language: " + p.language() + ".");
        if (!orEmpty(p.tags()).isEmpty()) {
            parts.add("Tech/tags: " + String.join(", ", p.tags()) + ".");
        }
        addIf(parts, p.status(), "Status: " + p.status() + ".");
        parts.add(p.stars() + " stars, " + p.forks() + " forks, " + p.commits() + " commits.");
        addIf(parts, p.liveUrl(), "Live URL: " + p.liveUrl() + ".");
        addIf(parts, p.repoUrl(), "Repo URL: " + p.repoUrl() + ".");
        if (notBlank(p.lastCommitMsg())) {
            parts.add("Last commit: " + p.lastCommitMsg() + (notBlank(p.lastCommit()) ? " (" + p.lastCommit() + ")" : "") + ".");
        }
        return new IndexableChunk(IndexableChunk.TYPE_PROJECT, slugify(p.slug()), joinParts(parts));
    }

    private IndexableChunk experienceChunk(ExperienceSummary e) {
        List<String> parts = new ArrayList<>();
        String header = (notBlank(e.title()) ? e.title() : "Role")
                + (notBlank(e.org()) ? " at " + e.org() : "");
        parts.add(header + ".");
        addIf(parts, e.type(), "Type: " + e.type() + ".");
        String dates = notBlank(e.date()) ? e.date() + (notBlank(e.dateEnd()) ? " – " + e.dateEnd() : " – present") : null;
        addIf(parts, dates, "Dates: " + dates + ".");
        if (!orEmpty(e.description()).isEmpty()) {
            parts.add(String.join(" ", e.description()));
        }
        if (!orEmpty(e.tags()).isEmpty()) {
            parts.add("Tech/tags: " + String.join(", ", e.tags()) + ".");
        }
        addIf(parts, e.url(), "Link: " + e.url() + ".");
        String id = slugify((notBlank(e.title()) ? e.title() : "role") + "-" + (notBlank(e.org()) ? e.org() : "x"));
        return new IndexableChunk(IndexableChunk.TYPE_EXPERIENCE, id, joinParts(parts));
    }

    private IndexableChunk skillBranchChunk(SkillBranchSummary b) {
        String skills = orEmpty(b.skills()).stream()
                .map(this::renderSkill)
                .collect(Collectors.joining(", "));
        String text = "Skill group \"" + b.branchName() + "\": " + (skills.isEmpty() ? "(none listed)" : skills) + ".";
        return new IndexableChunk(IndexableChunk.TYPE_SKILLS, slugify(b.branchName()), text);
    }

    private String renderSkill(SkillSummary s) {
        StringBuilder sb = new StringBuilder(s.name());
        sb.append(" (level ").append(s.level()).append("/5");
        if (notBlank(s.tag())) {
            sb.append(", ").append(s.tag());
        }
        return sb.append(')').toString();
    }

    private IndexableChunk skillDiffChunk(List<SkillDiffSummary> diffs) {
        if (diffs.isEmpty()) {
            return null;
        }
        String body = diffs.stream()
                .map(d -> d.name() + " (" + d.type() + (notBlank(d.note()) ? ": " + d.note() : "") + ")")
                .collect(Collectors.joining(", "));
        return new IndexableChunk(IndexableChunk.TYPE_SKILL_DIFF, "main",
                "Recent skill changes / learning: " + body + ".");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void addIf(List<String> parts, String guard, String text) {
        if (notBlank(guard)) {
            parts.add(text);
        }
    }

    private static String joinParts(List<String> parts) {
        return parts.stream().filter(CorpusChunker::notBlank).collect(Collectors.joining(" ")).trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** Stable, URL-safe id from arbitrary text (lowercase, non-alphanumerics → single dashes). */
    static String slugify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isBlank() ? "unknown" : s;
    }
}
