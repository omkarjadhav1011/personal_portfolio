package com.portfolio.recruiter;

import com.portfolio.chatbot.PortfolioContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The deterministic scoring core of recruiter mode: turns the LLM's {@link JdExtraction} plus the
 * portfolio snapshot into a {@link MatchResult} whose {@code fitScore} comes from arithmetic, not
 * from the model. Pure and stateless — the same extraction and context always produce a
 * byte-identical result, regardless of which LLM provider did the extraction.
 *
 * <p>Per requirement: a normalized match against the portfolio's skill list earns full credit; a
 * match found only in project/experience tags earns partial credit (evidenced, but not a listed
 * skill — still surfaced as a gap so the display never flatters the score). The fit score is a
 * weighted coverage of must-have vs nice-to-have requirements.
 */
@Component
public class MatchScoreCalculator {

    private static final double MUST_WEIGHT = 0.7;
    private static final double NICE_WEIGHT = 0.3;
    private static final double TAG_ONLY_CREDIT = 0.5;
    private static final String MUST_HAVE = "must-have";
    private static final String NICE_TO_HAVE = "nice-to-have";

    // Response caps — mirror matchResultSchema in frontend/src/lib/recruiter/types.ts.
    private static final int MAX_PROJECTS = 5;
    private static final int MAX_MATCHED_SKILLS = 12;
    private static final int MAX_GAP_SKILLS = 8;

    /** A scored match plus the per-criterion audit trail behind the number. */
    public record Scored(MatchResult result, Breakdown breakdown) {
    }

    /** The full audit breakdown: every input to the final number, loggable as one line. */
    public record Breakdown(
            int mustTotal, int niceTotal,
            double mustCredit, double niceCredit,
            double mustCoverage, double niceCoverage,
            List<RequirementCredit> perRequirement,
            int fitScore
    ) {
        static Breakdown empty() {
            return new Breakdown(0, 0, 0, 0, 0, 0, List.of(), 0);
        }
    }

    /** How one extracted requirement was credited. {@code matchedVia} is skill | tag | none. */
    public record RequirementCredit(String skill, String importance, double credit, String matchedVia) {
    }

    public Scored score(JdExtraction extraction, PortfolioContext ctx) {
        List<JdExtraction.Requirement> requirements =
                extraction.requirements() == null ? List.of() : extraction.requirements();
        if (!extraction.isJobDescription() || requirements.isEmpty()) {
            return new Scored(new MatchResult(0, List.of(), List.of(), List.of()), Breakdown.empty());
        }

        // Normalized name → canonical portfolio skill name. Insertion order follows the context's
        // deterministic branch/skill ordering; first occurrence wins.
        Map<String, String> skillsByKey = new LinkedHashMap<>();
        for (PortfolioContext.SkillBranchSummary branch : nullSafe(ctx.skillBranches())) {
            for (PortfolioContext.SkillSummary skill : nullSafe(branch.skills())) {
                skillsByKey.putIfAbsent(SkillNormalizer.normalize(skill.name()), skill.name());
            }
        }
        Set<String> tagKeys = new LinkedHashSet<>();
        for (PortfolioContext.ProjectSummary project : nullSafe(ctx.projects())) {
            nullSafe(project.tags()).forEach(t -> tagKeys.add(SkillNormalizer.normalize(t)));
        }
        for (PortfolioContext.ExperienceSummary exp : nullSafe(ctx.experience())) {
            nullSafe(exp.tags()).forEach(t -> tagKeys.add(SkillNormalizer.normalize(t)));
        }

        List<RequirementCredit> credits = new ArrayList<>();
        List<MatchResult.MatchedSkill> matchedSkills = new ArrayList<>();
        List<MatchResult.GapSkill> gapSkills = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int mustTotal = 0;
        int niceTotal = 0;
        double mustCredit = 0;
        double niceCredit = 0;

        for (JdExtraction.Requirement req : requirements) {
            if (req == null || req.skill() == null || req.skill().isBlank()) {
                continue;
            }
            Match match = lookup(req.skill(), skillsByKey, tagKeys);
            // Dedupe on the resolved skill ("Java" and "Java (17 or 21)" are one requirement),
            // falling back to the normalized raw name when nothing matched.
            String seenKey = match.canonicalSkill() != null
                    ? SkillNormalizer.normalize(match.canonicalSkill())
                    : SkillNormalizer.normalize(req.skill());
            if (seenKey.isEmpty() || !seen.add(seenKey)) {
                continue; // duplicate requirement — count once, first occurrence wins
            }
            String importance = MUST_HAVE.equalsIgnoreCase(req.importance()) ? MUST_HAVE : NICE_TO_HAVE;

            if (match.canonicalSkill() != null) {
                matchedSkills.add(new MatchResult.MatchedSkill(match.canonicalSkill(), req.reason()));
            } else {
                gapSkills.add(new MatchResult.GapSkill(req.skill(), importance));
            }
            double credit = match.credit();
            credits.add(new RequirementCredit(req.skill(), importance, credit, match.via()));
            if (MUST_HAVE.equals(importance)) {
                mustTotal++;
                mustCredit += credit;
            } else {
                niceTotal++;
                niceCredit += credit;
            }
        }

        double mustCoverage = mustTotal == 0 ? 0 : mustCredit / mustTotal;
        double niceCoverage = niceTotal == 0 ? 0 : niceCredit / niceTotal;
        // An empty bucket shifts its weight to the other, so a JD with only must-haves (or only
        // nice-to-haves) is scored on what it actually asked for.
        double weighted;
        if (mustTotal == 0 && niceTotal == 0) {
            weighted = 0;
        } else if (mustTotal == 0) {
            weighted = niceCoverage;
        } else if (niceTotal == 0) {
            weighted = mustCoverage;
        } else {
            weighted = MUST_WEIGHT * mustCoverage + NICE_WEIGHT * niceCoverage;
        }
        int fitScore = Math.clamp(Math.round(100 * weighted), 0, 100);

        List<MatchResult.MatchedProject> matchedProjects = validProjects(extraction, ctx);

        MatchResult result = new MatchResult(
                fitScore,
                cap(matchedProjects, MAX_PROJECTS),
                cap(matchedSkills, MAX_MATCHED_SKILLS),
                cap(gapSkills, MAX_GAP_SKILLS));
        Breakdown breakdown = new Breakdown(mustTotal, niceTotal, mustCredit, niceCredit,
                mustCoverage, niceCoverage, List.copyOf(credits), fitScore);
        return new Scored(result, breakdown);
    }

