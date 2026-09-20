# 02 — AUDIT

The site measured against [`01-SEO-RULEBOOK.md`](./01-SEO-RULEBOOK.md). Rule IDs are that
document's. Evidence is a `file:line`, a command output, or a URL fetched on 2026-09-19.

**Headline result: 12 Pass · 11 Partial · 57 Fail, across 80 rules.**

That ratio is not a sign of a neglected site — the codebase is careful, well-commented, and
architecturally serious. It is a sign that **the SEO surface was never built**. The Next.js → Vite
port (`vite.config.ts:20`) carried the application across but left `next/head`,
`generateMetadata`, and server rendering behind, and nothing replaced them. Almost every failure
below traces to that one omission plus a single typo in a domain name.

---

## 1. Plugin audit reconciliation

Per the brief, the plugin skills were run and are reported here against my own inspection.

| Plugin skill | What it contributed | What it missed |
|---|---|---|
| `seo-audit` | Confirmed the checklist shape: meta tags, headings, images, schema, internal links | It audits *source files* for `page.tsx`-style metadata. On a Vite SPA there is nothing to find — it cannot tell "no metadata implementation exists" apart from "metadata is fine". It never fetched the raw HTML, so **it did not detect that the served body is empty**. |
| `technical-seo` | Correct CWV thresholds; prompted the compression/TTFB/exposed-file checks | Its React guidance is Next.js-specific (`next/image`, Server Components, `use client`) and does not apply. **It has no check for "the canonical points at a domain that 404s"** — the single most damaging issue on this site. |
| `broken-links` | Correct framing on link equity and 404 cost | Found nothing on its own. The broken links here are **in database rows and a seeder**, not in source markup, so a source-tree scan misses all of them. |
| `internal-linking` | Orphan/anchor-text framing | Its model assumes links are `<a href>` in source. This site's primary navigation is `<button onClick>` (`Navbar.tsx:86-88`), which the scan counts as *no links at all* rather than flagging *why* that's a problem. |
| `content-strategy` | Intent mapping and the prioritization matrix, reused in `03-KEYWORD-MAP.md` | Built for a B2B blog with a sales funnel. Its scoring model has no representation for the actual conversion here — **one human or one AI correctly identifying one person** — so its priority ordering had to be discarded. |

**Net:** the plugins produced a usable checklist skeleton and zero of the eight Critical findings.
Every Critical below came from fetching the live site, reading the database, and extracting the
PDF. This is the expected division of labour, and the reason the brief said not to treat plugin
output as the audit.

---

## 2. Findings

Impact: **Critical** (blocks indexing or publishes a false fact) · **High** · **Medium** · **Low**.
Effort: **S** <1h · **M** 1–4h · **L** 1–2 days · **XL** multi-day.

### Section A — Crawlability and indexation

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| A1 robots.txt correct | ❌ Fail | `frontend/public/robots.txt:4` → `Sitemap: https://omkarjadhav.vercel.app/sitemap.xml`; that host returns **404**. No admin `Disallow`, no AI-agent groups | Critical | S | Generate at build from `VITE_SITE_URL`; add `Disallow: /admin/`; add I2 agent groups |
| A2 XML sitemap | ❌ Fail | `frontend/public/sitemap.xml` — all 5 `<loc>` on the dead host; `lastmod` frozen `2026-06-01`; uses `priority`/`changefreq` (ignored by Google) | Critical | M | Generate at build from route manifest + `VITE_SITE_URL` |
| A3 no fragment URLs | ❌ Fail | `sitemap.xml:10,16,22,28` — `/#about`, `/#projects`, `/#experience`, `/#contact` | High | S | Remove; replaced by real routes (C2) |
| A4 self-referential canonical | ❌ Fail | `frontend/index.html:12` → `https://omkarjadhav.vercel.app` (404). Identical on every route | **Critical** | M | Per-route canonical from `VITE_SITE_URL`, emitted by the prerenderer |
| A5 correct status codes | ❌ Fail | `curl -o /dev/null -w "%{http_code}" $SITE/this-page-does-not-exist-12345` → **200**. `frontend/vercel.json:7` rewrites `/(.*)` → `/index.html` | Critical | M | Prerender real routes to files; add `404.html`; drop the blanket rewrite |
| A6 admin excluded | ⚠️ Partial | Not in sitemap ✅; no robots `Disallow` ❌; no `noindex` ❌ | Medium | S | `Disallow: /admin/` + `noindex` meta on admin routes |
| A7 redirect hygiene | ✅ Pass | No redirects configured; `curl -sIL` shows no 3xx on any route | — | — | — |
| A8 trailing slash | ⚠️ Partial | `vercel.json` sets no `trailingSlash`; catch-all serves `/mcp` and `/mcp/` both 200, byte-identical | Medium | S | `"trailingSlash": false` + canonical enforcement |
| A9 no orphan pages | ❌ Fail | Zero crawlable links: `Navbar.tsx:86-88` renders `<button onClick={() => goTo(s.id)}>`, not `<a href>`. With JS off, the site has **no links at all** | Critical | M | Sections become routes with real `<a href>`; keep smooth-scroll as progressive enhancement |
| A10 crawl budget | ❌ Fail | Consequence of A5 — unbounded 200-returning URL space | High | — | Resolved by A5 |
| A11 IndexNow | ❌ Fail | Absent | Medium | S | Key file + deploy-hook ping |

