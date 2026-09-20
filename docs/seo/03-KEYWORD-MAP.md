# 03 — KEYWORD AND ENTITY MAP

Target URLs assume the Section C2 architecture from [`01-SEO-RULEBOOK.md`](./01-SEO-RULEBOOK.md),
built in Wave 3. Clusters are derived from the confirmed Subject Profile only.

---

## Method, and an honest limitation

**There is no keyword-volume data in this document, because I have no tool that produces it.**
No Search Console property exists, no Ahrefs/Semrush/Keyword Planner access is configured, and
this site has zero impressions to learn from. Any monthly-volume number here would be invented,
and an invented number is worse than no number because it gets planned against.

What I *can* ground in evidence is **competition**, which I measured directly by running the
queries (`00-RECON.md` §0.7). So every cluster below is scored on:

- **Competition** — Very Low / Low / Moderate / High / Unwinnable, based on who actually ranks today
- **Volume band** — a qualitative estimate with its reasoning stated, never a fabricated figure
- **Priority** — using the plugin's quick-win matrix, re-based on *entity value* rather than traffic

**Re-basing the priority model.** The `keyword-clustering` and `content-strategy` skills score on
volume × difficulty × business value, with a funnel model (awareness → consideration → decision).
That model doesn't fit. The conversion event here is **one person or one AI assistant correctly
identifying one human being**. A query with 10 searches a month from the right recruiter outranks
one with 10,000 from strangers. Priority below is therefore:

> **entity value** (does ranking for this prove *which* Omkar Jadhav he is?) **× reachability**

### The strategic finding that shapes every cluster

The bare query **"Omkar Jadhav" is not winnable in the near term, and should not be the target.**

Measured, the first-page field for that name includes a Software Engineer at **Google**, a Senior
SWE at **LTIMindtree**, a Senior Backend Developer using **C# and Spring Boot**, an **"AI & LLM
Engineer and Full Stack Developer"** whose portfolio already ranks #1, two developers in
**Kolhapur**, and a frontend developer in **Pune** — plus a professional cyclist, two IMDb film
credits, an astrophysics PhD student, a podcaster, and Wikipedia knowledge panels for three public
figures surnamed Jadhav.

Three consequences:

1. **The reachable targets are qualified variants** — name + employer, name + college, name + role,
   name + project. These are where a real recruiter's search actually lands.
2. **The SERP is fragmented across professions**, which helps. A tech-qualified query narrows the
   field from "everyone named Omkar Jadhav" to "software engineers named Omkar Jadhav", which is a
   winnable subset.
3. **The bare name is a long-game target**, won by accumulating entity signals (Section E + J),
   not by optimizing a page for it.

---

## Cluster 1 — Identity (core)

**Target URL:** `/` · **Intent:** navigational · **Priority: 1 — highest entity value**

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `omkar jadhav` | **Unwinnable (near term)** | Moderate — a common name | Primary (long game) |
| `omkar jadhav software engineer` | High | Low | Secondary |
| `omkar jadhav developer` | High — a portfolio already ranks #1 | Low | Secondary |
| `omkar jadhav sde` | Moderate | Very low | Supporting |
| `omkar jadhav backend developer` | Moderate | Very low | Supporting |
| `omkar jadhav portfolio` | High | Very low | Supporting |
| `omkar jayvant jadhav` | **Very Low** | Very low | **Disambiguation anchor** |
| `omkar j jadhav` | Very Low | Negligible | Variant |
| `omkar jaywant jadhav` | Very Low | Negligible | Misspelling variant |

**On the legal name.** I ran `"Omkar Jayvant Jadhav"` as an exact phrase. It returned **no results
for that phrase at all** — the engine silently fell back to "Omkar Jadhav". That means near-zero
competition, and it also means near-zero search volume; nobody types a middle name into Google.

