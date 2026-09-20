# Content brief: End-to-end user audit logging in NestJS

- **slug:** `nestjs-user-audit-logging`
- **primary keyword:** nestjs user audit log implementation
- **supporting:** nestjs audit log interceptor, audit log table schema postgres, append-only audit log, what to record in an audit trail, nestjs interceptor example typescript, redact sensitive fields from audit logs, audit log query index actor target, audit trail compliance traceability
- **target length:** 1400 words
- **meta description:** Omkar Jadhav, an SDE-I at Nonstop IO, explains the NestJS user audit log pattern: an interceptor, what to record, redaction, append-only tables, and queries.

## Outline

### Lede (above the first H2) — ~120 words, no heading
- Open by naming the entity, not a pronoun: "Omkar Jadhav implemented end-to-end user audit logging at Nonstop IO Technologies in Pune, tracking and logging user actions across an enterprise reporting product for compliance and traceability." That is the ONLY sentence in the article about his employer's work. Everything after it is the pattern, taught generically.
- Immediately after that sentence, state the scope boundary in plain text so no reader can mistake an example for employer code: "None of the code in this article is that system. The table, the decorator and the action names below are invented for this article and use a made-up order-management service as the example domain." This line is mandatory and must survive editing.
- Link out in this paragraph: anchor "his backend work at Nonstop IO Technologies" -> /experience.
- State what the reader gets, in one sentence with the primary keyword: where a NestJS user audit log implementation captures events, what each row records, how to keep secrets out, why the table must be append-only, and the query shape that keeps it readable.
- Set the example domain once and reuse it everywhere: a fictional SaaS order service with an `orders` resource and an `audit_log` table. Never introduce a second invented domain mid-article.
- H1 on the page is "End-to-end user audit logging in NestJS"; the <title> tag is that plus " — Omkar Jadhav" (54 chars, per the site's /blog/{slug} title formula). Exactly one H1.

### An audit log is not an application log
- Define it in one self-contained sentence: an audit log answers who did what to which record, when, and what changed — it is a business record, not a debugging aid.
- Contrast concretely: application logs are for engineers, are sampled, rotate, expire, and are often unstructured; audit rows are evidence, must be complete, and must outlive the data they describe.
- The consequence that follows: audit rows belong in the database next to the data, in a real table with real constraints, not in stdout, Loki, or an APM vendor's retention window.
- The test that settles it: if the system cannot answer "who deleted order 8123 on 14 March 2026, and what did that row look like beforehand", it has logs, not an audit trail.
- Name the third thing it is not: an audit log is not an event-sourcing stream. It records what a user did, not every state transition the system made.

### The fields an audit row needs
- Give the field list with a one-line reason each: actor_id + actor_type (a human, a service account, or the system); action (a stable enum string such as `order.cancelled`); target_type + target_id; occurred_at (timestamptz, set from the server clock, never the client); request_id (the join key back to application logs); changes (JSONB diff); source_ip and user_agent; outcome (succeeded / failed).
- Why `action` must be a stable enum string and not free text: it is the column every query groups by, and free text means the same event is spelled four ways within a year.
- Why target is a (type, id) pair and not a foreign key: audit rows must survive the deletion of the row they describe, so a real FK with a cascade is exactly the wrong constraint here.
- Why occurred_at is server-side and timestamptz: a client clock is an input the actor controls, and audit evidence cannot depend on it.
- Code block 1 — a generic Postgres DDL for `audit_log` with those columns, a `bigserial` primary key, `jsonb` for changes, and NOT NULL on actor, action, target and occurred_at. Label it in the surrounding prose as an example schema written for this article.
- Mention explicitly: this DDL is Postgres; the pattern is not Postgres-specific, but the append-only enforcement later in the article is written in Postgres grants.

### Where to capture the event: interceptor, service call, or database trigger
- Present three real options and their honest trade-offs rather than declaring one winner up front.
- NestJS interceptor: one place, runs inside the request, already knows the authenticated actor, the request id and the outcome — but it sits outside the service and cannot see what a row looked like before the write.
- Explicit emit inside the service method: knows the before and after state precisely — but every new write path added later is another chance to forget the call.
- Database trigger: cannot be bypassed, catches writes from migrations and consoles too — but it has no idea who the HTTP actor was unless the app pushes it into the session first (e.g. `SET LOCAL app.actor_id`).
- The recommendation, stated plainly as a choice with a reason: use the interceptor for the envelope (actor, action, request id, source, outcome, timing) and an explicit emit inside the service for the diff. The interceptor guarantees a row exists; the service supplies what changed.
- One line on the failure this avoids: a trigger-only design produces rows nobody can attribute, and an interceptor-only design produces rows that say something changed without saying what.

### A NestJS audit interceptor, start to finish
- Code block 2 — an `@Audit('order.cancelled')` decorator built on `SetMetadata`. Explain why auditing is opt-in per route: a blanket interceptor over every endpoint produces a table full of GETs that nobody will ever read, which is the first step toward the write-only graveyard.
- Code block 3 — `AuditInterceptor implements NestInterceptor`: read the metadata with `Reflector`, pull the actor from `request.user`, get the request id, then use rxjs `tap` for the success path and `catchError` to record `outcome: 'failed'` before rethrowing. The handler's exception must still propagate unchanged.
- State the ordering rule as a rule: the interceptor must run after the auth guard, or `request.user` is undefined and every row is attributed to nobody.
- Request id: generate one in middleware when the inbound `x-request-id` header is absent, and carry it with `AsyncLocalStorage` so the service layer can attach the diff to the same row without threading a parameter through every call.
- Say what the interceptor deliberately does not do: it does not read the request body wholesale. What gets recorded is decided per entity in the next two sections.
- Keep the code ORM-agnostic — write against a small `AuditWriter` interface, and say in one sentence that the pattern works the same behind TypeORM, Prisma, or hand-written SQL. Do not turn the article into an ORM tutorial.

### Recording what changed without recording everything
- Store a diff, not two full row snapshots: for each changed key, the old and new value, plus the target's primary key. A full before/after snapshot doubles the storage and preserves fields you never intended to keep.
- Code block 4 — a small `diff(before, after)` helper returning `{ field: { from, to } }`, skipping unchanged keys, and a size guard that truncates an oversized value to a marker string rather than writing a multi-megabyte JSONB row.
- Explain the ordering: capture `before` inside the same transaction as the write, not by re-reading afterwards, or a concurrent update makes the diff a lie.
- Name the fields that should never be diffed even though they change on every write: `updated_at`, version counters, and derived/computed columns. They add rows of noise and hide the one field that mattered.
- One sentence on volume as a design input rather than a metric claim: the diff is what keeps an audit table queryable years later, so decide its shape before the table is large, not after. No numbers.

### Never log secrets: allowlist, not denylist
- State the rule first: allowlist the fields that may be audited, per entity. A denylist of `password`, `token`, `ssn` fails silently the first time a teammate adds `apiKeyBackup`, and nobody finds out until an export lands on an auditor's desk.
- Code block 5 — a per-entity `AUDITABLE_FIELDS` map plus a `pick()` function applied to both sides of the diff, so a value that is not on the list cannot physically reach the row.
- List the four places secrets actually leak into audit tables: request bodies recorded wholesale, `Authorization` headers copied with the rest of the headers, query strings carrying tokens, and error messages that echo the payload back in the failure path.
- The password rule, stated as a rule: a password change records the action `user.password_changed` with no before and no after — not the old hash, not the new hash, not a length, not a masked string.
- Why the standard is higher here than for application logs: audit tables are exported to people outside the engineering team, and the table inherits the strictest handling requirement of anything inside it.
- Add the defensive test, since this is the part that rots: a unit test asserting that a payload containing a non-allowlisted key produces a row without it. One test, named after the rule.

### Append-only is what makes the table evidence
- Open with the argument: an audit row that the application can update or delete proves nothing, because the same code path that did the thing could also erase the record of it.
- Enforce it in the database, not in code. Code block 6 — a Postgres grant block: the application role gets `INSERT` and `SELECT` on `audit_log` and nothing else; `REVOKE UPDATE, DELETE` is explicit; the sequence gets `USAGE`.
- Keep the entity out of the ORM's normal write path: no update timestamp column, no soft-delete flag, no cascading delete from the audited table, and no repository method that takes an id and a patch.
- Corrections are new rows. If a row was written wrong, append an annotation row that references the original id — never edit the original. Say this plainly; it is the part people get wrong.
- Retention: deletion happens on a written policy, by a scheduled job running as a separate privileged role, and the deletion is itself recorded as an audit action. A retention job with the application's own credentials defeats the grant above.
- No compliance-certification claims anywhere in this section. Say what the property gives you — a record the application cannot rewrite — and stop there.

### The query shape that keeps it from becoming a write-only graveyard
- Name the three questions an audit table must answer quickly, because they determine the indexes: everything one actor did in a time window; everything that happened to one record, in order; every occurrence of one action type in a window.
- Code block 7 — the three SQL queries, one per question, each with an explicit time bound. State the rule that every audit query carries a time bound; an unbounded audit query is a table scan waiting to happen.
- The matching indexes, one per query: `(actor_id, occurred_at DESC)`, `(target_type, target_id, occurred_at DESC)`, `(action, occurred_at DESC)`. Say which query each one serves, and note that a BRIN index on `occurred_at` is the cheaper option once the table is very large and append-ordered.
- Paginate by keyset on `(occurred_at, id)`, not `OFFSET` — an append-only table grows monotonically and deep offsets get slower every week.
- Mention monthly partitioning as the next step when the table outgrows a single partition, explicitly as a later decision and not a day-one requirement.
- The admin-facing read API: filter by actor, target, action and date range; never expose an unfiltered list endpoint. And note the recursive detail people forget — reading the audit log is itself an auditable action.

### Where the audit write can fail, and what that costs
- Frame it as the one decision worth making deliberately and writing down, because both answers are defensible and the wrong one is only discovered during an investigation.
- Same transaction as the business write: the data and its audit row can never disagree, but a failure in the audit write fails the user's request.
- Async through a queue: the request stays fast, but a dropped or unacked message is a missing audit row, and a missing row is indistinguishable from an action that never happened.
- The rule to follow: if the trail exists for compliance and traceability, it goes in the same transaction as the write it describes. Use a transactional outbox when the same event also has to reach somewhere else.
- One line on what never to do: swallowing the audit write error in a try/catch so the request succeeds. That converts a loud failure into a silent gap in the record.
- No incident anecdote here. State the trade-off; do not invent a story about discovering it.

### What this pattern is worth
- Close short and plain, naming the entity once more: Omkar Jadhav's summary of the pattern, not a pitch.
- The honest takeaway: the value of an audit log is concentrated in the unglamorous parts — the field allowlist, the REVOKE, the three indexes, and the decision about which transaction the write belongs to. The interceptor is the easy half.
- Restate the boundary once, briefly: this is the pattern, written with invented examples; the production implementation it came from is not described here.
- Internal links in this section: anchor "the Spring Boot and React portfolio he builds outside work" -> /projects/portfolio-ai-assistant, and anchor "more about Omkar Jadhav and what he works on" -> /about. Optional fourth link: "his other projects" -> /projects.
- No call to action, no newsletter line, no "thanks for reading".
- Page-level requirements for the writer, not body copy: Article schema with author -> the site's #person node, datePublished and a visible dateline; if the piece is cross-posted to dev.to or Hashnode, rel=canonical on the cross-post points back to this URL.

## Must not say

- Any table name, column name, class name, module name, service name, schema detail or architecture element from the Nonstop IO Technologies codebase. `audit_log`, `@Audit()`, `AuditInterceptor`, `AUDITABLE_FIELDS`, `order.cancelled` and the order-service domain are invented for this article and the article must say so once, near the top.
- Any number about the employer's system: row counts, table sizes, request volumes, latency figures, query timings, percentage improvements, team size, sprint counts, or dates other than his confirmed employment dates.
- Any client, customer, product or industry name, or a guess at one. The employer's work is describable only as "an enterprise reporting product".
- Claiming he designed the compliance policy, chose the retention period, worked with auditors, ran a compliance audit, or owned the audit subsystem. The confirmed record says he implemented the functionality.
- Seniority inflation: "years of experience", "architected", "led", "owned", "at scale", "we scaled", "in my experience running", "battle-tested across teams". He is an SDE-I roughly 7 months into his first full-time role.
- "We" for his own work — it implies a team he is not confirmed to have led. Use "I" for his work and "you" for the reader.
- Next.js, FastAPI, ChromaDB, vector databases or RAG pipelines anywhere on this page as a skill, bio line, author-box item, or tag. They are withdrawn from his resume.
- Calling him a student, fresher, intern (as a current role), graduate-in-waiting, aspiring developer, or job-seeker. He graduated in 2026 and is employed.
- Stating or implying that this pattern makes a system GDPR, SOC 2, HIPAA or ISO compliant, or offering legal/audit-certification guidance. The article teaches a pattern.
- An invented incident or war story: "we once had to trace a deletion", "during an investigation last quarter", "a customer asked us to prove". No anecdotes that did not happen.
- Opening any section, paragraph or heading with a bare pronoun ("He built...", "It records..." as the first words of a section). Each section's first sentence must name its subject.
- Marketing register: "in today's fast-paced world", "robust and scalable", "game-changer", "let's dive in", "seamlessly", exclamation marks, emoji.
- Code that does not compile or that uses an API incorrectly — a wrong rxjs operator, an interceptor placed before the guard, or a `SetMetadata` decorator with the wrong signature. Every block must read as runnable TypeScript or valid Postgres.
- Presenting one ORM as required. If TypeORM or Prisma appears at all, the article must say the pattern is ORM-agnostic and the examples are written against a small interface.
- Padding to reach a word count. The site's rulebook sets 800 words as a floor for a blog post and explicitly forbids inflating past what the content supports.

## Open questions for the owner

- The `/blog` route does not exist yet in the frontend (noted as pending in docs/future_plan.md). The route shell plus `/blog/{slug}` prerendering, sitemap entry and Article schema must ship before this piece can be published. Confirm that is in scope before drafting.
- Confirm Omkar is comfortable publicly stating in one sentence that he implemented end-to-end user audit logging at Nonstop IO. The same claim already appears on /about and in the confirmed work bullets, so this is a confirmation, not a new disclosure — but it is his employer's work and his call.
- Which NestJS major version the code examples target (10 or 11). The interceptor and `SetMetadata` APIs are stable across both, but the article should pin one and say so in a single line so the code is verifiable.
- Confirm Postgres as the example database for the DDL, grants and index blocks. The append-only section is written in Postgres `GRANT`/`REVOKE`; a vendor-neutral version would lose the most concrete part of the article.
- The publication date for the visible dateline and the Article schema `datePublished`.
- The exact author-bio wording for the article footer. It must repeat the site's canonical disambiguating statement verbatim rather than paraphrasing it, so the phrasing matches /about, llms.txt and the schema description.
- Whether a dev.to or Hashnode cross-post is planned. If yes, the cross-post needs rel=canonical back to this URL.
- Confirm the `/projects/portfolio-ai-assistant` slug is live in the projects database, since the closing internal link targets it. The link makes no technical claim about that project — only that it is his — so nothing else about it needs verifying.
- Whether the page should carry an explicit "this is a pattern write-up, not legal or compliance advice" line. The brief currently avoids compliance claims entirely rather than adding a disclaimer; confirm that is the preferred handling.
- Whether the article should include a short "further reading" block linking the other planned posts in the content cluster (SQL for a reporting module, Flyway + Hibernate validate). If those are not written yet, omit rather than link to nothing.
