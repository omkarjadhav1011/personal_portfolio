---
title: "Keeping the score deterministic when an LLM is in the loop"
slug: "deterministic-scoring-beside-an-llm"
description: "Omkar Jadhav on splitting a feature so an LLM only extracts structure and plain Java arithmetic computes the score - same input, byte-identical result."
primaryKeyword: "deterministic scoring llm output"
status: draft-unverified
grounded: true
wordCount: 1720
verifyLensesRun:
  - "proprietary-leak"
verifyLensesNotRun:
  - "grounding"
  - "withdrawn-and-inflation"
  - "seo-structure"
unresolvedFindings:
  - "[proprietary-leak/low] \"audit log\" is the one phrase in the draft that collides with the employer's public record (\"Implemented end-to-end user audit functionality tracking and logging user actions\"). Here it means the portfolio backend's own scoring log, and the file citation makes that clear to a careful reader — but a skimmer, or an LLM summarizing the page next to the experience page, could read the described log shape (JD hash, bucket totals, credits, coverages, DEBUG per-requirement detail) as a description of the audit logging Omkar built at Nonstop IO. No employer detail is actually disclosed; this is a conflation risk, not a leak. -> Qualify the log as the portfolio's own, e.g. \"That breakdown is written to one audit log line in the portfolio backend's own logs per computed score, carrying the JD hash, bucket totals, credits and coverages, with per-requirement detail at DEBUG (`RecruiterMatchService.java:160-168`).\""
claimsNeedingConfirmation:
  - "The /blog/{slug} route does not exist in the repository - frontend/src/router.tsx has routes for about, projects, projects/:slug, experience, education, resume, recruiter and mcp, but no blog route and no content directory. Confirm the blog shell, the URL shape, and the Article JSON-LD (author pointing at the #person node) before this is published, or treat this delivery as prose only."
  - "Confirm the publication date to use, and whether the article should carry a visible 'last updated' date. The draft contains no publication date of its own; the only dated sentence is the August 2026 employment line."
  - "Confirm the canonical public GitHub URL, and whether the article should link to specific source files. The draft cites file paths and line numbers inline but adds no external links - if repository links are wanted, confirm whether to pin a commit SHA so the cited line numbers stay valid."
  - "Confirm that /recruiter is live in production and returns a score at publish time. RecruiterMatchService throws RecruiterMatchUnavailableException (503) when no LLM provider is configured, and the article links to the live feature twice."
  - "Confirm whether to mention the MCP front door. RecruiterMatchService's javadoc and PortfolioMcpTools show the same scoring implementation serving both the web endpoint and the public match_against_jd tool. It is omitted from this draft to keep the article scoped to the web endpoint; say the word and a one-sentence mention can be added."
  - "Confirm the exact employer sentence. The draft uses: 'Since August 2026 he has worked as a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, on backend development for an enterprise reporting product.' It is built only from the approved facts, but it is the one sentence in the piece about the day job."
  - "Confirm whether the 0.7 / 0.3 / 0.5 weights should be described as provisional and open to change. The draft calls them constants Omkar Jadhav chose for this feature and explicitly not an industry rubric, but does not say they may change."
  - "Confirm the four internal link targets resolve: /recruiter, /projects/portfolio-ai-assistant, /experience and /about. The first two are verified in router.tsx and data/projects.ts; /experience and /about are present in router.tsx as 'experience' and 'about'."
  - "The javadoc on MatchScoreCalculator.RequirementCredit (line 55) says matchedVia is 'skill | tag | none', but the implementation also emits 'skill-token' and 'tag-token'. The article describes the five actual values. Worth fixing the javadoc in the code so the two agree."
---
# Keeping the score deterministic when an LLM is in the loop

## A score that moves when nothing moved

Omkar Jadhav built recruiter mode into his portfolio at [/recruiter](/recruiter): paste a job description, and the page returns a fit score out of 100 along with matched skills, gap skills, and the projects that are relevant to the role.

