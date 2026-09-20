# Content brief: Flyway + Hibernate validate: the schema as source of truth

- **slug:** `flyway-hibernate-validate`
- **primary keyword:** flyway hibernate validate schema mismatch
- **supporting:** hibernate ddl-auto validate, spring boot flyway migration, schema-validation missing column, flyway forward-only migrations, pgvector create extension flyway, jpa entity schema drift, spring boot 3.5 flyway postgresql
- **target length:** 1600 words
- **meta description:** Omkar Jadhav on running Hibernate in validate mode so Flyway owns the schema: what a validate failure looks like, and the pgvector table it skips.

## Outline

### Intro: two lines of YAML decide who owns the schema
- Open by naming the entity and the artefact: "Omkar Jadhav built the backend of this portfolio as a Spring Boot 3.5.15 service on Java 21 and Postgres" — no bare pronoun, and a date (September 2026).
- State the decision in one sentence: Flyway owns the schema, Hibernate is only allowed to check it. `spring.jpa.hibernate.ddl-auto: validate` (application.yml line 27) plus `spring.flyway.enabled: true` (lines 29-30).
- Show the real config excerpt, including the in-repo comment that states the rule: "Schema is owned by Flyway (db/migration). Hibernate only validates entities against it."
- Note the Spring Boot 3.5 packaging detail a reader will hit: Flyway needs both `flyway-core` and `flyway-database-postgresql` on the classpath (pom.xml lines 122-128), the Postgres module is no longer bundled.
- Promise the three things the article delivers: the discipline this forces, the exact failure text, and the one table in this codebase that validate never looks at.

### Where the schema came from: V1 is a baseline, not a design
- The first migration is honest about its origin — V1's header reads "Baseline of the Hibernate-generated schema (now owned by Flyway)" (V1__init.sql lines 1-2).
- Explain the migration path this implies: let Hibernate generate once, freeze that DDL as V1, then flip `ddl-auto` to validate and never let Hibernate write again.
- Fourteen migrations exist today, V1 through V14, in backend/src/main/resources/db/migration.
- Concrete rule for the next change: list the directory and use V<max+1>; do not trust a number written in a doc, because docs go stale.

### What Hibernate's validate actually checks
- Be precise instead of hand-wavy: Spring Boot 3.5.15 pins Hibernate 6.6.53.Final, and `AbstractSchemaValidator#validateTable` does exactly three things per mapped entity — the table exists, every mapped column exists, and each column's type matches.
- Then it checks sequences (missing sequence, inconsistent increment-size). That is the whole surface.
- Give the three message templates verbatim so the reader can grep their own logs: `Schema-validation: missing table [%s]`, `Schema-validation: missing column [%s] in table [%s]`, and `Schema-validation: wrong column type encountered in column [%s] in table [%s]; found [%s (Types#%s)], but expecting [%s (Types#%s)]`.
- The failure is a `SchemaManagementException` thrown while the EntityManagerFactory is being built — so it is a startup failure, not a request-time error. Nothing serves traffic against a schema that does not match.

### What validate does not check (and why that still matters)
- Validate compares the mapping, not the schema. It does not verify nullability, defaults, unique constraints, indexes, or check constraints.
- In-repo proof, no hypotheticals: `DailyCounter.name` is annotated `@Column(name = "name", length = 40)` with no `nullable = false`, while V14 declares the column `varchar(40) NOT NULL` — the suite is green, because validate never looks.
- Second proof: V2 creates `CREATE UNIQUE INDEX profile_singleton ON profile ((TRUE))`, a unique index on a constant expression that enforces at most one profile row. Hibernate has no idea it exists, and `ddl-auto: update` could never have produced it. Constraints like this are exactly what you buy by moving schema ownership to SQL.
- Conclusion to state plainly: validate is a cheap boot-time guarantee that entities and tables agree on names and types. Invariants are the migration's job, and the DB enforces them whether Hibernate knows or not.

