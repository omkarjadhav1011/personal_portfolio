# Content brief: Optimizing SQL Queries for a Reporting Module

- **slug:** `optimized-sql-reporting-queries`
- **primary keyword:** optimize sql queries reporting module
- **supporting:** sql query plan explain analyze, composite index column order, sargable where clause, n+1 queries report assembly, keyset pagination sql, materialized view vs rollup table, audit log table performance, group by aggregation index, partition audit log by month
- **target length:** 1500 words
- **meta description:** Omkar Jadhav, an SDE-I at Nonstop IO who writes reporting and audit-log SQL, on query plans, filter-then-aggregate indexes, N+1 assembly and pre-aggregation.

## Outline

### Intro (no heading) — who is writing this and what it covers
- Open by naming the entity, not a pronoun: 'Omkar Jadhav is a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, where he writes SQL for reporting and audit-log flows on an enterprise reporting product.' That is the only sentence about his employer in the body.
- Second paragraph states the scope rule explicitly and in the reader's interest: every schema, query and plan below is invented for this article and belongs to no employer's codebase. This sentence also stops the piece being read as a leak.
- Third paragraph states the thesis: a reporting query is a different animal from a transactional one, and the OLTP habits most backend developers learn first (index the lookup key, fetch one row) actively mislead you once the query filters a date range and aggregates.
- Name the six things the article covers, in order, so a retrieved snippet of the intro is useful alone: workload shape, reading a plan, indexing for filter-then-aggregate, sargability, report assembly and pagination, pre-aggregation, and audit tables that only grow.
- Budget: ~150 words.

### Reporting queries and transactional queries have different shapes
- Transactional query: narrow predicate, one or a few rows, sub-millisecond, run thousands of times a second, predicate known at development time.
- Reporting query: wide date range, scans many rows to return few, GROUP BY and aggregates, ORDER BY plus LIMIT over a sorted result, run tens of times a minute, and the predicate is partly chosen by the user at runtime (which columns, which filters, which grouping).
- Consequence 1: you cannot index every combination a report builder can produce. Index the columns that are always present — the tenant/account scope and the time range — and let the rest filter.
- Consequence 2: the cost model is different. A reporting query that touches 200k rows and returns 40 is not broken; a reporting query that touches 200k rows one at a time from application code is.
- Consequence 3: the data grows monotonically. A query tuned against six months of history can fall off a cliff at three years, which is why the last section is about growth rather than about a single query.
- Budget: ~180 words.

### The example schema (invented for this article)
- State plainly: the following two tables exist only in this article. They are deliberately generic and are not a description of any product the author works on.
- Give the DDL as a real, runnable PostgreSQL block: `invoice(id bigserial primary key, tenant_id bigint not null, customer_id bigint not null, issued_on date not null, status text not null, amount_cents bigint not null)` and `app_event(id bigserial primary key, tenant_id bigint not null, actor_id bigint not null, action text not null, entity_type text not null, entity_id bigint, occurred_at timestamptz not null, payload jsonb)`.
- Say the dialect once: examples are PostgreSQL; the ideas (plan reading, composite index order, sargability, keyset pagination) carry over to SQL Server and MySQL, and flag the one or two places where syntax differs.
- Give the running example in one sentence: monthly invoiced totals per status for one tenant over a date range, plus a paginated audit-log screen over `app_event`. Everything later refers back to these two.
- Internal link 2 lands here: `/projects/portfolio-ai-assistant`, anchor 'the Spring Boot and PostgreSQL API behind this site', as the reason PostgreSQL is the dialect on show. A statement about software he built, never a skills claim.
- Budget: ~150 words.

### Read the plan before you change anything
- The command, verbatim and correct: `EXPLAIN (ANALYZE, BUFFERS) SELECT ...`. ANALYZE actually runs the statement, so wrap a write in a transaction you roll back.
- What to read first, in priority order: (1) which node owns most of the actual time, (2) `rows=` estimated versus `actual rows=`, (3) `Rows Removed by Filter`, (4) `shared hit` versus `shared read`, (5) whether a Sort reports `Sort Method: external merge` and spills to disk.
- The key diagnostic idea: an estimate off by orders of magnitude is the real bug. The planner chose a nested loop because it expected 12 rows and got 120,000; fixing the estimate (ANALYZE the table, raise the statistics target, rewrite the predicate) often fixes the plan with no new index.
- Say what is NOT a diagnosis: 'it says Seq Scan' is not a problem by itself. A sequential scan over a small table, or one where you genuinely need most rows, is the correct plan. `Rows Removed by Filter` on a large scan is the signal that an index would help.
- Run the plan against data shaped like production — similar row counts and similar value distribution. A plan measured on a 200-row development table tells you nothing, and this is the most common way a 'fix' fails after deploy.
- Do not paste a fabricated EXPLAIN output with invented timings. Either describe the fields to read (preferred), or show a plan generated from a `generate_series` fixture the reader can reproduce, with the fixture script included.
- Budget: ~230 words.