**So its value is not traffic — it is entity resolution.** `Person.name` = "Omkar Jayvant Jadhav"
with `alternateName` covering the short forms gives Google a globally unique string to hang the
entity on, which is precisely what "Omkar Jadhav" cannot provide. Treat it as **schema
infrastructure that happens to also be a keyword**, not as a traffic play. It currently appears
nowhere on the site.

**Draft title (60 chars):**
`Omkar Jadhav — Software Development Engineer I at Nonstop IO`

**Draft meta description (156 chars):**
`Omkar Jadhav is a Software Development Engineer I at Nonstop IO Technologies, Pune. B.Tech CSE (Data Science), KIT Kolhapur. C#, NestJS, SQL, Spring Boot.`

**Required on-page elements:** `<h1>` carrying name **and** role (D6) · the I5 canonical
disambiguating statement in the first 100 words · full legal name stated once in visible copy ·
`Person` JSON-LD with complete `sameAs` · profile photo with role-bearing alt text · visible links
to `/about`, `/projects`, `/experience`, `/education`, `/resume`, `/contact`.

**Internal links out:** every C2 page. **Internal links in:** every page (logo/home link).

---

## Cluster 2 — Entity association (name-absent queries)

**Target URL:** `/about` · **Intent:** informational · **Priority: 2**

These are searches where the name is *absent* — someone reconstructing an identity from
attributes. Low volume, exceptionally high entity value, and very low competition because the
attribute combination is nearly unique.

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `sde-1 nonstop io technologies pune` | Very Low | Very low | Primary |
| `kit kolhapur data science graduate backend developer` | Very Low | Very low | Secondary |
| `c# nestjs developer pune` | Low | Low | Secondary |
| `backend developer enterprise reporting c# nestjs` | Very Low | Very low | Supporting |
| `kit college of engineering kolhapur 2026 cse data science` | Low | Very low | Supporting |

**Draft title (58 chars):**
`About Omkar Jadhav — Backend Developer in Pune, India`

**Draft meta description (159 chars):**
`Omkar Jadhav works as an SDE-I at Nonstop IO Technologies in Pune, building backend services in C#, NestJS and SQL. B.Tech CSE (Data Science), KIT Kolhapur 2026.`

**Required on-page elements:** `ProfilePage` + `mainEntity` → `Person` (E12) · the I4 Q&A headings
(*Who is Omkar Jadhav? · Where does Omkar Jadhav work? · What technologies does Omkar Jadhav work
with? · Where did Omkar Jadhav study?*) · ≥600 words (D7) · full legal name and both cities in
visible copy.

**Internal links out:** `/experience`, `/education`, `/skills`, `/projects`, `/resume`.
**Internal links in:** `/` hero, footer, every project page byline.

---

## Cluster 3 — Education

**Target URL:** `/education` · **Intent:** informational · **Priority: 3**

Institution names are **established entities with their own knowledge-graph presence**. Linking to
them via `alumniOf` (E4) borrows disambiguating power that the name alone cannot provide — this is
the cheapest real disambiguation available.

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `omkar jadhav kit college kolhapur` | **Very Low** | Very low | Primary |
| `kit's college of engineering kolhapur alumni software engineer` | Low | Low | Secondary |
| `b.tech computer science data science kit kolhapur` | Moderate | Low | Secondary |
| `omkar jadhav b.tech data science` | Very Low | Very low | Supporting |
| `institute of civil and rural engineering gargoti diploma computer` | Very Low | Very low | Supporting |
| `icre gargoti alumni` | Very Low | Negligible | Supporting |

⚠️ **Note the competitor overlap.** Two other Omkar Jadhavs are educated in Kolhapur — one at
Shivaji University, one a Java developer currently *in* Kolhapur. `omkar jadhav kolhapur` is
therefore **contested**, while `omkar jadhav kit college kolhapur` is **not**, because neither
competitor attended KIT. **Target the institution, not the city.** This distinction is the
difference between a winnable query and a contested one, and it is invisible without having
measured the field.

**Draft title (59 chars):**
`Education — Omkar Jadhav, B.Tech CSE, KIT Kolhapur 2026`