### The discipline: a new column is a migration, not a field
- State the rule as it is written in this repo's own implementation notes: every new table or column needs the migration and the matching entity in the same commit, or `mvn test` fails at context boot — and that failure is the check working, not a reason to relax validate.
- Walk one real pair end to end. V14__add_daily_counter.sql creates `daily_counter(name varchar(40) PK, day date NOT NULL, count integer NOT NULL)`; `DailyCounter.java` maps it with `@Table(name = "daily_counter")` and `@Column` names for all three. Show both blocks side by side — they are short enough to print in full.
- Both shipped in one commit (f7636f2), together with the repository, service and tests that use them. That is the unit of change: SQL plus mapping plus test.
- Contrast with `ddl-auto: update`, where adding the Java field is enough — the column appears on the next boot of whichever environment starts first, with no record that it happened and no review of the SQL that ran.

### Forward-only: never edit a migration that has run
- Flyway records a checksum per applied migration in `flyway_schema_history`; editing an applied file makes the next boot fail with a checksum mismatch. The fix is a new migration, never an edit.
- Real example from this repo, two files apart: V3 added `avatar_url VARCHAR(1000)`; V4 dropped that column and added `avatar_data BYTEA` plus `avatar_content_type VARCHAR(50)` instead of rewriting V3.
- Same pattern for the resume: V6 added the binary columns, V10 later added `resume_text` as its own migration.
- Practical note on style: these migrations use `ADD COLUMN IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS`, which makes a partially-applied file safe to re-run during local development.

### Names are matched literally, so pin them
- Validate compares the column name the mapping derives against the column name that exists. Any naming-strategy surprise becomes a boot failure.
- Real case: the entity field is `Profile.currentRole`, but the column is `current_role_json` (V1 line 18), so the entity pins it explicitly — `@Column(name = "current_role_json", columnDefinition = "text")` (Profile.java line 68).
- Same story for JSON-shaped data. `Profile.socials`, `funFacts`, `stash` and `techPicks` are stored as JSON in `text` columns through `AttributeConverter`s in `com.portfolio.persistence`; the converter changes the Java side, not the column type, so `columnDefinition = "text"` has to match what the migration created.
- Takeaway: prefer explicit `@Column(name = ...)` on anything whose column name is not the obvious snake_case of the field.

### Conditional wiring is not conditional schema
- A subtlety worth its own section: the document vault's beans are gated on an env var, but `DriveFile` and `DriveFolder` are plain `@Entity` classes with no condition on them (DriveFile.java lines 22-23, DriveFolder.java lines 23-24).
- Entity scanning is not conditional, so `drive_file` and `drive_folder` are mapped — and therefore validated — on every boot, including the test suite, which runs with no storage endpoint configured.
- Which is fine, because V8 runs unconditionally too. Flyway applies every migration; the app decides which beans exist.
- Generalise in one line: if you gate a feature on configuration, gate the beans, not the tables — a missing table is a startup failure for everyone.

### The pgvector case: a migration that installs an extension
- V9 is the migration that does more than DDL: its first statement is `CREATE EXTENSION IF NOT EXISTS vector;`, then it creates `embedding` with an `embedding vector(768) NOT NULL` column and an HNSW index `USING hnsw (embedding vector_cosine_ops)`.
- Consequence for local setup, stated concretely: the dev database image has to be `pgvector/pgvector:pg16`, not stock `postgres`. A plain image fails on line 5 of V9 and the app never starts — a clean failure, but a confusing one if you do not know where to look.
- The twist that ties the article together: `embedding` has no entity. Nothing in the codebase maps it, so Hibernate's validate never inspects it at all. Access goes through `EmbeddingRepository`, a `JdbcTemplate` repository that binds vectors as the pgvector text literal and casts them with `?::vector`, precisely because JPA has no vector type.
- So this table's contract is held by two things instead: the migration, and the client that pins its output width to 768 to match the column (`GEMINI_EMBED_DIM` defaults to 768). Change one without the other and there is no boot-time check to catch it — the insert fails at runtime.
- Frame this as the honest limit of validate: it covers the mapped subset of your schema. Anything you reach through raw SQL is outside the net, and you pay for that with tests instead.