**Note on A9.** Project detail pages are *not* orphaned in the React sense — `PRCard.tsx:197`
renders `<Link to={`/projects/${project.slug}`}>`, which produces a real `<a href>`. They become
crawlable the moment prerendering lands. The orphaning problem is the **section navigation**,
which is buttons.

### Section B — Rendering

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| B1 content in server HTML | ❌ Fail | `curl -sL $SITE/` → 2,392 bytes; body is `<div id="root"></div>`. Identical on `/`, `/recruiter`, `/mcp`, `/projects/git-portfolio`, `/scratch` | **Critical** | L | Build-time prerender (option **a**, confirmed) |
| B2 prerender at build | ❌ Fail | No prerender step in `frontend/package.json:8` (`tsc --noEmit && vite build`) | **Critical** | L | Post-build headless-browser prerender per public route |
| B3 build fails loudly | ❌ Fail | N/A — no prerender exists | Critical | M | Assert per route: no skeleton/error sentinel, expected content present; throw otherwise |
| B4 no interaction-gated content | ❌ Fail | `FloatingAIButton.tsx:8` → `openInMode("ai")`; `useTerminal.ts:4-7` renders profile/projects/skills/experience only in the Ctrl+K palette | Critical | L | Every fact the assistant states must exist on a page |
| B5 prerender/hydration agree | ❌ Fail | N/A | High | — | Verify after B2 |
| B6 no runtime API for first paint | ❌ Fail | `Home.tsx:60-64` — five React Query calls; `:66-68` renders skeletons while pending, `:83-96` renders `"fatal: failed to load portfolio data from the backend."` on failure. Backend measured at **6.86 s** cold | **Critical** | L | Bake content at build |

### Section C — Information architecture

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| C1 one URL ≠ many clusters | ❌ Fail | `router.tsx:38-43` — all biography, skills, experience, education and contact content lives on `/` | Critical | XL | Build C2 |
| C2 target structure | ❌ Fail | Missing `/about`, `/projects`, `/skills`, `/experience`, `/education`, `/resume`, `/contact`, `/blog` | Critical | XL | Wave 3 |
| C3 slug rules | ✅ Pass | `git-portfolio`, `dev-mobiles`, `crop-recommendation`, `snapsktch` — lowercase, hyphenated | — | — | Rename `git-portfolio` when the project is rewritten |
| C4 breadcrumbs | ❌ Fail | No breadcrumb component; `grep -rn "breadcrumb" src/` → none | High | M | Visible trail + `BreadcrumbList` (E7) |
| C5 ≤3 clicks | ✅ Pass | Flat structure; max depth 2 | — | — | Preserve through C2 |
| C6 contextual internal links | ❌ Fail | `grep -rhoE 'to="[^"]+"'` → 12 internal links total, all navigational. Zero body-copy links | High | M | Add contextual links with descriptive anchors |