### Index for the shape: equality first, range last
- The rule for composite B-tree column order: equality columns first, the range column last, and the sort column matching index order if you want the ORDER BY for free.
- Concrete: for `WHERE tenant_id = $1 AND occurred_at >= $2 AND occurred_at < $3 ORDER BY occurred_at DESC`, the index is `CREATE INDEX ON app_event (tenant_id, occurred_at DESC)`. Explain why `(occurred_at, tenant_id)` is worse: once the leading column is a range, the second column can only filter, not seek.
- Index-only scans: `INCLUDE (action, entity_type)` lets a count-by-action report answer from the index without touching the heap. Caveat stated: index-only scans depend on the visibility map, so they degrade on a constantly written table until it is vacuumed.
- Partial index for a skewed predicate: `CREATE INDEX ON invoice (tenant_id, issued_on) WHERE status = 'open'` stays small and hot when one status is queried far more than the others.
- The cost side, stated honestly: every index is paid for on every insert, and an audit table is append-heavy by definition, so three indexes there is a design decision rather than a free win. Add an index for a query you actually run; drop indexes nothing uses (`pg_stat_user_indexes.idx_scan` is how you find them).
- Budget: ~220 words.

### Make the predicate sargable
- Define the term in one line: a predicate is sargable when the database can use an index to seek to the matching range instead of computing something for every row.
- The highest-leverage rewrite, as a before/after pair. Before: `WHERE date_trunc('month', occurred_at) = date_trunc('month', $1)`. After: `WHERE occurred_at >= $1 AND occurred_at < $1 + interval '1 month'`. Same rows, but only the second can seek on `(tenant_id, occurred_at)`.
- Three more of the same family: a cast on the column side (`WHERE entity_id::text = $1`), a leading-wildcard `LIKE '%term%'` (a B-tree cannot help; that is what trigram or full-text indexes are for), and `WHERE lower(action) = $1` against an index on `action` — either normalize on write or build the expression index `ON app_event (lower(action))`.
- The implicit-coercion trap: comparing a `bigint` column to a string parameter, or a `timestamptz` column to a naive local timestamp, can silently change the plan and, worse for a report, silently change the boundary rows.
- Time zones get their own paragraph because reporting lives on boundaries: decide once whether 'March' means March in UTC or in the tenant's zone, express it as a half-open range `>= start AND < end`, and never use `BETWEEN` on a timestamp — it includes the upper bound and double-counts midnight.
- Budget: ~220 words.

### Assemble the report in one round trip, not in a loop
- Name the failure precisely: the query returns 500 invoice rows, then the renderer loops and fetches the customer name for each one. 501 round trips, each fast, total latency terrible — and it never appears in a slow-query log, because no individual query is slow.
- Where it comes from: lazy-loaded ORM associations, a mapper resolving a display name per row, or a service method reused from an OLTP path where one row at a time was the right shape.
- The fix hierarchy: (1) join in SQL and return the report's rows already assembled; (2) if a join is genuinely wrong, batch the second query with `WHERE id = ANY($1)` and map in memory — one extra query, not N; (3) never accept 'it is fine, they are all indexed'.
- Push the aggregation down. Show a correct `GROUP BY` returning the finished shape: grouping on `date_trunc('month', issued_on)` and `status` with `sum(amount_cents)` and `count(*)` — noting that `date_trunc` in the SELECT and GROUP BY lists is fine; it is only in the WHERE clause that it kills the index.
- Window functions instead of correlated subqueries for running totals and per-group ranking: `sum(amount_cents) OVER (PARTITION BY status ORDER BY month)` replaces a subquery run once per row, and `LATERAL` is the clean way to do top-N-per-group.
- Budget: ~220 words.

