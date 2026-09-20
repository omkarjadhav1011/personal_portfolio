# Content brief: Keeping the score deterministic when an LLM is in the loop

- **slug:** `deterministic-scoring-beside-an-llm`
- **primary keyword:** deterministic scoring llm output
- **supporting:** llm structured output json schema, deterministic fit score calculation, llm extraction versus scoring, reproducible llm feature design java, weighted must-have nice-to-have scoring, spring boot llm structured extraction, skill name normalization java
- **target length:** 1600 words
- **meta description:** Omkar Jadhav on splitting a feature so an LLM only extracts structure and plain Java arithmetic computes the score - same input, byte-identical result.

## Outline

### A score that moves when nothing moved
- Open by naming the entity and the concrete artefact: Omkar Jadhav built recruiter mode on his portfolio at /recruiter - paste a job description, get a fit score out of 100 plus matched skills, gaps, and relevant projects.
- State the failure mode plainly: if the model produces the number, the same job description can return 78 one afternoon and 71 the next, because the request may have been answered by a different provider in a failover chain, or by the same provider on a different day.
- Frame the fix as a boundary, not a prompt trick: the model is allowed to read and structure; it is not allowed to do arithmetic. Reference the contract written into the service javadoc - extraction failure is an error, never a guessed score (RecruiterMatchService.java:40).
- Set the article's scope explicitly in one sentence: this is portfolio code, open in the repository, not work from an employer codebase. No elaboration.
- Do NOT open with a pronoun. The first sentence contains 'Omkar Jadhav'.

### The split: JdExtraction has no score field
- State the design rule in one line: the type the model fills in cannot express a score, so the model cannot return one.
- Show the JdExtraction record verbatim - isJobDescription, requirements (skill, importance, reason), matchedProjects - and point out what is absent (JdExtraction.java:14-22).
- Explain that the same absence is enforced on the wire: the JSON Schema handed to the provider carries additionalProperties:false, lists every property as required, and has no score property (RecruiterPromptBuilder.java:28-62).
- Make the point that this is stronger than a prompt instruction. A prompt saying 'do not score' is advice; a schema with additionalProperties:false and no score key is a shape the response has to fit.
- Note the temperature choice honestly: the extraction call runs at temperature 0.0 (RecruiterMatchService.java:52), which reduces sampling variance - but say plainly that the score's stability does not rest on that setting, because the score is not produced by the call at all.