### Section D — On-page

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| D1 title formulas | ❌ Fail | `index.html:7` — one stale title on **every** route: `"Omkar Jadhav — B.Tech CSE (Data Science) Student & Full-Stack Developer"` | **Critical** | M | Per-route titles from D1 table |
| D2 meta descriptions | ❌ Fail | `index.html:10` — one stale description on every route, containing "Student" and "Open to internships" | **Critical** | M | Per-route, written |
| D3 exactly one h1 | ❌ Fail | **Two** on `/`: `HeroSection.tsx:78` and `AboutSection.tsx:228` | High | S | Demote AboutSection's to `<h2>` |
| D4 heading order | ⚠️ Partial | Within sections correct (`h2` → `h3` in `ExperienceSection.tsx:145`); the document outline is `h1, h1, h2…` because of D3 | Medium | S | Resolved with D3 |
| D5 semantic landmarks | ⚠️ Partial | Good use of `<nav>` (`Navbar.tsx:61`), `<footer>` (`Footer.tsx:10`), `<section>`, `<article>`. But **nested `<main>`** — `MainLayout.tsx:20` wraps `<Outlet/>` in `<main>` and `ProjectDetail.tsx:51` renders its own `<main>` inside it | Medium | S | Remove the inner `<main>`; add `<header>` around the navbar |
| D6 keyword placement | ❌ Fail | `HeroSection.tsx:78-89` — `<h1>` contains only `# omkarjadhav` + the name. No role, employer or city in the strongest heading on the site | High | S | `Omkar Jadhav — Software Development Engineer I` |
| D7 content depth | ❌ Fail | 0 words crawlable on every route (B1) | Critical | L | Resolved by B2 + Wave 3 |
| D8 unique content per page | ❌ Fail | All five tested routes are byte-identical, 2,392 bytes | Critical | L | Resolved by B2 |
| D9 descriptive anchor text | ⚠️ Partial | Terminal-themed anchors — `"git checkout projects"` (`HeroSection.tsx:118`), `"git show --contact"`, `"git export --resume"`. Distinctive and on-brand, but they carry **no keyword signal** | Medium | M | Keep the aesthetic; add an accessible name or supporting text carrying the real target |
| D10 image alt text | ⚠️ Partial | `AboutSection.tsx:55` → `alt={name}` = "Omkar Jadhav" (thin but valid). `AboutSection.tsx:108` → `alt={t.name}` ✅. `ProfileClient.tsx:249` → `alt="avatar"` ❌ (admin-only) | Medium | S | Profile photo alt should carry role + employer |
| D11 accurate employment copy | ❌ Fail | See the §0.5 tables — "Student" in title/description/OG image; `availableForWork: true`; three conflicting job titles live | **Critical** | M | Wave 1 |

### Section E — Structured data

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| E1–E14 (all) | ❌ Fail | `curl -sL $SITE/ \| grep -c "application/ld+json"` → **0**. No JSON-LD anywhere in the repo or on any route | **Critical** | L | Wave 2 — one typed module emitting the full graph |

Fourteen rules, one root cause: **the entity layer does not exist**. Given that entity
disambiguation is the central problem of this engagement (fifteen namesakes — `00-RECON.md` §0.7),
this is the highest-value work in the project, and none of it has been started.

**Blocked:** E2 (`sameAs`) cannot be completed until the LinkedIn URL is confirmed and a LeetCode
URL is supplied (`00-RECON.md` §9.1, §9.2). Everything else in Section E can proceed.

### Section F — Performance and Core Web Vitals