### Paginate an audit log with keyset, not OFFSET
- Why OFFSET degrades: `OFFSET 100000 LIMIT 50` still produces and discards 100,000 rows. Page 1 is instant, page 2000 times out, and audit-log screens are exactly where people page deep.
- Keyset (seek) pagination shown correctly, with a tiebreaker so the order is total: `WHERE tenant_id = $1 AND (occurred_at, id) < ($2, $3) ORDER BY occurred_at DESC, id DESC LIMIT 50`, backed by `(tenant_id, occurred_at DESC, id DESC)`. The cursor the UI carries is the last row's `(occurred_at, id)`.
- State the trade-off rather than selling the technique: keyset gives next/previous, not 'jump to page 47'. For an audit log that is the right trade; for a ten-page report it may not be worth the change.
- The other half of the cost is the total count. `COUNT(*)` over the same filter is a second full scan. Options: drop the total, show 'more results' instead of a page count, or take an estimate from `pg_class.reltuples` or `EXPLAIN` when approximate is acceptable — and label it approximate in the UI.
- Budget: ~190 words.

### Pre-aggregation, and what it costs you
- When to reach for it: only after the plan is good, the index matches the predicate, and the query is still too slow — and only for a report that is repeated, bounded and well-defined. A report builder generating arbitrary groupings cannot be pre-aggregated.
- Three mechanisms with the real trade-off beside each. Materialized view: simplest, refreshed on a schedule, `REFRESH MATERIALIZED VIEW CONCURRENTLY` needs a unique index and still costs a full recompute. Rollup table: written incrementally per period, cheap to refresh, but you own the correctness. Write-time summary: fastest to read, but it puts reporting logic on the transactional write path, where a bug corrupts history.
- The cost nobody budgets for: backdated and corrected rows. If a record can be edited after the fact or arrives late, any rollup keyed by event date must be recomputed for that period — so a reprocess path is a day-one requirement, not a follow-up.
- Staleness is a product decision, not an implementation detail. If the numbers are up to an hour old the screen must say so with a timestamp; a silently stale total is worse than a slow accurate one, because someone will reconcile it against a live query and lose a day to it.
- Keep one source of truth: the rollup should be derivable from the base table by a query you can run on demand, and ideally a test asserts the two agree for a sample period.
- Budget: ~220 words.

### Keeping audit-log queries fast as the table grows
- The defining property of an audit table: append-only, never updated, queried by a scope plus a time range, growing forever. Every technique here follows from that.
- Range-partition on the timestamp, monthly. Two payoffs: the planner prunes to the partitions the date range touches, and retention becomes `DROP TABLE` on an old partition instead of a mass `DELETE`. Note that the partition key must appear in the predicate for pruning to happen — another reason the sargable date range matters.
- Why mass DELETE is the trap: it leaves dead tuples, bloats the table and its indexes, and generates a vacuum workload on precisely the table you are trying to keep fast. Detaching a partition is metadata.
- BRIN as the niche win: on a very large table whose rows are physically in timestamp order (which append-only insertion gives you), `USING brin (occurred_at)` is a tiny index that prunes blocks. Say explicitly that it does not replace the composite B-tree on `(tenant_id, occurred_at)`, so nobody swaps one for the other.
- The `payload jsonb` column: keep it out of the filter path. If it must be searchable, a GIN index makes writes and storage noticeably more expensive, so promote the one or two keys people actually filter on into real columns instead.
- Retention is a performance feature. A documented policy — how long audit rows are kept and what happens to them afterwards — is the only intervention whose benefit does not decay.
- Budget: ~230 words.

### A checklist before a reporting query ships
- A short numbered list, one line each, written so the section stands alone if retrieved by itself.
- 1. You have run `EXPLAIN (ANALYZE, BUFFERS)` against data with production-like row counts and distribution.
- 2. Estimated rows and actual rows are within an order of magnitude at every expensive node.
- 3. Every filter is sargable: no function or cast on the column side, half-open date ranges, no `BETWEEN` on a timestamp.
- 4. The composite index is equality-first, range-last, and its order matches the ORDER BY.
- 5. The report is one round trip; no per-row lookup in application code.
- 6. Deep pages use keyset pagination, and any total shown is either cheap or labelled approximate.
- 7. If pre-aggregated: a stated staleness window in the UI, a reprocess path for backdated rows, and a test reconciling the rollup against the base table.
- 8. You know what this query does at ten times the row count.
- Budget: ~140 words.