The obvious way to build that is to hand the job description and the portfolio to a language model and ask it for a number. The failure mode is just as obvious once you see it: the same job description can come back as 78 one afternoon and 71 the next. Nothing about the JD changed and nothing about the portfolio changed. The request was answered by a different provider, or by the same provider on a different day.

A score that moves when nothing moved is not a score. So the fix in this codebase is a boundary rather than a prompt trick: the model is allowed to read a job description and turn it into a typed structure, and it is not allowed to do arithmetic. The service javadoc states the contract directly — extraction failure is an error (503/500), never a guessed score (`backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:40`).

Scope: everything described below is portfolio code, open in the repository behind [the portfolio project](/projects/portfolio-ai-assistant).

## The split: JdExtraction has no score field

The design rule is one line long: the type the model fills in cannot express a score, so the model cannot return one.

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record JdExtraction(
        boolean isJobDescription,
        List<Requirement> requirements,
        List<MatchResult.MatchedProject> matchedProjects
) {
    /** One skill/competency the JD asks for, in the JD's own wording. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requirement(String skill, String importance, String reason) {
    }
}
```

That is the whole record (`backend/src/main/java/com/portfolio/recruiter/JdExtraction.java`). A requirement carries a skill, an importance and a reason. There is no `fitScore`, no `confidence`, no `rating`.

The same absence is enforced on the wire, not only in the Java type. The JSON Schema handed to the provider for structured output carries `additionalProperties:false` at every object level, lists every property as required, and has no score property (`backend/src/main/java/com/portfolio/recruiter/RecruiterPromptBuilder.java:28-64`):

```java
public static final Map<String, Object> MATCH_RESPONSE_SCHEMA = Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
                "isJobDescription", /* ... */,
                "requirements",     /* ... items: skill, importance, reason ... */,
                "matchedProjects",  /* ... items: slug, reason, relevantTags ... */),
        "required", List.of("isJobDescription", "requirements", "matchedProjects"));