Measurement method stated per row. No Lighthouse run — no field data exists (zero traffic, no
CrUX sample, no Search Console). These are **lab/synthetic observations from `curl` timings**, and
are directional, not p75 field values.

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| F1 LCP ≤ 2.5 s | ❌ Fail | LCP content requires 5 chained XHRs to a cold Render free-tier backend; `GET /api/profile` measured **6.86 s** cold (curl, single sample). Until then the page is skeletons | Critical | L | Prerender (B2) removes the API from the critical path entirely |
| F2 INP ≤ 200 ms | ❓ Unmeasured | No field data. Risk factors: 536 KB raw main bundle; framer-motion scroll animations | Medium | — | Re-measure after Wave 4 |
| F3 CLS ≤ 0.1 | ⚠️ Partial | Skeletons reserve space ✅. But **no `<img>` in the repo has `width`/`height`** (`AboutSection.tsx:53-56`, `:108`, `:281`), and the Google Fonts swap has no metric-compatible fallback | High | M | Add dimensions; add `size-adjust` fallback |
| F4 modern image formats | ❌ Fail | Only asset is `opengraph-image.png` (48,257 B). Avatar is JPEG from the backend origin. No AVIF/WebP, no `srcset` | Medium | M | Convert; add `srcset`/`sizes` |
| F5 `fetchpriority` on LCP | ❌ Fail | `grep -rn "fetchpriority\|loading=" src/` → none | Medium | S | `fetchpriority="high"` on the profile photo; lazy below fold |
| F6 self-hosted fonts | ❌ Fail | `index.html:41-44` — render-blocking Google Fonts stylesheet for JetBrains Mono. `display=swap` ✅ and `preconnect` ✅, but the request still blocks | High | M | Self-host woff2; inline `@font-face`; preload the LCP face |
| F7 JS bundle budget | ⚠️ Partial | Main bundle **535,979 B raw / 172,873 B gzipped** (measured via `curl --compressed`). Under the 200 KB gz budget, but heavy to parse on mobile | Medium | M | Audit framer-motion + radix usage |
| F8 code-split heavy widgets | ⚠️ Partial | Admin, recruiter, MCP and project detail are lazy ✅ (`router.tsx:13-30`). But `CommandPalette` and `FloatingAIButton` are **eager imports in `MainLayout.tsx:5,7`**, so the chat UI ships on every public page load | Medium | S | Lazy-load both behind their trigger |
| F9 no render-blocking resources | ❌ Fail | Google Fonts stylesheet (F6) | High | M | Resolved with F6 |
| F10 caching headers | ✅ Pass | `Cache-Control: public, max-age=0, must-revalidate` on HTML; Vercel serves fingerprinted `/assets/*` immutable | — | — | — |
| — compression | ✅ Pass | `curl --compressed` on the main bundle: 535,979 → 172,873 B | — | — | — |

### Section G — Mobile and accessibility

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| G1 mobile-first indexing | ❌ Fail | No content in either rendering (B1) | Critical | L | Resolved by B2 |
| G2 viewport meta | ✅ Pass | `index.html:6` — `width=device-width, initial-scale=1.0`, no `maximum-scale`, no `user-scalable=no` | — | — | — |
| G3 tap targets ≥44px | ✅ Pass | `FloatingAIButton.tsx:12` — `min-w-[44px] min-h-[44px]`; mobile polish commit `561b188` | — | — | — |
| G4 no horizontal scroll | ❓ Unmeasured | Needs a real-device / 320px check | Low | S | Verify in Wave 4 |
| G5 content parity | ✅ Pass | Same React tree at all breakpoints; no `display:none` content gating | — | — | — |
| G6 a11y overlaps | ⚠️ Partial | `<html lang="en">` ✅ (`index.html:3`); `focus-visible` and `useReducedMotion` are project conventions ✅; `aria-hidden` on decorative icons ✅ (`ExternalLink.tsx:24`). Fails inherited from D3/D5/D10 | Medium | M | Resolved with D3, D5, D10 |

### Section H — Social and sharing

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| H1 complete OG | ❌ Fail | `index.html:15-24` — `og:url` and `og:image` on the dead host; description says "Student"/"internships"; identical on all routes; no `og:locale`, no `og:image:alt` | Critical | M | Per-route, env-derived |
| H2 Twitter card | ❌ Fail | `index.html:30` — `twitter:creator="@omkarjadhav"`. **Confirmed: he has no X/Twitter account** | High | S | Remove `twitter:creator`; keep card tags |
| H3 per-page OG images | ❌ Fail | One static PNG for the whole site, with the dead domain and the stale headline **rendered into pixels** (`scripts/generate-og.mjs:9,10,31`) | High | M | Generate per route from `VITE_SITE_URL` + live copy |
| H4 favicon set + manifest | ❌ Fail | `grep -c 'rel="icon"\|manifest' index.html` → **0**. `/favicon.ico` returns `text/html`, 2,392 B | High | M | Full icon set + manifest |