### Closing: where this comes from
- One short paragraph, entity-named: 'Omkar Jadhav has been a Software Development Engineer I at Nonstop IO Technologies since August 2026, after joining as a Software Developer Intern on 2 February 2026. He contributes to the Report Builder module of an enterprise reporting product and writes SQL for reporting, audit-log and data-retrieval flows.' No metrics, no scale claims, no team framing.
- Repeat the boundary once at the end, in the reader's interest: the schemas and queries in this article were written for it and are not from that product.
- Internal link 1 — `/experience`, anchor 'his work at Nonstop IO Technologies', in this closing paragraph.
- Internal link 3 — `/projects`, anchor 'other things he has built', in the closing.
- Internal link 4 (optional, only if it reads naturally) — `/about`, anchor 'Omkar Jadhav'. Drop it rather than force a fourth link.
- Page-level requirements for whoever implements it, per docs/seo/01-SEO-RULEBOOK.md: `Article` JSON-LD with `author` pointing at the site's existing `#person` node, `og:type: article`, a concrete `datePublished`, exactly one `<h1>`, breadcrumbs, and `rel=canonical` on any cross-post. The `/blog/{slug}` depth floor is 800 words; this brief targets ~1500.
- Budget: ~120 words.

## Must not say

- Any table name, column name, index name, stored-procedure name, module internal or architecture detail from the Nonstop IO product. The three confirmed bullets are the entire permitted surface.
- Any performance number attributed to his work: no 'cut report time from 12s to 400ms', no percentages, no row counts, no p95 figures, no 'saved X hours'. If a number would improve the piece and the reader cannot reproduce it from a fixture in the article, leave it out.
- A fabricated `EXPLAIN ANALYZE` output presented as a real measurement. Either teach the fields to read, or include a reproducible `generate_series` fixture that produces the plan shown.
- Any phrasing that makes the invented schema sound like his employer's: 'our audit table', 'the schema we use', 'in our reporting product'. Use 'this article's example' or 'a generic reporting schema'.
- The name of his employer's client, product, database engine, or reporting tool. The confirmed fact says 'SQL', not a vendor — do not assert or imply which engine the employer runs.
- Next.js, FastAPI, ChromaDB, vector databases, RAG, embeddings, pgvector. None belong in this article at all; the simplest safe rule here is zero mentions.
- Seniority or scale inflation: 'years of experience', 'at scale', 'architected', 'led the optimization effort', 'owned the reporting platform', 'mentored'. He is an SDE-I roughly seven months into his first full-time role.
- Student, fresher, intern as a current role, 'aspiring', 'looking for opportunities', or any job-seeking framing.
- Vague time words: 'recently', 'currently', 'nowadays', 'these days' without a date attached. Use 'since August 2026' or 'as of September 2026'.
- Marketing register: 'in today's data-driven world', 'blazing fast', 'game-changer', 'supercharge', 'unlock', exclamation marks, emoji.
- Absolutes that are wrong: 'never use OFFSET', 'always add an index', 'sequential scans are bad'. Every technique in this article ships with its trade-off stated.
- Opening a section or paragraph with a bare pronoun ('He', 'It', 'This'). Every section must survive being retrieved on its own.
- Unverified SQL. Every query, DDL statement and command in the article must be syntactically valid PostgreSQL that the author has actually run.

## Open questions for the owner

- The site has no `/blog` route. `frontend/src/router.tsx` defines only `/`, `/about`, `/projects`, `/projects/:slug`, `/experience`, `/education`, `/resume`, `/recruiter`, `/mcp`. Confirm the final URL is `/blog/optimized-sql-reporting-queries`; the route, a `/blog` index page and the `sitemap.xml` entry all need adding before publish.
- Dialect: this brief uses PostgreSQL throughout, justified by the portfolio's own Postgres API. Confirm that is acceptable and that the article should stay silent on which engine the employer uses (the confirmed bullet says only 'SQL').
- Confirm the exact wording of the single permitted work sentence. Proposed: 'Omkar Jadhav writes SQL for reporting and audit-log flows on an enterprise reporting product at Nonstop IO Technologies.'
- Employer spelling: docs/seo/03-KEYWORD-MAP.md flags three variants live on the site. Confirm 'Nonstop IO Technologies' is the canonical form used here.
- `datePublished` for the Article schema and the visible byline date — the piece must carry a concrete date rather than 'recently'.
- Will the author run a `generate_series` fixture locally so at least one real `EXPLAIN (ANALYZE, BUFFERS)` plan can be pasted alongside its fixture script? If not, the plan-reading section ships descriptive only. Decide before drafting.
- Are partitioning and BRIN in scope? Both are correct for the audit-table section, but neither appears in his three confirmed bullets, so they must read as 'techniques for this problem', not 'techniques he has deployed'. Confirm that framing.
- Cross-posting: docs/seo/01-SEO-RULEBOOK.md J3 allows dev.to/Hashnode with `rel=canonical` back to the site. Is this article a cross-post candidate?
- The `#person` node id used in the site's existing JSON-LD entity graph, so the Article's `author` reference matches it exactly.