```

(Inner property definitions elided for length; the file has them in full.)

This is stronger than a prompt instruction. A prompt saying "do not score" is advice the model may follow. A schema with `additionalProperties:false` and no score key is a shape the response has to fit. The same schema constrains `importance` to the enum `must-have` / `nice-to-have`, so the field the arithmetic depends on has two legal values.

The extraction call runs at temperature 0.0 with a 2048-token output cap (`RecruiterMatchService.java:50-52`). That reduces sampling variance, and it is worth saying plainly that the stability of the score does not rest on that setting — the score is not produced by the call at all.

## Normalizing both sides before comparing them

A job description says "Postgres", "React.js", "Spring Boot 3.3". The portfolio says "PostgreSQL", "React", "Spring Boot". String equality fails on all three, and a scorer built on string equality would report gaps that are not gaps.

`SkillNormalizer.normalize` is nine lines and carries the whole idea (`backend/src/main/java/com/portfolio/recruiter/SkillNormalizer.java:51-62`):

```java
static String normalize(String raw) {
    if (raw == null) {
        return "";
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    // "spring boot 3.2" / "java v21" → drop the version-only last word (multi-word names only,
    // so "s3"/"ec2" survive).
    value = value.replaceAll("\\s+v?\\d+(\\.\\d+)*(\\.x)?$", "");
    // Collapse separators/punctuation; keep + and # (c++, c#, f#).
    value = value.replaceAll("[^a-z0-9+#]", "");
    return ALIASES.getOrDefault(value, value);
}
```

Two details in there earn their own sentences. The version-stripping regex requires preceding whitespace, so "Spring Boot 3.3" becomes `springboot` while the single token "S3" survives intact as `s3` — a trailing digit on a one-word name is a product name, not a version. And `+` and `#` are deliberately kept by the character filter, so C, C++ and C# normalize to three distinct values instead of collapsing into one.

The alias table maps post-normalization spellings to canonical ones: `postgres` to `postgresql`, `k8s` to `kubernetes`, `reactjs` to `react`, `js` to `javascript`, `golang` to `go`. Both sides of every comparison go through `normalize` first, so the map only ever needs one direction.

Normalization belongs in code rather than in the prompt for a specific reason: it is a pure function of its input, with `Locale.ROOT` ruling out locale surprises and nothing random anywhere in it. That property is exactly what the score is built on.

## The arithmetic: must-have at 0.7, nice-to-have at 0.3

Three constants define the weighting (`backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:27-29`):

```java
private static final double MUST_WEIGHT = 0.7;
private static final double NICE_WEIGHT = 0.3;
private static final double TAG_ONLY_CREDIT = 0.5;
```

These are values Omkar Jadhav chose for this feature. They are not an industry rubric and they are not tuned against a dataset; they are compile-time constants a reader can see and disagree with.

Every extracted requirement is sorted into one of two buckets by its `importance`, earns credit between 0 and 1, and each bucket's coverage is its earned credit divided by its requirement count. The weighting step then handles the case where one bucket is empty (`MatchScoreCalculator.java:126-136`):

```java
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
```

That empty-bucket branch matters. A demanding JD that lists only must-haves would otherwise be capped at 70 by an accident of its structure rather than by anything about the match. When a bucket is empty, its weight shifts to the other one. The last line produces a single integer between 0 and 100 — no float leaks into the response.

The test suite works a concrete example (`backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:50-63`). Must-haves Java, Spring Boot, PostgreSQL and Kubernetes against a portfolio holding the first three gives 3/4 = 0.75. Nice-to-haves React (full credit) and Docker (half credit, tag only) give 1.5/2 = 0.75. Then 0.7 × 0.75 + 0.3 × 0.75 = 0.75, so the score is 75. Anyone can recompute that by hand, which is the point of keeping the weights visible.

What the number means is narrow and worth stating: it is coverage of one JD's extracted requirements against one portfolio's declared skills and tags. It is not a measure of whether someone can do the job.

## Half credit when the evidence is a tag, not a listed skill

A requirement can match in two places. The first is the portfolio's declared skill list, collected into a `LinkedHashMap` keyed by normalized name with `putIfAbsent`, so the first occurrence wins and iteration order is deterministic. The second is the tag arrays on projects and experience entries, collected into a `LinkedHashSet` (`MatchScoreCalculator.java:66-80`).

A skill-list match earns credit 1.0 and reports the portfolio's canonical name back — a JD asking for "Postgres" is answered with "PostgreSQL". A match found only in tags earns 0.5 (`MatchScoreCalculator.java:165-169`).

One rule keeps the display honest. A tag-only match is credited at half but is still listed as a gap, never as a matched skill (`MatchScoreCalculator.java:106-110`). The skill list shown under the score therefore cannot flatter it: nothing appears as "matched" unless it is a declared skill.

The test pins that behaviour. Two must-have requirements matched only by tags score 50 and produce two gap entries with zero matched skills (`MatchScoreCalculatorTest.java:79-88`).

The reasoning is small and, I think, right. "Docker appears as a tag on a project" is real evidence, and it is weaker evidence than "Docker is a declared skill". Half credit is the arithmetic form of that distinction.

## Absorbing the model's granularity quirks

The prompt tells the model that each skill must be one atomic technology, never a compound or a versioned phrase, and gives explicit split examples — "React with TypeScript" becomes two entries, "Java (17 or 21)" becomes "Java" (`RecruiterPromptBuilder.java:80`). Models in a failover chain do not all obey that identically.

Rather than trusting the instruction, the resolver has a deterministic fallback (`MatchScoreCalculator.java:162-182`):

```java
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
```

`tokenCandidates` produces normalized bigrams first and then unigrams of the raw phrase, so "spring boot" is tried before "spring". Skills are searched before tags, left to right, and the first hit wins.

The worked case from the tests: "Java (17 or 21)", "React with TypeScript" and "Docker experience" resolve to Java (1.0), React (1.0) and a Docker tag (0.5) — 2.5/3, which rounds to 83 (`MatchScoreCalculatorTest.java:113-126`).

Deduplication belongs in the same step. Requirements are deduped on the resolved canonical skill, falling back to the normalized raw name when nothing resolved (`MatchScoreCalculator.java:96-103`). "Java" and "Java (17 or 21)" therefore count as one requirement and cannot inflate the denominator; neither can "Java", "java" and "JAVA".

One last guard sits beside it: importance is accepted only as a case-insensitive `must-have`, and every other value falls through to nice-to-have (`MatchScoreCalculator.java:104`), with the schema's enum constraining the model to those two strings in the first place.

## Dropping what the model made up

The model is asked for project narrative and told to use only slugs that appear in the supplied projects array, never to invent one (`RecruiterPromptBuilder.java:56-57`). The code does not rely on that either.

`validProjects` builds the set of real slugs from the portfolio context and keeps only extraction entries whose slug is in that set; anything else is silently dropped (`MatchScoreCalculator.java:197-213`). The test feeds it one real slug and one invented one and asserts that exactly one matched project comes out (`MatchScoreCalculatorTest.java:150-160`).

The general rule across this response: every field the model produces is either validated against known data or capped. Matched projects, matched skills and gap skills carry hard caps of 5, 12 and 8 entries.

Input that is not a job description short-circuits to a zero score with empty lists, rather than to an error or a guess.

## Proving determinism, and being able to explain any past score

The scorer is pure and stateless, which makes determinism something a test can assert rather than something a comment claims. One test scores the same extraction and context six times and asserts that both the result and the full breakdown are equal every time (`MatchScoreCalculatorTest.java:180-191`).

A second test pins ordering: a strong JD must outscore a partial one, which must outscore a poor one, and a JD sharing nothing with the portfolio scores exactly 0 (`MatchScoreCalculatorTest.java:162-178`).

Determinism is only half the value. The other half is being able to explain a number after the fact. The calculator returns a `Breakdown` alongside the result: must and nice totals, raw credit per bucket, coverage per bucket, the final score, and a per-requirement record of skill, importance, credit and how it matched — `skill`, `skill-token`, `tag`, `tag-token` or `none`.

That breakdown is written to one audit log line per computed score, carrying the JD hash, bucket totals, credits and coverages, with per-requirement detail at DEBUG (`RecruiterMatchService.java:160-168`). Any past score can be reconstructed from it.

The cache deserves a separate, careful sentence. Identical pastes are served from `MatchResultCache`, keyed on a hash of the normalized JD plus a fingerprint of the serialized portfolio content, which skips both the model call and the daily budget guard. That is a cost and latency optimization. It is not what makes the score reproducible: a cold cache produces the same number, because the number comes from the arithmetic either way.

## When to reach for this split

If a feature produces a number that a person will compare, argue with, or act on, keep the model on the side of the boundary that turns unstructured text into a typed structure, and keep every number on the side that is plain code.

The test for whether the split is real: can a reviewer recompute the output by hand from the structured intermediate? Here they can, because the extraction, the weights and the credit rules are all visible.

The cost is honest enough to state. This is more code than asking the model for a score, and the scoring rules become something you own, tune and maintain. That is the trade.

This scoring core sits inside the portfolio application described on [the project page](/projects/portfolio-ai-assistant), alongside the provider failover chain that makes "which provider answered" a question worth defending against in the first place.

## Where this code lives

Omkar Jadhav wrote the classes discussed here — `MatchScoreCalculator`, `SkillNormalizer` and `JdExtraction` — in the `recruiter` package of the portfolio backend, with the tests quoted above beside them. The live feature is at [/recruiter](/recruiter). Since August 2026 he has worked as a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, on backend development for an enterprise reporting product, described on [the experience page](/experience); that work is separate from this code and none of it appears here. More context on the person and the rest of the site is on [the about page](/about).