### Section I — AI answer-engine optimization

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| I1 `llms.txt` | ❌ Fail | `curl $SITE/llms.txt` → 200 `text/html`, the SPA shell | High | S | Author + serve as `text/plain` |
| I2 AI crawler policy | ❌ Fail | `robots.txt` has one `User-agent: *` group; no named AI agents | High | S | Explicit allow for all (confirmed decision) |
| I3 citation-friendly writing | ❌ Fail | `data/profile.ts:8-12` bio opens *"I build things for the web…"* — first person, no entity named, unquotable when extracted | High | M | Rewrite per I3 |
| I4 Q&A formatting | ❌ Fail | No question-shaped headings anywhere | Medium | M | Wave 3 on `/about` |
| I5 canonical disambiguating statement | ❌ Fail | Does not exist. **"Omkar Jayvant Jadhav" appears nowhere on the site** — the only occurrence online is inside the outdated résumé PDF | **Critical** | S | Author once; reuse verbatim everywhere |
| I6 fact consistency | ❌ Fail | Three job titles, three employer spellings, two start dates live simultaneously (`00-RECON.md` §0.5C) | **Critical** | M | Fact matrix, applied everywhere |
| I7 extractable without JS | ❌ Fail | `curl -sL $SITE/ \| sed 's/<[^>]*>//g'` → **his name does not appear**. `GPTBot`, `ClaudeBot`, `PerplexityBot`, `CCBot` do not execute JS | **Critical** | L | Resolved by B2 |

### Section J — Off-page and entity authority

| Rule | Status | Evidence | Impact | Effort | Fix |
|---|---|---|---|---|---|
| J1 `sameAs` reciprocity | ❌ Fail | GitHub profile has **no website link and no profile README** (fetched `github.com/omkarjadhav1011?tab=repositories`). Nothing links back to the site from anywhere | **Critical** | S | Wave/Phase 5 |
| J2 external fact consistency | ❌ Fail | GitHub bio: *"SDE **Intern** @ Nonstop IO \| Java · Spring Boot · PostgreSQL · React \| **Final-year CS @ KIT, 2026**"* — stale title **and** a student claim on his highest-authority profile | **Critical** | S | Phase 5.5 |
| J3 backlinks | ❌ Fail | Zero. `site:` returns nothing; no referring domains | High | L | Phase 5.6 |
| J4 GitHub optimization | ❌ Fail | No README, no website field, no topics, repo descriptions missing on `personal_portfolio`, `expense-tracker`, `crop-recommendation` | **Critical** | M | Phase 5.5 |
| J5 LinkedIn optimization | 🚫 Blocked | Cannot audit — the URL in the brief resolves to a **different person** (`00-RECON.md` §9.1) | Critical | — | Blocked on confirmation |
| J6 controlled facts correct | ❌ Fail | Inherited from J2/J5 | Critical | M | Phase 5 |

### Section K — Anti-patterns