**Draft meta description (154 chars):**
`Omkar Jadhav graduated from KIT's College of Engineering (Autonomous), Kolhapur with a B.Tech in CSE (Data Science) in 2026, scoring 80%. Diploma: ICRE Gargoti.`

**Required on-page elements:** past tense throughout (D11) · `EducationalOrganization` nodes with
full institution names (E4) · `hasCredential` (E5) · percentages **visible** on the page since they
are marked up (E10) · breadcrumbs (C4).

**Internal links out:** `/about`, `/experience`, `/resume`. **In:** `/about`, `/`, `/resume`.

---

## Cluster 4 — Employer

**Target URL:** `/experience` · **Intent:** informational · **Priority: 3**

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `omkar jadhav nonstop io` | **Very Low** | Very low | Primary |
| `nonstop io technologies sde-1` | Low | Low | Secondary |
| `nonstop io technologies kharadi pune engineers` | Low | Low | Secondary |
| `omkar jadhav sde-1` | Very Low | Very low | Supporting |
| `nonstop io technologies backend developer c#` | Very Low | Very low | Supporting |

**Nonstop IO is an indexed entity** — it has a LinkedIn company page, a Peerlist profile, a
Glassdoor presence and a ZoomInfo record (`00-RECON.md` §0.7). Associating with it is high-value
and low-effort.

⚠️ **Prerequisite: fix the employer spelling.** The site currently spells it three different ways
("NonStop io Technologies", "NonstopIO", "Nonstop IO Technologies"). Entity association requires
**one** spelling, used everywhere, matching how the company writes it. Until that's fixed, this
entire cluster is fighting itself.

**Draft title (60 chars):**
`Experience — Omkar Jadhav, SDE-I at Nonstop IO Technologies`

**Draft meta description (158 chars):**
`Omkar Jadhav is an SDE-I at Nonstop IO Technologies, Kharadi, Pune. He builds backend modules for an enterprise reporting product in C#, NestJS and SQL.`

**Required on-page elements:** both roles as separate entries — Software Developer Intern
**2 Feb 2026 – 2 Aug 2026**, then **SDE-I from Aug 2026** · `worksFor` → `Organization` with the
Kharadi `PostalAddress` (E6) · the three confirmed achievements (user audit functionality, Report
Builder contributions, optimized SQL) · breadcrumbs.

**Do not** imply seniority, scale or leadership — he is ~7 months into his first full-time role,
~1.5 months at the SDE-I title.