### What this costs, and why it is still the right default
- Be straight about the trade-off: schema work happens at boot, so the application refuses to start against a database that is wrong, missing, or unreachable. There is no degraded mode.
- The upside is that the failure is loud, early, and local — a mismatched entity fails every `@SpringBootTest` in the suite (they boot the full context against a real Postgres with Flyway), so it is caught before a deploy, not after.
- The downside is real too: boot-time database work means database trouble presents as a crash loop rather than a partial outage. This backend leans into that deliberately rather than papering over it.
- Close by naming the rule in one sentence a reader can take away: if the only way to change the schema is a migration, then the schema in source control is the schema in production.

### Links and next reading
- Link 1: /projects/portfolio-ai-assistant — the project this backend belongs to, where the embedding table and the AI surfaces are described.
- Link 2: /projects — the rest of what Omkar Jadhav has built.
- Link 3: /about — who he is and what the stack is.
- Link 4 (optional): /experience — his role history, for readers arriving from search with the wrong Omkar Jadhav in mind.

## Grounded claims (claim -> evidence)

- The backend runs Hibernate with ddl-auto set to validate.  
  `backend/src/main/resources/application.yml:27`
- The in-repo comment states the ownership rule: "Schema is owned by Flyway (db/migration). Hibernate only validates entities against it."  
  `backend/src/main/resources/application.yml:26`
- Flyway is explicitly enabled in configuration.  
  `backend/src/main/resources/application.yml:29-30`
- The project is Spring Boot 3.5.15 on Java 21.  
  `backend/pom.xml:10 and backend/pom.xml:21`
- Flyway is on the classpath as two artifacts: flyway-core and flyway-database-postgresql.  
  `backend/pom.xml:122-128`
- V1 is a frozen baseline of the previously Hibernate-generated schema, now owned by Flyway.  
  `backend/src/main/resources/db/migration/V1__init.sql:1-2`
- There are fourteen migrations, V1 through V14.  
  `backend/src/main/resources/db/migration`
- Spring Boot 3.5.15 pins Hibernate 6.6.53.Final.  
  `~/.m2/repository/org/springframework/boot/spring-boot-dependencies/3.5.15/spring-boot-dependencies-3.5.15.pom:68`
- Hibernate's table validation checks only three things: the table exists, each mapped column exists, and each column's type matches; it then checks sequences.  
  `hibernate-core-6.6.53.Final-sources.jar org/hibernate/tool/schema/internal/AbstractSchemaValidator.java:128-200 (validateTable / validateColumnType)`
- The exact failure messages are "Schema-validation: missing table [%s]", "Schema-validation: missing column [%s] in table [%s]" and "Schema-validation: wrong column type encountered in column [%s] in table [%s]; found [%s (Types#%s)], but expecting [%s (Types#%s)]", all thrown as SchemaManagementException.  
  `hibernate-core-6.6.53.Final-sources.jar org/hibernate/tool/schema/internal/AbstractSchemaValidator.java:135-180`
- Validate does not check nullability: DailyCounter.name is mapped without nullable=false while the column is declared varchar(40) NOT NULL, and the suite passes.  
  `backend/src/main/java/com/portfolio/common/counter/DailyCounter.java:19-21 vs backend/src/main/resources/db/migration/V14__add_daily_counter.sql:4-8`
- V2 enforces a single profile row with a unique index on a constant expression, which Hibernate never sees and ddl-auto could not generate.  
  `backend/src/main/resources/db/migration/V2__add_profile_singleton_constraint.sql:5`
- The repo's own implementation rule is that a new table or column needs the migration and the matching entity in the same commit, or mvn test fails at context boot.  
  `docs/lead_capture_implementation.md:25-27`
- V14 creates daily_counter(name varchar(40) PK, day date NOT NULL, count integer NOT NULL).  
  `backend/src/main/resources/db/migration/V14__add_daily_counter.sql:4-9`