| Rule | Status | Evidence | Impact |
|---|---|---|---|
| K1 leftover `noindex` | ✅ Pass | `curl -sI $SITE/ \| grep -i x-robots-tag` → nothing. No `noindex` in the repo | — |
| K2 blocked CSS/JS | ✅ Pass | `robots.txt` has only `Allow: /` | — |
| K3 duplicate titles/descriptions | ❌ Fail | One title and one description across **all** routes | Critical |
| K4 thin pages | ❌ Fail | Every route serves 0 words of content | Critical |
| K5 broken links | ❌ Fail | 3 published GitHub URLs 404 (`/dev-mobiles`, `/git-portfolio`, `/snapsktch`); `liveUrl` → dead `omkarjadhav.vercel.app` (`data/projects.ts:18`, `DataSeeder.java:119`) | High |
| K6 mixed content | ✅ Pass | No `http://` asset references; HSTS set | — |
| K7 missing canonicals | ⚠️ Partial | Present but wrong and non-unique (see A4) | Critical |
| K8 autogenerated filler | ❌ Fail | `data/profile.ts:35-45` — `funFacts` / `stash` template copy, incl. *"My Hugging Face API calls cost more than my monthly coffee budget"* | Medium |
| K9 placeholder social links | ❌ Fail | X/Twitter link for an account he doesn't own (`data/profile.ts:27-30`, `DataSeeder.java:94`, live API); LinkedIn URL resolves to a different person | **Critical** |
| K10 hardcoded domain strings | ❌ Fail | **13 locations** enumerated in `00-RECON.md` §0.8 | Critical |
| K11 fabricated metrics | ❌ Fail | `DataSeeder.java:113-143` — invented stars/forks/commits live in production via `render.yaml:50` `SEED_DEMO_DATA=true` | **Critical** |
| K12 soft 404s | ❌ Fail | Every unknown URL returns 200 | Critical |
| K13 fragment URLs as pages | ❌ Fail | 4 of 5 sitemap entries | High |
| K14 schema ≠ visible content | ➖ N/A | No schema exists | — |
| K15 stale downloadable documents | ❌ Fail | `/api/profile/resume` — old phone, "student", "seeking", Next.js, FastAPI, ChromaDB, RAG (`00-RECON.md` §0.5F) | **Critical** |
| K16 stuffing / hidden text / cloaking | ✅ Pass | No evidence of any | — |

**One clarification on K6.** I probed `/.env`, `/.git/config`, `/.vercel/project.json` and
`/package.json` on the live site. All returned **200 `text/html`** — but that is the SPA catch-all
rewrite serving the app shell, **not** file exposure. No secrets are reachable. It is another
symptom of A5, not a security finding.

---

## 3. What a standard SEO checklist would miss on this specific site

Everything above would appear on any competent audit. The following would not, and several matter
more than the items that would.

### 3.1 The canonical typo is a single-character-class failure with total blast radius

A checklist asks "is a canonical present?" — yes. "Is it absolute?" — yes. "Is it self-referential?"
— it *looks* self-referential, because it's a plausible-looking URL for this person.

The two halves of his name are transposed. The live site is `jadhavomkar`; the canonical says
`omkarjadhav`. I fetched it: **404**. Every canonical, `og:url`, `og:image`, `twitter:image`, all
five sitemap entries and the robots.txt sitemap line inherit the same typo.

**This one string is a sufficient explanation for zero indexation on its own**, independent of the
rendering problem. It is also the cheapest fix in the entire engagement. No automated tool in the
plugin set checks whether a canonical target resolves — and `technical-seo`'s checklist includes
"canonical URLs — self-referencing and cross-domain" without a resolution check.

### 3.2 The résumé PDF is the highest-authority document about him, and nobody audits PDFs

A checklist audits HTML pages. `/api/profile/resume` is a 90 KB PDF linked from the hero CTA, and
it is the **single most complete, most quotable, most citation-ready statement of his identity
that exists on the internet**. Google indexes PDFs. AI assistants weight résumés heavily. Recruiters
download them.

It contains the old phone number, "final-year student", "seeking a role", and Next.js, FastAPI,
ChromaDB and RAG pipelines — every withdrawn claim, in one authoritative document, on his own
domain. Fixing the HTML while this PDF stays live produces a site that **contradicts itself**, and
in a conflict between a polished résumé and a web page, the résumé wins with both humans and
models.

**Replacing this PDF is a higher-priority action than any structured-data work.**

### 3.3 Withdrawing the technologies costs him his only real differentiator — unless it's handled deliberately

This is the strategic tension at the centre of the engagement, and no checklist can see it.

Against fifteen namesakes — one at Google, several with 5+ years, one positioned as an "AI & LLM
Engineer" with a better-ranking portfolio — his genuine advantage is not "C# and SQL at a Pune
consultancy". Thousands of engineers have that. His advantage is that he **built and shipped** a
multi-provider LLM failover router with circuit-breaking and per-provider quotas, a pgvector-backed
retrieval layer, a public MCP server, an envelope-encrypted document vault, and TOTP MFA — as a
personal project, in his first year.