**Internal links out:** `/skills` (C#, NestJS, SQL), `/projects`, `/about`, `/resume`.

---

## Cluster 5 — Projects

**Target URL:** `/projects` + one page per project · **Intent:** informational · **Priority: 2**

**This is where the engagement is won.** Project pages are the only pages that can rank for
queries *not containing his name* — the entry point for someone who has never heard of him
(`02-AUDIT.md` §3.3).

### 5a · Portfolio + AI assistant — `/projects/portfolio-ai-assistant`

**The single most differentiating page on the site.** Per confirmed decision (option **b**), the
implementation may be described truthfully — pgvector, embeddings, retrieval, multi-provider
failover — while these stay out of `knowsAbout` and on-page skill lists (E3).

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `portfolio site with ai chatbot spring boot react` | Low | Low | Primary |
| `multi provider llm failover java` | **Very Low** | Low | Secondary |
| `pgvector spring boot embeddings` | Low | Low | Secondary |
| `mcp server spring ai portfolio` | **Very Low** | Low — growing | Secondary |
| `llm circuit breaker provider quota java` | Very Low | Very low | Supporting |

**Draft title (59 chars):** `Portfolio with AI Assistant — Spring Boot, React, pgvector`
**Draft meta (157 chars):** `Omkar Jadhav built this portfolio: a Spring Boot 3.5 API and React 18 SPA with an AI assistant, a multi-provider LLM failover router, and a public MCP server.`

⚠️ **Blocked on naming.** The seeded slug is `git-portfolio` with a `Next.js 14` description that
must be deleted entirely. Needs a new slug and copy written from the real codebase.

### 5b · AI Interview Preparation System — `/projects/ai-interview-preparation-system`

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `ai interview preparation system python gemini` | Low | Low | Primary |
| `voice interview practice gemini api speech recognition` | Very Low | Low | Secondary |
| `gemini api evaluate spoken answers scoring` | Very Low | Very low | Supporting |

**Draft title (57 chars):** `AI Interview Preparation System — Python & Gemini API`
**Draft meta (152 chars):** `A voice-driven interview practice system by Omkar Jadhav. Speech Recognition transcribes answers; the Gemini API scores them and generates feedback.`

Likely repo: `github.com/omkarjadhav1011/InterviewAI` — **confirm before linking.**

### 5c · Text-to-Image Generator — `/projects/text-to-image-generator`

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `text to image generator streamlit hugging face` | Moderate | Low | Primary |
| `streamlit hugging face inference api image` | Moderate | Low | Secondary |

**Draft title (56 chars):** `Text-to-Image Generator — Streamlit & Hugging Face API`
**Draft meta (149 chars):** `A Streamlit web app by Omkar Jadhav that turns a text prompt into an image using a Hugging Face hosted model, with error handling for slow generations.`

⚠️ Seeded as `snapsktch`; seeded repo link **404s**. Publish with **no** `codeRepository` (E8)
unless a URL is confirmed.

### 5d · Expense Tracker — `/projects/expense-tracker`

| Keyword | Competition | Volume band | Role |
|---|---|---|---|
| `expense tracker react spring boot postgresql` | Moderate | Moderate | Primary |
| `full stack expense management spring boot rest api` | Moderate | Low | Secondary |
| `postgresql schema expense tracking category month` | Low | Low | Supporting |

**Draft title (56 chars):** `Expense Tracker — React, Spring Boot & PostgreSQL`
**Draft meta (155 chars):** `A full-stack expense manager by Omkar Jadhav: React frontend, Spring Boot REST API, PostgreSQL schema with category and monthly spending aggregation.`

Repo confirmed to exist: `github.com/omkarjadhav1011/expense-tracker` (HTTP 200).

### 5e · crop-recommendation and dev-mobiles — 🚫 **blocked, no copy possible**

Both were confirmed for retention, but **neither has a single confirmed fact**. Their only
descriptions are `DataSeeder.java:122-135` demo content with fabricated metrics, and
`dev-mobiles`'s description credits *"my internship at Dnyanda Solutions"* — the employer being
deleted.

**Cannot be written until 2–3 sentences of real description are supplied per project**
(`00-RECON.md` §9.3–9.4). Publishing the seeded text would violate the no-fabrication rule.

**Required on-page elements, all project pages:** `SoftwareSourceCode` with `author` → `#person`
(E8) · `codeRepository` **omitted** where no repo exists — never a 404 link (K5) · breadcrumbs ·
≥300 words · per-page OG image · link back to `/projects` and `/about`.

---

## Cluster 6 — Skills

**Target URL:** `/skills` + per-skill sections · **Intent:** informational · **Priority: 4**

**Blunt assessment: most of this cluster is unwinnable, and pursuing it is a waste of effort.**

| Keyword | Competition | Verdict |
|---|---|---|
| `c# developer`, `react developer`, `python developer`, `sql`, `docker` | **Unwinnable** | Owned by Microsoft, Meta, python.org, Docker Inc. and StackOverflow. A personal site will never rank. **Do not target.** |
| `spring boot rest api`, `nestjs tutorial`, `postgresql queries` | **Unwinnable** | Owned by official docs and Baeldung/DigitalOcean-scale publishers. **Do not target.** |
| `c# nestjs developer pune` | Low | ✅ Reachable — geography narrows the field |
| `gemini api python integration example` | Moderate | ⚠️ Reachable only as long-tail (Cluster 7) |
| `hugging face api streamlit image generation` | Moderate | ⚠️ Reachable as long-tail |

**What `/skills` is actually for:** not ranking for skill names, but **feeding `knowsAbout`** (E3)
and providing the visible on-page content that structured data must mirror (E10). Its SEO value is
entity reinforcement, not traffic. Build it, keep it honest, and do not optimize it for head terms.

**Draft title (55 chars):** `Skills — Omkar Jadhav: C#, NestJS, Spring Boot, SQL`
**Draft meta (151 chars):** `The technologies Omkar Jadhav works with: C#, NestJS and SQL at Nonstop IO, plus Java, Spring Boot, React, TypeScript, Python, PostgreSQL and Docker.`

🚫 **Explicitly not targeted, per the withdrawal list:** Next.js, FastAPI, ChromaDB, vector
databases, RAG pipelines. No keyword, title, description, heading or `knowsAbout` entry in this
cluster may reference them. (The *portfolio project page* in 5a may describe its own implementation
— that is a statement about software, not a skill claim.)

---

## Cluster 7 — Content and long-tail

**Target URL:** `/blog/{slug}` · **Intent:** informational · **Priority: 5 (after Waves 1–4)**

The only cluster that can attract people who have never heard of him, and the only realistic
backlink engine (J3). Topics are drawn strictly from work he has actually done.

| # | Article | Primary keyword | Competition | Evidence it's his to write |
|---|---|---|---|---|
| 1 | Implementing end-to-end user audit logging in NestJS | `nestjs user audit log implementation` | **Low** | Confirmed: he built exactly this in production |
| 2 | Writing optimized SQL for a reporting module | `optimize sql queries reporting module` | Moderate | Confirmed: Report Builder work |
| 3 | Multi-provider LLM failover in Java | `llm provider failover retry java` | **Very Low** | Built it — `LlmRouter`, circuit breaker, quotas |
| 4 | Deterministic scoring beside an LLM | `deterministic scoring llm output` | **Very Low** | Built it — `MatchScoreCalculator` |
| 5 | Building an MCP server with Spring AI | `spring ai mcp server tutorial` | **Very Low** — growing fast | Built it — `PortfolioMcpTools` |
| 6 | Envelope encryption for file storage in Spring Boot | `envelope encryption aes gcm spring boot s3` | Low | Built it — `EnvelopeCryptoService` |
| 7 | Integrating the Gemini API into a Python app | `gemini api python structured prompts` | Moderate | Confirmed: Interview Prep project |
| 8 | Flyway + Hibernate `validate` in practice | `flyway hibernate validate schema mismatch` | Low | Confirmed: the repo's migration discipline |

**Highest-value: #3, #4 and #5.** Very low competition, genuinely scarce expertise, and each
independently reinforces the Cluster 5a differentiator. #5 in particular sits on a fast-growing
topic with almost no good content.

**Format per article (I3/I4):** name the entity in the first sentence · self-contained paragraphs ·
concrete dates · `Article` schema with `author` → `#person` · `rel=canonical` back to the site on
any dev.to/Hashnode cross-post (J3).

---

## Name competitors, and what beating each requires

| Competitor | Their strength | What beating them requires | Realistic? |
|---|---|---|---|
| [omkarjadhav0456.netlify.app](https://omkarjadhav0456.netlify.app/) — "AI & LLM Engineer, Full Stack" | Ranks #1 for "omkar jadhav portfolio developer"; near-identical positioning | Deeper content, real project pages, full entity graph, reciprocal `sameAs`. His site is a single page too — **beatable on architecture alone** | ✅ **Yes, 3–6 months** |
| [omkarkjadhav.netlify.app](https://omkarkjadhav.netlify.app/) — frontend, **Pune** | Same city | Same as above; he has no employer entity association | ✅ Yes |
| [omkar118.github.io](https://omkar118.github.io/omkarsite/) — backend, 5+ yrs | `github.io` authority, seniority | Content depth + entity graph. Cannot out-experience him — **don't try** | ⚠️ Partially |
| [LinkedIn — SWE at Google](https://www.linkedin.com/in/omkar-jadhav-7922aba4/) | Google + LinkedIn domain authority | **Cannot be beaten on the bare name.** Compete only on qualified queries | ❌ No |
| [LinkedIn — Senior SWE, LTIMindtree](https://www.linkedin.com/in/omkar-jadhav07/) | Seniority, LinkedIn authority | Same — qualified queries only | ❌ No |
| [LinkedIn — Java Dev @1GEN, **Kolhapur**](https://www.linkedin.com/in/omkar-jadhav09/) | Same city, same language family | **Target KIT, not Kolhapur** (Cluster 3) | ✅ Yes, on KIT queries |
| [LinkedIn — Full Stack @ Sceniuz, Kolhapur](https://www.linkedin.com/in/omkar-jadhav-768660248/) | Shivaji University, Kolhapur | Same — institution beats city | ✅ Yes |
| [in/omkar-jadhav](https://in.linkedin.com/in/omkar-jadhav) — Testing/Selenium | **Owns the clean vanity slug** | Nothing — unavailable. Claim a distinct, consistent slug and use it everywhere | ➖ N/A |
| Behance / IMDb / cyclist / astrophysicist / podcaster | Non-tech, different verticals | Nothing needed — they **fragment** the SERP, which helps a tech-qualified query | ➖ Neutral |

**Summary:** the bare name is unwinnable against Google and LTIMindtree. The **portfolio-site
competitors are all beatable**, because every one of them is a single-page site with no structured
data — precisely the position he's leaving. That is the winnable fight, and it is winnable on
execution rather than authority.

---

## Keywords with no home — the content gaps

Every keyword below has **no page that could rank for it today**. These are the Wave 3 and Wave 5
build list.

| Keyword / query | Needs | Wave |
|---|---|---|
| `omkar jadhav resume` | `/resume` — an **HTML** résumé, not only a PDF | 3 |
| `omkar jadhav contact` / `hire omkar jadhav` | `/contact` as a real URL | 3 |
| `omkar jadhav kit college kolhapur` | `/education` | 3 |
| `omkar jadhav nonstop io` | `/experience` | 3 |
| `omkar jadhav projects` | `/projects` index | 3 |
| each project by name | `/projects/{slug}` ×4–6 | 3 |
| `omkar jayvant jadhav` | Legal name in visible copy + schema — **currently nowhere on the site** | 2–3 |
| `who is omkar jadhav` | I4 Q&A block on `/about` | 3 |
| `nestjs user audit log implementation` | Blog #1 | 5 |
| `llm provider failover retry java` | Blog #3 | 5 |
| `deterministic scoring llm output` | Blog #4 | 5 |
| `spring ai mcp server tutorial` | Blog #5 | 5 |
| `envelope encryption aes gcm spring boot s3` | Blog #6 | 5 |
| `gemini api python structured prompts` | Blog #7 | 5 |
| `optimize sql queries reporting module` | Blog #2 | 5 |
| `flyway hibernate validate schema mismatch` | Blog #8 | 5 |

---

## Priority sequence

| Priority | Cluster | Target | Why first |
|---|---|---|---|
| **1** | Identity | `/` | Everything else points here. Entity root. |
| **2** | Entity association | `/about` | Carries `ProfilePage` + the Q&A block AI engines retrieve |
| **2** | Projects | `/projects/{slug}` | The only pages that rank without his name; the real differentiator |
| **3** | Education | `/education` | KIT association is the cheapest real disambiguation available |
| **3** | Employer | `/experience` | Nonstop IO is an indexed entity — free association |
| **4** | Skills | `/skills` | Entity reinforcement, not traffic. Build honestly, don't optimize |
| **5** | Content | `/blog/{slug}` | Highest ceiling, longest payback. Only after 1–4 ship |

---

**Next:** Phase 4 — implementation in waves, starting with Wave 0 (domain-agnostic foundation).