    /** One requirement's resolution: {@code canonicalSkill} is null unless matched via the skill list. */
    private record Match(String canonicalSkill, double credit, String via) {
    }

    /**
     * Resolves a requirement name against the portfolio. Exact normalized match first; if the
     * extraction produced a compound or versioned phrase ("Java (17 or 21)", "React with
     * TypeScript") that survives the prompt's atomic-skill rule, fall back to deterministic
     * token/bigram matching (bigrams first — "spring boot" before "spring"; skill list before
     * tags; left to right, first hit wins). This keeps scoring stable across the extraction
     * granularity quirks of different failover models.
     */
    private static Match lookup(String rawSkill, Map<String, String> skillsByKey, Set<String> tagKeys) {
        String key = SkillNormalizer.normalize(rawSkill);
        if (skillsByKey.containsKey(key)) {
            return new Match(skillsByKey.get(key), 1.0, "skill");
        }
        if (tagKeys.contains(key)) {
            return new Match(null, TAG_ONLY_CREDIT, "tag");
        }
        List<String> candidates = tokenCandidates(rawSkill);
        for (String candidate : candidates) {
            if (skillsByKey.containsKey(candidate)) {
                return new Match(skillsByKey.get(candidate), 1.0, "skill-token");
            }
        }
        for (String candidate : candidates) {
            if (tagKeys.contains(candidate)) {
                return new Match(null, TAG_ONLY_CREDIT, "tag-token");
            }
        }
        return new Match(null, 0, "none");
    }

    /** Normalized bigrams then unigrams of the raw name, in order — e.g. "React with TypeScript" → ["reactwith", "withtypescript", "react", "with", "typescript"]. */
    private static List<String> tokenCandidates(String rawSkill) {
        String[] words = rawSkill.trim().toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9+#]+");
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i + 1 < words.length; i++) {
            candidates.add(SkillNormalizer.normalize(words[i] + " " + words[i + 1]));
        }
        for (String word : words) {
            candidates.add(SkillNormalizer.normalize(word));
        }
        return candidates;
    }

    /** Keeps only narrative projects whose slug exists in the portfolio — invented slugs are dropped. */
    private static List<MatchResult.MatchedProject> validProjects(JdExtraction extraction, PortfolioContext ctx) {
        Set<String> slugs = new LinkedHashSet<>();
        for (PortfolioContext.ProjectSummary project : nullSafe(ctx.projects())) {
            slugs.add(project.slug());
        }
        List<MatchResult.MatchedProject> valid = new ArrayList<>();
        for (MatchResult.MatchedProject project : nullSafe(extraction.matchedProjects())) {
            if (project != null && project.slug() != null && slugs.contains(project.slug())) {
                valid.add(new MatchResult.MatchedProject(
                        project.slug(),
                        project.reason(),
                        project.relevantTags() == null ? List.of() : project.relevantTags()));
            }
        }
        return valid;
    }

    private static <T> List<T> cap(List<T> list, int max) {
        return list.size() <= max ? List.copyOf(list) : List.copyOf(list.subList(0, max));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