The withdrawal list, applied naively, deletes exactly that. You chose option **(b)**, which
resolves it correctly: the project may be described truthfully while the skills list stays
conservative. But the *execution* has to be deliberate — if project copy gets sanitized into "an
AI chatbot", the site throws away the one thing that makes him unmistakable and falls back to
competing on a description that fits a thousand people.

**The project pages are where this engagement is won or lost**, and that is not a conclusion any
SEO checklist produces.

### 3.4 Three of his own "facts" are wrong at the source, and one belongs to a stranger

An audit assumes the brief is ground truth. Here, checking against evidence found:

- The LinkedIn URL in the brief → a different person (Dropouts Technologies LLP, Univ. of Pune 2005–2009).
- An X/Twitter account published in `sameAs` → he doesn't own it.
- `crop-recommendation` → he said it isn't on GitHub; it returns 200.
- Employment dates → three versions live, none matching the confirmed truth.

**Publishing `sameAs` before resolving these would be actively harmful**, not merely incomplete.
`sameAs` is a machine-readable assertion of identity; a wrong entry instructs Google to merge him
with a stranger, and reciprocity failure guarantees the ambiguity persists. The correct move is to
ship a *smaller* `sameAs` containing only GitHub, and expand it once verified.

### 3.5 `vercel.app` means there is nothing to protect — which inverts the migration advice

Standard advice is "migrate carefully, expect a ranking dip, wait for a quiet period".

`vercel.app` is on the Public Suffix List, so no authority accrues to the subdomain from the parent
domain, and with zero indexed pages and zero backlinks there is **nothing to transfer and nothing
to lose**. The usual caution is inverted: **migrate as early as possible**. Every week on the
temporary domain is a week of work accruing to an address he intends to abandon.

Concretely: **buy the domain before Wave 5 content work begins**, not after.

### 3.6 The content is fetched from a free-tier backend that sleeps

A checklist measures TTFB of the HTML — 1.05 s here, acceptable. It does not model that the HTML
is empty and the *content* requires five more round trips to a service that cold-starts in
**6.86 s** (measured).

This also creates a build-time hazard the checklist can't anticipate: once prerendering is added,
**the build depends on Render being awake**. A cold or failed API during a Vercel build would bake
skeletons — or the literal string *"fatal: failed to load portfolio data from the backend"* — into
the published HTML, and it would look fine in a browser because hydration repairs it. That is why
rule **B3** exists and why it is non-negotiable.

### 3.7 Fixing the database will not fix the site

`00-RECON.md` §0.2 documents two competing content sources. The footer, navbar, status bar, contact
section, contribution heatmap and terminal all read **hardcoded** `frontend/src/data/profile.ts`,
not the API. So correcting the admin panel leaves *"B.Tech CSE (Data Science) Student"* and the
Twitter link rendering in the footer of every page.

Any audit that treats "the CMS is the source of truth" as given — which is the normal assumption —
misses this entirely and produces a fix that appears to work while half the surface stays stale.

### 3.8 The name is not just contested — it's contested by better-positioned people

"Common name" understates it. The field includes a **Software Engineer at Google** using Spring
Boot, a **Senior Backend Developer using C# and Spring Boot**, two developers **in Kolhapur**, a
frontend developer **in Pune**, and an **"AI & LLM Engineer and Full Stack Developer"** whose
portfolio already ranks #1 for "Omkar Jadhav portfolio developer". Wikipedia carries knowledge
panels for three public figures surnamed Jadhav.

The honest read: **"Omkar Jadhav" alone is not winnable in the near term.** The reachable targets
are the qualified variants — `Omkar Jadhav Nonstop IO`, `Omkar Jadhav KIT Kolhapur`,
`Omkar Jadhav SDE-I` — and above all **"Omkar Jayvant Jadhav"**, his full legal name, which has
near-zero competition and currently appears **nowhere on his site**. A checklist that says "target
your name" without measuring the field would send him at the hardest query first and produce
nothing for months.