### Normalizing both sides before comparing them
- The comparison problem: a job description says 'Postgres', 'React.js', 'Spring Boot 3.3'; the portfolio says 'PostgreSQL', 'React', 'Spring Boot'. String equality fails on all three.
- Walk through SkillNormalizer.normalize step by step (SkillNormalizer.java:51-62): trim, lowercase under Locale.ROOT, drop a trailing version token on multi-word names only, strip every character outside [a-z0-9+#], then resolve an alias map.
- Include the normalize method as a code block - it is nine lines and carries the whole idea.
- Two details worth their own sentences: the version regex requires preceding whitespace, so 'Spring Boot 3.3' becomes springboot while the single token 'S3' survives intact; and + and # are deliberately kept so C, C++ and C# stay three distinct skills.
- Show the alias table shape with a few real entries - postgres to postgresql, k8s to kubernetes, reactjs to react - and note that both sides of every comparison go through normalize, so the map only needs one direction.
- Close on why this belongs in code and not in the prompt: it is a pure function with no locale surprise and no randomness, which is the property the score depends on.

### The arithmetic: must-have at 0.7, nice-to-have at 0.3
- Give the three constants first, as a code block: MUST_WEIGHT = 0.7, NICE_WEIGHT = 0.3, TAG_ONLY_CREDIT = 0.5 (MatchScoreCalculator.java:27-29).
- Describe the two-bucket computation: every extracted requirement is sorted into must-have or nice-to-have, each earns credit between 0 and 1, and each bucket's coverage is earned credit divided by requirement count.
- Show the weighting block verbatim, including the empty-bucket branch: a JD listing only must-haves is scored on pure must coverage rather than 70% of it, so a demanding JD is not capped at 70 by an accident of structure (MatchScoreCalculator.java:126-135).
- Final step: Math.clamp(Math.round(100 * weighted), 0, 100) - one integer, no float leaking into the response.
- Work the concrete example from the test suite: musts Java, Spring Boot, PostgreSQL, Kubernetes against a portfolio holding the first three gives 3/4 = 0.75; nices React (1.0) and Docker (0.5, tag only) give 0.75; 0.7*0.75 + 0.3*0.75 = 0.75, so the score is 75.
- One sentence on why the weights are visible constants rather than tuned parameters: the number is defensible because a reader can recompute it by hand.

### Half credit when the evidence is a tag, not a listed skill
- Define the two places a requirement can match: the portfolio's declared skill list, and the tag arrays on projects and experience entries (MatchScoreCalculator.java:68-80).
- A skill-list match earns 1.0 and reports the portfolio's canonical name back; a tag-only match earns 0.5 (MatchScoreCalculator.java:165-169).
- The rule that keeps the display honest: a tag-only match is credited at half but still listed as a gap, never as a matched skill, so the skill list shown under the score can never flatter it (MatchScoreCalculator.java:106-110).
- Prove it with the test: two requirements matched only by tags score 50 and produce two gap entries with zero matched skills.
- Explain the reasoning in one paragraph: 'Docker appears as a tag on a project' is real evidence and weaker evidence than 'Docker is a declared skill'. Half credit is the arithmetic form of that distinction.

### Absorbing the model's granularity quirks
- Acknowledge the honest limitation: the prompt tells the model each skill must be one atomic technology and gives explicit split examples (RecruiterPromptBuilder.java:80), and models in a failover chain do not all obey identically.
- Rather than trusting the instruction, the resolver has a deterministic fallback: exact normalized match against skills, then tags, then normalized bigrams and unigrams of the raw phrase - bigrams first, so 'spring boot' is tried before 'spring' - skills before tags, left to right, first hit wins (MatchScoreCalculator.java:162-195).
- Include the lookup method as a code block; it is short and it is the mechanism.
- Worked case from the tests: 'Java (17 or 21)', 'React with TypeScript' and 'Docker experience' resolve to Java (1.0), React (1.0) and a Docker tag (0.5) for 2.5/3, which rounds to 83.
- Deduplication belongs here too: requirements are deduped on the resolved canonical skill, falling back to the normalized raw name, so 'Java' and 'Java (17 or 21)' count as one requirement and cannot inflate the denominator.
- One more guard: importance is accepted only as a case-insensitive 'must-have' and everything else falls to nice-to-have (MatchScoreCalculator.java:104), with the schema's enum constraining the model to those two values in the first place.

### Dropping what the model made up
- The model is asked for project narrative and told never to invent a slug (RecruiterPromptBuilder.java:57), but the code does not rely on that either.
- validProjects builds the set of real slugs from the portfolio context and keeps only extraction entries whose slug is in it; anything else is silently dropped (MatchScoreCalculator.java:197-213).
- Test evidence: an extraction containing one real slug and one invented slug yields exactly one matched project.
- Generalize in one line: every field the model produces is either validated against known data or capped - matched projects, matched skills and gap skills all carry hard response caps (5 / 12 / 8).
- Non-JD input short-circuits to a zero score with empty lists rather than to an error or a guess.

### Proving determinism, and being able to explain any past score
- The scorer is pure and stateless, so determinism is testable: one test scores the same extraction and context six times and asserts the result and the full breakdown are equal every time.
- A second test pins ordering: a strong JD must outscore a partial one, which must outscore a poor one, and a JD with nothing in common scores exactly 0.
- Determinism is only half the value; the other half is explicability. The calculator returns a Breakdown alongside the result: must and nice totals, raw credit per bucket, coverage per bucket, and a per-requirement record of skill, importance, credit and how it matched - skill, skill-token, tag, tag-token or none.
- That breakdown is written to one audit log line per computed score, with the JD hash, bucket totals, credits and coverages, so any past score can be reconstructed from the log (RecruiterMatchService.java:161-168).
- Mention the cache correctly and separately: identical pastes are served from a cache keyed on the normalized JD hash plus a portfolio-content fingerprint, skipping the model call and the daily budget. Say explicitly that the cache is a cost and latency optimization - it is not what makes the score reproducible, and a cold cache produces the same number.

### When to reach for this split
- State the general shape in two sentences: if a feature produces a number a person will compare, argue with, or act on, keep the model on the side of the boundary that turns unstructured text into a typed structure, and keep every number on the side that is plain code.
- The test for whether the split is real: can a reviewer recompute the output by hand from the structured intermediate. Here they can - the extraction, the weights and the credit rules are all visible.
- Note the cost honestly: it is more code than asking the model for a score, and the scoring rules become something you own and maintain. That is the trade being made.
- One short sentence connecting to the wider project without inflating: this scoring core sits inside the portfolio application described on the project page, alongside the provider failover chain that makes the 'which provider answered' question worth defending against.
- Close by naming the entity again and pointing at the code: the classes discussed are MatchScoreCalculator, SkillNormalizer and JdExtraction in the recruiter package of the portfolio backend.
- Internal links to place naturally across the article: /projects/portfolio-ai-assistant (the application this code is part of), /recruiter (the live feature), /experience (Omkar Jadhav's role at Nonstop IO, one factual sentence), /about.

## Grounded claims (claim -> evidence)

- The fit score is computed by arithmetic in MatchScoreCalculator, not returned by the model; the class javadoc states the same extraction and context always produce a byte-identical result regardless of which LLM provider did the extraction.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:13-23`
- Must-have requirements are weighted 0.7, nice-to-have 0.3, and a tag-only match earns 0.5 credit; all three are compile-time constants.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:27-29`
- Each bucket's coverage is earned credit divided by requirement count, computed separately for must-have and nice-to-have.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:122-123`
- If one bucket is empty its weight shifts to the other, so a JD with only must-haves is scored on must coverage alone rather than capped at 70.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:126-135`
- The final score is Math.clamp(Math.round(100 * weighted), 0, 100) - an integer between 0 and 100.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:136`
- A requirement matched against the portfolio's declared skill list earns credit 1.0 and resolves to the portfolio's canonical skill name; a requirement found only in project or experience tags earns 0.5.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:162-182`
- A tag-only match is credited at half but is still reported as a gap skill and never as a matched skill, so the displayed lists cannot flatter the score.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:106-110`
- Skill names are looked up in a LinkedHashMap keyed by normalized name, built with putIfAbsent over the context's skill branches, so the first occurrence wins and iteration order is deterministic.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:66-73`
- Tag keys are collected from both project tags and experience tags into a LinkedHashSet.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:74-80`
- Requirements are deduplicated on the resolved canonical skill, falling back to the normalized raw name, so 'Java' and 'Java (17 or 21)' count as one requirement.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:96-103`
- Importance is accepted only as a case-insensitive 'must-have'; every other value falls through to nice-to-have.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:104`
- When the extraction is not a job description, or has no requirements, the scorer returns fit score 0 with empty lists and an empty breakdown.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:59-64`
- When an exact normalized match fails, the resolver falls back to normalized bigrams then unigrams of the raw phrase, trying the skill list before tags, left to right, first hit wins.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:154-195`
- Matched projects returned by the model are filtered against the set of real portfolio slugs; entries with an unknown slug are dropped.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:197-213`
- Response lists are hard-capped at 5 matched projects, 12 matched skills and 8 gap skills.  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:33-36,140-144`
- The scorer returns a Breakdown audit record with must/nice totals, per-bucket credit and coverage, the final score, and a per-requirement RequirementCredit of skill, importance, credit and matchedVia (skill, skill-token, tag, tag-token or none).  
  `backend/src/main/java/com/portfolio/recruiter/MatchScoreCalculator.java:38-57`
- SkillNormalizer.normalize lowercases under Locale.ROOT, drops a trailing version token, strips every character outside [a-z0-9+#], then resolves an alias map - a pure function with no randomness.  
  `backend/src/main/java/com/portfolio/recruiter/SkillNormalizer.java:46-62`
- The version-stripping regex requires preceding whitespace, so 'Spring Boot 3.3' normalizes to springboot while the single token 'S3' is left untouched.  
  `backend/src/main/java/com/portfolio/recruiter/SkillNormalizer.java:56-58; backend/src/test/java/com/portfolio/recruiter/SkillNormalizerTest.java:24-32`
- The character filter deliberately keeps + and # so C, C++ and C# normalize to three distinct values.  
  `backend/src/main/java/com/portfolio/recruiter/SkillNormalizer.java:59-60; backend/src/test/java/com/portfolio/recruiter/SkillNormalizerTest.java:34-39`
- The alias table maps post-normalization spellings to canonical forms, including postgres to postgresql, js to javascript, k8s to kubernetes, reactjs to react and nodejs to node.  
  `backend/src/main/java/com/portfolio/recruiter/SkillNormalizer.java:16-41`
- normalize returns an empty string for null and for blank input.  
  `backend/src/test/java/com/portfolio/recruiter/SkillNormalizerTest.java:48-52`
- JdExtraction - the record the model fills in - contains isJobDescription, requirements and matchedProjects, and deliberately contains no score field.  
  `backend/src/main/java/com/portfolio/recruiter/JdExtraction.java:7-22`
- A Requirement carries only skill, importance and reason.  
  `backend/src/main/java/com/portfolio/recruiter/JdExtraction.java:19-22`
- The structured-output JSON Schema sent to the provider has additionalProperties:false at every object level, lists every property as required, and contains no score property.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterPromptBuilder.java:17-62`
- The schema constrains importance to the enum must-have / nice-to-have.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterPromptBuilder.java:45-46`
- The prompt instructs the model that each skill must be one atomic technology, never a compound or versioned phrase, with explicit split examples.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterPromptBuilder.java:80`
- The prompt instructs the model to use only slugs present in the supplied projects array and never to invent one.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterPromptBuilder.java:56-57`
- The extraction call runs at temperature 0.0 with a 2048-token output cap, against the structured schema, via LlmRouter.generateStructured.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:50-52,134-135`
- The service's documented stability contract is: identical JD after trim/case/whitespace normalization yields an identical result, and extraction failure is an error (503/500), never a guessed score.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:30-41`
- Results are cached on a key combining the normalized JD and a SHA-256 fingerprint of the serialized portfolio context, so a portfolio edit invalidates the entry; a cache hit skips both the model call and the daily budget guard.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:106-122`
- Cache-key normalization is trim, lowercase under Locale.ROOT and whitespace collapse - applied to the key only, while the prompt receives the original text.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:175-178`
- Every computed score is written to a single audit log line carrying the JD hash, must/nice totals, per-bucket credit and coverage, and the final fit score, with per-requirement detail at DEBUG.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:160-168`
- The match result cache is an in-memory store with a 6-hour TTL and a 200-entry bound that evicts the oldest entries.  
  `backend/src/main/java/com/portfolio/recruiter/MatchResultCache.java:23-26,53-60`
- Worked example: three of four must-haves matched (0.75) and nice-to-haves React 1.0 plus tag-only Docker 0.5 (0.75) produce a score of exactly 75.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:50-63`
- Spelling variants 'spring-boot', 'Postgres' and 'React.js' score 100 and are reported back under the portfolio's canonical names Spring Boot, PostgreSQL and React.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:65-77`
- Two tag-only must-haves score 50 and appear as two gap skills with no matched skills.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:79-88`
- Compound and versioned names resolve through the token fallback: 'Java (17 or 21)', 'React with TypeScript' and 'Docker experience' give 2.5/3, which rounds to 83.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:113-126`
- Duplicate requirements - whether by case ('Java', 'java', 'JAVA') or by version phrasing - collapse to one, keeping the denominator honest.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:128-148`
- An extraction containing one real slug and one invented slug yields exactly one matched project.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:150-160`
- A strong JD scores above a partial one, which scores above a poor one, and a JD sharing nothing with the portfolio scores exactly 0.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:162-178`
- Scoring the same extraction and context six times yields an equal result and an equal breakdown every time.  
  `backend/src/test/java/com/portfolio/recruiter/MatchScoreCalculatorTest.java:180-191`
- The same scoring implementation serves both the web endpoint and the public MCP tool match_against_jd - one implementation, two front doors.  
  `backend/src/main/java/com/portfolio/recruiter/RecruiterMatchService.java:18-21; backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:114`
- The recruiter feature is reachable at the /recruiter route of the portfolio frontend.  
  `frontend/src/router.tsx:52`
- The portfolio application itself is documented at the project slug portfolio-ai-assistant.  
  `frontend/src/data/projects.ts:23`

## Must not say

- Do not claim RAG, vector databases, ChromaDB, FastAPI or Next.js as Omkar Jadhav's skills. This article has no reason to mention any of them - the scoring path does not touch retrieval at all. If pgvector or embeddings come up, cut the sentence rather than qualify it.
- Do not attribute this code to his employer. The recruiter scorer is portfolio code in a public repository. Do not connect it to the enterprise reporting product, and do not invent any Nonstop IO table, schema, class or module name.
- Do not invent numbers: no accuracy percentage, no variance-before-and-after figure, no latency measurement, no count of job descriptions scored, no recruiter or user counts, no token or cost figures. Every number must come from the source files or test assertions in groundedClaims.
- Do not claim that temperature 0 makes an LLM deterministic or guarantees identical output across providers. The code sets 0.0 to reduce sampling variance; the article's argument is that the score does not depend on that setting at all.
- Do not credit the cache with determinism. MatchResultCache is a cost and latency layer with a 6-hour TTL and a 200-entry bound. State explicitly that a cold cache produces the same number.
- Do not describe the provider failover chain in depth or benchmark providers against each other - that is a separate planned article. One clause referencing it is the maximum.
- No seniority inflation: no 'years of experience', 'architected', 'led', 'at scale', 'owned the platform', 'designed the system for the team'. He is an SDE-I writing about something he built.
- Do not call Omkar Jadhav a student, fresher, intern (as a current role) or job-seeker, and do not frame the recruiter feature as him looking for work.
- Do not present the weights 0.7 / 0.3 / 0.5 as an industry standard, a published rubric, or a validated model. They are constants he chose, and the article should say so.
- Do not describe the fit score as an objective measure of a candidate's suitability. It is coverage of one JD's extracted requirements against one portfolio's declared skills and tags.
- Do not write invented code. Every code block must be copied from the listed source files, or be a clearly generic invented-for-teaching snippet labelled as such.
- No marketing register: no 'in today's fast-paced world', no 'game-changer', no 'unlock', no exclamation marks, no emoji, no rhetorical questions as headings.
- Do not open a section or paragraph with a bare pronoun. Sections open with 'Omkar Jadhav' or the concrete subject so a retrieved paragraph stands alone.
- Do not write 'recently' or 'currently' without a date.

## Open questions for the owner

- The /blog/{slug} route does not exist yet - frontend/src/router.tsx has no blog route and there is no content directory. Confirm the blog shell, the URL shape, and the Article JSON-LD (author pointing at the #person node) are in place before this is written, or scope the task to prose only.
- Confirm the publication date to use in dated sentences, and whether the article should carry a visible 'last updated' date.
- Confirm the canonical public GitHub URL and whether the article should link to specific files - and if so, whether to pin a commit SHA so the cited line numbers stay valid.
- Confirm whether /recruiter is live in production and returns a score at publish time - the endpoint returns 503 with no LLM provider key configured, and a linked feature that 503s undercuts the article.
- Confirm whether to mention the MCP front door (match_against_jd) at all, or keep the article scoped to the web endpoint and leave MCP to its own planned article.
- Confirm the exact one-sentence employer line to use if /experience is linked, drawn only from the three approved work facts.
- Confirm whether Omkar Jadhav wants the 0.7 / 0.3 / 0.5 weights described as provisional and open to change, or as settled.
