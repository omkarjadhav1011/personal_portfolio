# Blog drafts — Wave 5, incomplete

**Nothing here is ready to publish.** Read this before doing anything with these files.

## What happened

Wave 5 ran the eight article topics from [`../03-KEYWORD-MAP.md`](../03-KEYWORD-MAP.md)
(Cluster 7) through a pipeline: brief → draft → four adversarial verify lenses → revise.
The run stopped partway through when the Anthropic account hit its spend limit. Of 41
agents, 17 completed and 24 died.

The `revise` stage was the step that wrote files to disk, and every one of those agents
was killed — so the workflow itself wrote nothing. The briefs and five complete drafts
were recovered afterwards from the run journal without re-running anything.

## State of each article

| Article | Brief | Draft | Verify lenses run | Ready? |
|---|---|---|---|---|
| `multi-provider-llm-failover-java` | ✅ | ✅ 2,796w | **0 of 4** | ❌ unverified |
| `deterministic-scoring-beside-an-llm` | ✅ | ✅ 1,720w | 1 of 4 | ❌ unverified |
| `spring-ai-mcp-server` | ✅ | ✅ 1,780w | 3 of 4 | ⚠️ findings recorded, 2 applied |
| `envelope-encryption-spring-boot` | ✅ | ✅ 1,836w | **0 of 4** | ❌ unverified |
| `gemini-api-python-structured-output` | ✅ | ✅ 2,321w | **0 of 4** | ❌ unverified |
| `flyway-hibernate-validate` | ✅ | ❌ | — | brief only |
| `nestjs-user-audit-logging` | ✅ | ❌ | — | brief only |
| `optimized-sql-reporting-queries` | ✅ | ❌ | — | brief only |

Briefs for all eight are in [`_briefs/`](./_briefs) — outline, grounded claims with the
`file:line` that proves each one, traps to avoid, and open questions for the owner.

## What "unverified" means here

The four verify lenses exist because each catches a failure this particular content is
prone to:

- **grounding** — reads the actual source file and flags any claim the code does not
  support. Five of these articles describe real code in this repo; an unverified
  technical claim in a portfolio article is worse than no article.
- **withdrawn-and-inflation** — catches Next.js / FastAPI / ChromaDB reappearing as a
  claimed skill, seniority inflation, and student/seeking language.
- **proprietary-leak** — the highest-risk lens. Three articles touch work done at Nonstop
  IO Technologies, and the entire public record of that work is three sentences. An agent
  writing about audit logging will reach for concrete table and class names, and inventing
  an employer's internals is a serious problem, not a style one.
- **seo-structure** — title and meta length, single H1, keyword placement, and whether a
  paragraph still makes sense when retrieved on its own.

A draft with `verifyLensesRun: []` has had none of that applied. It was written under the
same constraints, but nothing independently checked it.

## Front matter

Each draft carries its real state, not an aspirational one:

```yaml
status: draft-unverified
grounded: true            # is there backing code in this repo?
verifyLensesRun: [...]    # which lenses actually completed
verifyLensesNotRun: [...] # which never ran
unresolvedFindings: [...] # verifier findings NOT applied (revise stage died)
claimsNeedingConfirmation: [...]
```

`unresolvedFindings` is the important one. `spring-ai-mcp-server.md` carries 20 of them.
Two were applied by hand — the article claimed a test file was "about eighty lines" when
`McpToolOutputBoundaryTest.java` is 99, and made an unevidenced claim about the approach
scaling to something larger. The rest are untouched.

## To finish this

1. Re-run the workflow when account budget allows. The script is saved in the session
   directory and supports resume — completed agents replay from cache, so only the
   3 missing drafts, the missing verify lenses and the revise stages would run.
2. Or finish by hand: apply `unresolvedFindings` per file, then read each draft against
   the four lenses above.

Either way **the owner reviews before anything is published** — that was the Wave 5
condition from the start.

## Publishing

There is no `/blog` route yet. It was deferred out of Wave 3 scope, so approved drafts
have nowhere to go until the route, the content pipeline and `Article` schema exist.
That is separate work, and it is not worth building until the drafts are settled.