### 3.9 The terminal aesthetic is quietly costing keyword signal

Not a defect — it is the site's best quality, and it should survive. But `"git checkout projects"`,
`"git show --contact"` and `"git export --resume"` (`HeroSection.tsx:118-137`) are the primary CTAs,
and as anchor text they carry zero keyword signal. The `<h1>` is `# omkarjadhav` + the name —
the strongest heading on the site spent on a handle.

This is solvable without touching the design: keep the visible terminal text, add accessible names
and supporting copy that carry the real semantics. Worth naming because "improve your anchor text"
would otherwise read as "remove the thing that makes the site memorable", and that would be the
wrong trade.

---

## 4. Architecture verdict

**Is the architecture fundamentally wrong for search? Yes — as currently configured.**

Plainly: a client-rendered SPA that fetches every fact at runtime from a cold-starting free-tier
API, serving a 2,392-byte empty shell to every URL, cannot rank for anything, and is *completely*
invisible to the AI crawlers that represent this site's best realistic use.

**But it does not need replacing.** The application architecture is sound — the Spring Boot backend,
the admin panel, the LLM router, the MCP server and the vault are all genuinely good work, and none
of them is the problem. The problem is that the **delivery** layer produces no HTML.

Cost comparison, stated honestly:

| Option | Work | SEO outcome | Cost |
|---|---|---|---|
| **(a) Build-time prerender** ← chosen | Post-build headless-browser pass over public routes; per-route head injection; guard rails (B3) | Resolves B1, B6, D7, D8, I7, G1, F1 — the great majority of Critical findings | **~1 wave.** No subsystem touched |
| (b) Static content, dynamic admin | Move public content to versioned repo files; prerender from those | Same, plus build-time independence from Render | ~1.5 waves; editing content becomes a git commit |
| (c) Migrate to a server-rendering framework | Rewrite the frontend | Marginally best | **Weeks.** And the obvious candidate is Next.js, a withdrawn claim |

**Option (a) buys ~90% of the available SEO outcome for ~5% of option (c)'s cost.** It is the right
call, and it is the one on record.

The residual risk is B3: prerendering against a live free-tier API means the build can silently
publish garbage. The guard rail is mandatory, not optional.

---

## 5. Critical findings, consolidated

Ordered by fix-first sequence, not severity — several Criticals are blocked behind others.

| # | Finding | Rules | Wave |
|---|---|---|---|
| 1 | No hardcoded domain may survive; `VITE_SITE_URL` + build assertion | K10 | **0** |
| 2 | Canonical / OG / sitemap / robots point at a 404 domain | A1, A2, A4, H1 | **1** |
| 3 | Résumé PDF contradicts every fact we are about to publish | K15, I6 | **1** ⚠️ *needs his action* |
| 4 | "Student" / "seeking" / "intern" in live copy, metadata and OG pixels | D11, I6 | **1** |
| 5 | Next.js live as a claimed skill; FastAPI/RAG in copy | E3, D11 | **1** |
| 6 | Fabricated star/fork/commit metrics in production | K11 | **1** |
| 7 | Three conflicting versions of his current job | I6, E6 | **1** |
| 8 | X/Twitter link + `twitter:creator` for an account he doesn't own | K9, H2, E2 | **1** |
| 9 | Crawlers receive zero content on every route | B1, B6, I7, D7, D8, G1 | **1** |
| 10 | Every unknown URL returns 200 (soft 404) | A5, K12 | **1** |
| 11 | Three published GitHub links are 404s | K5 | **1** |
| 12 | No JSON-LD of any kind — the entity layer is absent | E1–E14 | **2** |
| 13 | No canonical disambiguating statement; legal name absent from the site | I5 | **2** |
| 14 | GitHub profile: no README, no website link, stale "Intern"/"Final-year" bio | J1, J2, J4 | **5** |
| 15 | LinkedIn URL resolves to a different person | E2, J5 | 🚫 **blocked** |

---

**Next:** Phase 3 — [`03-KEYWORD-MAP.md`](./03-KEYWORD-MAP.md).