- DailyCounter maps that table with an explicit @Table(name = "daily_counter") and explicit @Column names for name, day and count.  
  `backend/src/main/java/com/portfolio/common/counter/DailyCounter.java:15-27`
- The V14 migration and the DailyCounter entity shipped in the same commit, alongside their repository, store and tests.  
  `git commit f7636f2 ("fix(security): H1-H3 — Vercel security headers, persisted daily counters (V14), contact daily cap")`
- Editing an already-applied migration produces a flyway_schema_history checksum mismatch; the fix is a new migration.  
  `docs/lab.md:149 and docs/lead_capture_implementation.md:25-27`
- V3 added profile.avatar_url and V4 dropped it and added avatar_data BYTEA plus avatar_content_type VARCHAR(50), instead of editing V3.  
  `backend/src/main/resources/db/migration/V3__add_avatar_url.sql:2 and V4__avatar_binary_storage.sql:2-4`
- The resume columns were added the same forward-only way: binary columns in V6, resume_text later in V10.  
  `backend/src/main/resources/db/migration/V6__add_resume_support.sql:6-8 and V10__add_resume_text.sql:4`
- Migrations in this repo use idempotent DDL (ADD COLUMN IF NOT EXISTS, CREATE TABLE IF NOT EXISTS).  
  `backend/src/main/resources/db/migration/V5__add_tech_picks.sql:1 and V7__add_admin_mfa.sql:12`
- The profile column is named current_role_json and the entity pins it explicitly with @Column(name = "current_role_json", columnDefinition = "text").  
  `backend/src/main/resources/db/migration/V1__init.sql:18 and backend/src/main/java/com/portfolio/profile/Profile.java:68`
- JSON-shaped fields are persisted into text columns through JPA AttributeConverters in com.portfolio.persistence, so the column type stays text.  
  `backend/src/main/java/com/portfolio/persistence/JsonAttributeConverter.java:15-24 and backend/src/main/java/com/portfolio/profile/Profile.java:53-68`
- DriveFile and DriveFolder are unconditional @Entity classes, so their tables are mapped and validated on every boot even when the vault's beans are disabled.  
  `backend/src/main/java/com/portfolio/drive/DriveFile.java:22-23 and backend/src/main/java/com/portfolio/drive/DriveFolder.java:23-24`
- V8 creates drive_folder and drive_file unconditionally, including the self-referencing cascade FK and the folder index.  
  `backend/src/main/resources/db/migration/V8__add_drive.sql:8-43`
- V9's first statement installs the extension: CREATE EXTENSION IF NOT EXISTS vector.  
  `backend/src/main/resources/db/migration/V9__add_pgvector.sql:5`
- The embedding table declares a vector(768) NOT NULL column and an HNSW index using vector_cosine_ops for cosine distance.  
  `backend/src/main/resources/db/migration/V9__add_pgvector.sql:15 and :23`
- The dev Postgres image must be pgvector/pgvector:pg16 because V9 runs CREATE EXTENSION vector against it.  
  `backend/docker-compose.yml:3-5`
- No JPA entity maps the embedding table; the thirteen @Entity classes cover profile, project, commit_entry, skill, skill_branch, skill_diff, admin_mfa, drive_file, drive_folder, contact_message, recruiter_lead, engagement_event and daily_counter.  
  `backend/src/main/java/com/portfolio (grep @Table(name) across entities)`
- EmbeddingRepository uses JdbcTemplate with raw SQL because JPA/Hibernate does not understand pgvector's vector type; embeddings are bound as the text literal [v1,v2,...] and cast with ?::vector.  
  `backend/src/main/java/com/portfolio/rag/EmbeddingRepository.java:9-12 and :31-39`
- Nearest-neighbour lookup uses the <=> cosine-distance operator in raw SQL.  
  `backend/src/main/java/com/portfolio/rag/EmbeddingRepository.java:76-87`
- The embedding client pins its output dimensionality to 768 to match the column width, via GEMINI_EMBED_DIM defaulting to 768.  
  `backend/src/main/java/com/portfolio/rag/GeminiEmbeddingClient.java:19 and :39`
- The backend test suite boots the full application context against the real docker-compose Postgres and runs Flyway, so an entity/schema mismatch fails the suite at boot.  
  `CLAUDE.md (Testing notes) and docs/lead_capture_implementation.md:25-27`

## Must not say

- Do not name pgvector, embeddings, vector search or RAG as Omkar Jadhav's skills. They may appear only as factual descriptions of what the portfolio's code does ('the migration creates a vector(768) column'), never in a skills list, a bio sentence, or a phrase like 'my experience with vector databases'.
- Do not mention Next.js, FastAPI or ChromaDB anywhere, in any framing.
- Do not describe any schema, table, column, class or query from the Nonstop IO codebase. The audit-logging and Report Builder work may be referred to only in the three confirmed verbatim phrasings, and any illustrative SQL must be generic and clearly invented for teaching.
- Do not imply this portfolio backend is the enterprise reporting product, or that these migrations run at work. It is his own project.
- Do not invent numbers: no migration timings, no 'caught N bugs', no percentage improvements, no row counts, no team sizes, no incident counts.
- Do not claim Hibernate validate checks nullability, defaults, unique constraints, indexes, foreign keys or check constraints — the source proves it checks only table existence, column existence, column type and sequences.
- Do not claim ddl-auto=update drops columns or tables; it does not. Describe it accurately as silently issuing additive ALTERs that nobody reviewed.
- Do not fabricate a full stack trace. Only the three Hibernate message templates are confirmed verbatim; anything above or below them in the log must be captured from a real run or omitted.
- Do not describe Flyway migrations as reversible or mention rollback scripts — this project is forward-only, with no undo migrations.
- Do not use 'years of experience', 'architected', 'at scale', 'led', 'we' (as a team), or any staff-engineer survey framing. First person singular, one engineer explaining his own code.
- Do not call Omkar Jadhav a student, fresher, intern (as a current role) or job-seeker.
- Do not open a section with 'It', 'This', 'He' or any bare pronoun — every section must name Omkar Jadhav or the concrete subject so a retrieved paragraph stands alone.
- Do not write 'recently', 'currently' or 'these days' without a date.
- No exclamation marks, no emoji, no rhetorical questions as headings, no 'in today's fast-paced world' opener.

## Open questions for the owner

- Where does the blog live? There is no /blog route in frontend/src/router.tsx and no blog entries in the generated sitemap on this branch. Confirm the route shape (/blog/flyway-hibernate-validate?) and that the new page gets added to the sitemap before publishing.
- Confirm the publication date to print in the article and in its JSON-LD, so the piece carries a concrete date rather than 'recently'.
- Capture the verbatim boot failure once, locally: add a throwaway @Column to an entity, boot against the compose Postgres, and paste the real log. The three Hibernate message templates are confirmed from source, but the surrounding Spring Boot wrapper lines (bean name, exception chain) are not, and the article should not reconstruct them from memory.
- Should the article mention the 2026-09-20 Neon free-tier quota outage documented in docs/future_plan.md (Flyway failing with SQLSTATE 53000 at boot, prod crash-looping, quota resets 2026-10-01)? It is a real, dated, in-repo fact and it illustrates the boot-time trade-off well, but it says 'prod is down' and may be stale or unwanted by publication.
- Unverified: whether profile.current_role_json carries the _json suffix to avoid the reserved word CURRENT_ROLE. Docker was not reachable to check pg_get_keywords(). Leave the reason out unless Omkar confirms it, and state only the fact that the entity pins the column name.
- Is the repository public? The draft cites commit f7636f2 as evidence that the V14 migration and the DailyCounter entity shipped together. If the repo is private, cite the change without the hash.
- Confirm whether to link /experience as the fourth internal link, or keep the article to three links (/projects/portfolio-ai-assistant, /projects, /about).
