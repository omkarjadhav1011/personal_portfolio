# 00 — RECON

**Subject:** Omkar Jayvant Jadhav
**Production URL:** `https://jadhavomkar.vercel.app/`
**Branch:** `seo/overhaul` (cut from `dev` @ `717df86`)
**Date of recon:** 2026-09-19
**Code changes made in this phase:** none. This document is observation only.

Every claim below cites a `file:line`, a command output, or a URL fetched during this phase.
Where evidence contradicts the Subject Profile I supplied-by-brief, I have flagged it rather than
silently resolving it — see [§9 Open questions](#9-open-questions--blockers).

---

## 0.1 Stack detection

I assumed nothing and read the configs.

| Property | Finding | Evidence |
|---|---|---|
| Frontend framework | **React 18.3.1 + Vite 5.4 + TypeScript 5.5.4** — *not* Next.js | `frontend/package.json:22-24,41,43` |
| Routing | `react-router-dom` 6.30.4, `createBrowserRouter` | `frontend/package.json:25`, `frontend/src/router.tsx:2,32` |
| Styling | Tailwind CSS 3.4.7 + PostCSS, dark-only terminal theme | `frontend/package.json:40`, `frontend/tailwind.config.ts` |
| Animation | framer-motion 11.18.2 | `frontend/package.json:16` |
| Server state | TanStack React Query 5.100.14 | `frontend/package.json:14` |
| Build output | `frontend/dist` | `frontend/vercel.json:4` |
| Build command | `tsc --noEmit && vite build` (typecheck is the lint step) | `frontend/package.json:8` |
| Backend | **Spring Boot 3.5.15, Java 21, Maven** | `backend/pom.xml:parent version 3.5.15`, `CLAUDE.md` |
| Backend host | Render free tier, `https://portfolio-backend-sfzm.onrender.com` | extracted from production bundle `/assets/index-D16nanWQ.js` |
| Database | PostgreSQL + Flyway (`V1`…`V14`), pgvector at `V9` | `CLAUDE.md` |
| Deploy config | `frontend/vercel.json` — framework `vite`, SPA rewrite | `frontend/vercel.json:5-8` |

### Rendering mode per route — **client-only, 100%, no exceptions**

There is no SSR, no SSG, no prerender step, and no head-management library. I grepped for one:

```
$ grep -rn "react-helmet|Helmet" frontend/src   →  no matches
```

`<head>` content is produced in exactly two places:

1. **`frontend/index.html`** — a single static `<head>` shipped byte-identically to every route.
2. **`frontend/src/hooks/useDocumentTitle.ts:8`** — sets `document.title` in a `useEffect`, i.e.
   after hydration. It mutates only the title; it cannot emit canonicals, descriptions, OG tags,
   or JSON-LD.

The consequence is structural: **every URL on this site serves the same `<title>`, the same
`<meta name="description">`, and the same canonical.** Route-level metadata does not exist and
cannot exist in the current architecture.

`vite.config.ts:20` carries the explanation — *"Mirrors the `@/...` import alias from the original
Next.js app."* This codebase was **ported off Next.js to Vite**. The SEO surface did not survive
the port: `next/head`, `generateMetadata`, and server rendering all went away, and the static
`index.html` is what replaced them.

---

## 0.2 Route and content inventory

### Routes (`frontend/src/router.tsx`)

**Public, indexable-intent:**

| Route | Component | Lazy | Notes |
|---|---|---|---|
| `/` | `pages/Home.tsx` | no | Single-page scroll: hero, about, skills, skills-diff, projects, experience, contact |
| `/projects/:slug` | `pages/ProjectDetail.tsx` | yes | One per project — currently 4 slugs exist in the live DB |
| `/recruiter` | `pages/RecruiterPage.tsx` | yes | JD fit-match tool |
| `/mcp` | `pages/McpPage.tsx` | yes | MCP server documentation page |
| `/scratch` | `pages/ScratchProjects.tsx` | yes | **Dev scaffold left in the router** — see §5 |
| `*` | `routes/NotFound.tsx` | no | Renders a 404 UI but **serves HTTP 200** — see §3 |

**Non-public (must be excluded from indexing):**
`/admin/login`, `/admin/oauth/callback`, `/admin/mfa/verify`, `/admin`, `/admin/projects`,
`/admin/experience`, `/admin/skills`, `/admin/profile`, `/admin/drive`, `/admin/messages`,
`/admin/mfa/setup`.

There are **no** `/about`, `/skills`, `/experience`, `/education`, `/resume`, `/contact`, or
`/blog` routes. The homepage carries all of that content as `#hero`, `#about`, `#skills`,
`#projects`, `#experience`, `#contact` anchors on one URL
(`grep -oE 'id="[a-z-]+"' frontend/src/components/sections/*.tsx`).

### Content sources — **two competing sources of truth**

This is the most consequential structural finding after the rendering problem.

**Source A — the backend database (runtime fetch).** `pages/Home.tsx:60-64` calls `useProfile`,
`useDomainProjects`, `useSkillBranches`, `useSkillDiff`, `useExperience`, which hit
`/api/profile`, `/api/projects`, `/api/skills/branches`, `/api/skills/diff`, `/api/experience`
(`frontend/src/api/*.ts`).

**Source B — hardcoded TypeScript files (`frontend/src/data/*.ts`), still imported and rendered:**

```
frontend/src/components/layout/Footer.tsx:1      import { profile as staticProfile } from "@/data/profile";
frontend/src/components/layout/Navbar.tsx:8      import { profile as staticProfile } from "@/data/profile";
frontend/src/components/layout/StatusBar.tsx:4   import { profile as staticProfile } from "@/data/profile";
frontend/src/components/sections/ContactSection.tsx:11
frontend/src/components/ui/ContributionHeatmap.tsx:4
frontend/src/components/recruiter/AnalysisProgress.tsx:6
frontend/src/pages/RecruiterPage.tsx:5
frontend/src/hooks/useTerminal.ts:4-7            (profile, projects, skills, experience)
```

**Implication:** correcting the database does **not** correct the site. The footer, navbar, status
bar, contact section and terminal command output all read the stale hardcoded copy in
`frontend/src/data/profile.ts` — which still says *"B.Tech CSE (Data Science) Student"*
(`:6`), *"Open to internships & collaborations"* (`:14`), and links a Twitter account
(`:27-30`). Any factual fix has to be applied in **both** places, or one source removed.

### Images

| Asset | Format | Bytes | Notes |
|---|---|---|---|
| `frontend/public/opengraph-image.png` | PNG | 48,257 | The **only** image in the repo. No AVIF/WebP. |
| Avatar (`/api/profile/avatar`) | JPEG | 50,902 | Served from the **Render backend origin**, not Vercel's CDN |
| Resume (`/api/profile/resume`) | PDF | 90,482 | `Omkar_Jadhav_Ace.pdf` — see §5, this is a live stale document |

There is **no favicon, no `site.webmanifest`, and no apple-touch-icon**:
`grep -c "noscript|rel=\"icon\"|manifest" frontend/index.html` → **0**.

The OG image is **rasterized from a build script**, `frontend/scripts/generate-og.mjs`, which bakes
three stale strings into pixels (`:7-10,31`): `HEADLINE = "B.Tech CSE (Data Science) Student…"`,
`STATUS = "Open to internships & collaborations"`, and a footer reading `omkarjadhav.vercel.app`.
Because these are pixels, no metadata edit fixes them — the PNG must be regenerated.

### JS bundle (production, measured)

```
/assets/index-D16nanWQ.js    535,979 bytes raw  /  172,873 bytes gzipped
/assets/index-By7UBeUw.css    50,445 bytes raw
```

---

## 0.3 The critical rendering check

I fetched the raw HTML the server returns — not the hydrated DOM.

```
$ curl -sSL -D - https://jadhavomkar.vercel.app/
HTTP/1.1 200 OK
Content-Length: 2392
Content-Type: text/html; charset=utf-8
Server: Vercel
X-Vercel-Cache: MISS
```

**The entire `<body>` a crawler receives is:**

```html
<body class="relative">
  <div id="root"></div>
</body>
```

**A crawler with JavaScript disabled sees zero words about Omkar Jadhav.** Not his name in a
heading, not his employer, not a skill, not a project, not his education. The 2,392 bytes are
`<head>` boilerplate and two asset tags. There is no `<noscript>` fallback.

### Why this is worse than the usual "SPA is bad for SEO"

Google does render JavaScript, so a client-rendered SPA is normally *handicapped*, not *invisible*.
Here the handicap compounds three ways:

1. **The content is not in the bundle — it is behind a network call.** `Home.tsx:70-80` renders
   skeleton components while any query is pending, and `:83-96` renders the literal string
   `"fatal: failed to load portfolio data from the backend."` if any query fails. So the rendered
   DOM depends on a successful cross-origin API round trip completing inside the renderer's budget.

2. **That API is on Render's free tier and cold-starts.** Measured, first request after idle:

   ```
   GET /api/profile     → 200 in 6.86 s
   GET /api/skills/branches, /api/experience, /api/projects → same cold window
   ```

   Google's renderer does not guarantee it will wait ~7s for five chained XHRs. When it doesn't,
   the indexed content is the skeleton — or the word "fatal".

3. **Every fact is gated behind *five* parallel queries, all of which must succeed.**
   `Home.tsx:66-68` — if *any* of the five is still pending, the whole page is skeletons; if any
   returns falsy, the whole page is the fatal-error string. There is no partial render.

**Verdict: this is the single largest ranking blocker on the site, and it outranks every other
finding in this document.** You selected **prerender-at-build-time (option a)** to fix it; that
decision is recorded and will be planned as Wave 1.

### Status codes — every unknown URL is a soft 404

`frontend/vercel.json:7` rewrites `/(.*)` → `/index.html` unconditionally.

```
$ curl -o /dev/null -w "%{http_code}" https://jadhavomkar.vercel.app/this-page-does-not-exist-12345
200
$ curl … /recruiter   → 200, 2392 bytes
$ curl … /mcp         → 200, 2392 bytes
$ curl … /projects/git-portfolio → 200, 2392 bytes
$ curl … /scratch     → 200, 2392 bytes
```

Note that all five responses are **byte-identical, 2,392 bytes**. From a crawler's perspective the
site is an unbounded number of URLs that all return HTTP 200 with the same empty document. That is
a textbook duplicate-content and crawl-budget-waste pattern, and it means `NotFound.tsx` — which
draws a nice terminal-themed 404 — never communicates 404 to a machine.

---

## 0.4 Hidden-content audit

Content reachable **only** through a JavaScript interaction is invisible to crawlers and to AI
answer engines. In the current build *all* content qualifies (§0.3), so this section records what
will *still* be interaction-gated after prerendering is in place.

| Surface | Trigger | What is gated |
|---|---|---|
| **AI assistant / chatbot** | Click the floating "Ask AI" button → opens command palette in `ai` mode | `FloatingAIButton.tsx:8` → `openInMode("ai")`; `CommandPalette.tsx:117,175,332`. The assistant can answer detailed questions about his experience, but **none of those answers exist as HTML**. |
| **Command palette** | `Ctrl+K` | `CommandPalette.tsx`; `useTerminal.ts:4-7` renders profile/projects/skills/experience as terminal output — real content, zero crawlability |
| **Recruiter fit-match** | Paste a JD on `/recruiter`, submit | `RecruiterClient.tsx` — output is per-session, correctly non-indexable |
| **Contribution heatmap** | Renders client-side from `@/data/profile` | `ContributionHeatmap.tsx:4` |
| **Project detail** | Route-level lazy chunk + API fetch | `ProjectDetail.tsx` |

**The sharpest instance:** the site's single best differentiator — an assistant that can
authoritatively answer *"who is Omkar Jadhav and what has he built?"* — is the part a search
engine and an AI crawler can least see. The facts it is grounded in live in a pgvector index
server-side, reachable only by POSTing to `/api/chat`. For the goal of *being the answer when
someone asks an AI assistant about him*, this is exactly backwards: the knowledge is there, and
it is sealed behind an interaction.

Rule to carry into the rulebook: **every fact the assistant can state must also exist as
crawlable HTML on some page.** The assistant should be a convenience layer over public content,
never the only place a fact lives.

---

## 0.5 Stale-content audit

Per the brief, these are factual errors on a live site, not style issues. All are **Critical**.

### A. Withdrawn technologies, currently live

| Location | Evidence | Content |
|---|---|---|
| **Live API — skills** | `GET /api/skills/branches` | `{"name":"Next.js","level":3,"tag":"v14"}` — a withdrawn technology published as a claimed skill, right now |
| **Live API — projects** | `GET /api/projects` | `git-portfolio` tags `["Next.js","Tailwind","Framer Motion"]`; description *"Built with **Next.js 14**, this portfolio…"* |
| `frontend/src/data/skills.ts:13` | static | `{ name: "Next.js", level: 3, tag: "v14", icon: "▲" }` |
| `frontend/src/data/skills.ts:59` | static | `{ type: "added", name: "Next.js 14 App Router", note: "used in this portfolio" }` |
| `frontend/src/data/skills.ts:62` | static | `{ type: "modified", name: "Python", note: "leveling up: async + **FastAPI**" }` |
| `frontend/src/data/projects.ts:17,23` | static | `tags: ["Next.js", …]`, *"Built with Next.js 14…"* |
| `frontend/src/components/sections/AboutSection.tsx:16` | static | `{ name: "Next.js", glyph: "▲", tint: "#ffffff" }` in the visible tech row |
| `frontend/src/components/admin/TechPicksEditor.tsx:40,55` | admin UI | `Next.js`, `FastAPI` offered as pickable tech |
| `frontend/src/components/recruiter/JobInputForm.tsx:21` | placeholder | *"Nice to have: LLM/**RAG** integrations…"* |
| `backend/.../seed/DataSeeder.java:119` | seeder | re-seeds the Next.js project row on every restart |

Non-user-facing and therefore **out of scope for removal** (implementation comments describing the
port, not skill claims): `vite.config.ts:20`, `ContactSection.tsx:31`, `lib/actions/contact.ts:9`,
`styles/globals.css:11`, `MainLayout.tsx` docblock.

### B. Student / intern / job-seeker language, currently live

| Location | Content |
|---|---|
| `frontend/index.html:7` | `<title>Omkar Jadhav — B.Tech CSE (Data Science) **Student** & Full-Stack Developer</title>` |
| `frontend/index.html:10` | description: *"…**Student** & Full-Stack Developer. **Open to internships** & collaborations."* |
| `frontend/index.html:21,32` | same string in `og:description` and `twitter:description` |
| **Live API** `/api/profile` | `headline:` *"B.Tech CSE (Data Science) **Student** & Full-Stack Developer"* |
| **Live API** `/api/profile` | `currentStatus:` *"Open to internships & collaborations"*, `availableForWork: true` |
| **Live API** `/api/profile` | `bio:` *"**Currently pursuing** B.Tech in Computer Science (Data Science) at KIT Kolhapur…"* |
| `frontend/src/data/profile.ts:6,14` | same two strings, static |
| `frontend/scripts/generate-og.mjs:9,10` | baked into the OG image pixels |
| `frontend/src/components/admin/ProfileClient.tsx:391` | placeholder `"Full-Stack Developer Intern"` (admin-only, low impact) |

### C. Employment facts — **three contradictory versions are live simultaneously**

| Source | Title | Employer | Start | Location |
|---|---|---|---|---|
| Live `/api/profile` → `currentRole` | Full-Stack Developer **Intern** | "NonStop io Technologies" | **Mar 2024** | "Pune, India · **Hybrid**" |
| Live `/api/experience` | **SDE-I** | "NonstopIO" | **Aug 2026** | — |
| `frontend/src/data/profile.ts:50-56` | Full-Stack Developer **Intern** | "NonStop io Technologies" | **Mar 2024** | "Pune, India · Hybrid" |
| **Confirmed truth (your answer #6)** | Software Developer Intern → **SDE-I** | Nonstop IO Technologies | **2 Feb 2026** → SDE-I **Aug 2026** | Kharadi, Pune — **on-site** |

`Mar 2024` predates the real start by ~23 months. The employer name is spelled three ways
("NonStop io Technologies", "NonstopIO", "Nonstop IO Technologies") — entity-resolution poison
when the goal is to associate him with one employer node.

### D. Fabricated demo data on a live production site

`render.yaml:50` sets `SEED_DEMO_DATA: "true"`. `DataSeeder.java:113-143` therefore populates the
production database with invented engagement metrics:

```
git-portfolio        stars 12, forks 3, commits 47, lastCommit "just now"
dev-mobiles          stars  8, forks 2, commits 84
crop-recommendation  stars 19, forks 5, commits 62
snapsktch            stars 14, forks 3, commits 38
```

Verified against the real GitHub account via the API — none of these repos have anything like
these counts, and two of them do not exist at all (below). **Publishing invented star/fork/commit
counts is a fabricated-metrics problem, not an SEO problem.** They must go regardless of ranking.

You approved turning `SEED_DEMO_DATA` off (answer #3). Until that lands, any row deleted through
the admin panel is re-created on the next Render restart — and the free tier restarts often — so
no content fix will stick before this change.

### E. Broken outbound links, currently live

Verified against `GET https://api.github.com/users/omkarjadhav1011/repos?per_page=100`:

| Link published on the site | HTTP | Reality |
|---|---|---|
| `github.com/omkarjadhav1011/dev-mobiles` | **404** | real repo is `Mobile_Shop` |
| `github.com/omkarjadhav1011/git-portfolio` | **404** | real repo is `personal_portfolio` |
| `github.com/omkarjadhav1011/snapsktch` | **404** | no such repo in the account |
| `github.com/omkarjadhav1011/crop-recommendation` | **200** | **exists** — contradicts your answer #2, see §9 |
| `https://omkarjadhav.vercel.app` (`projects.ts:18`, seeded `liveUrl`) | **404** | dead domain, see §0.6 |

### F. The old phone number is live on the site — inside the resume PDF

The brief asked me to grep for `7378729692`. It is not in the source tree. **It is in the PDF the
site serves at `/api/profile/resume`** (90,482 bytes, `Omkar_Jadhav_Ace.pdf`, linked from the hero
CTA at `HeroSection.tsx:128-137`). Extracted text:

> Omkar Jayvant Jadhav — Kolhapur, India | **+91 7378729692** | jadhavomkar101103@gmail.com

Since you chose **not to publish a phone number at all (answer #7)**, note that one is being
published today — just not from a file I can edit. Google indexes PDFs and AI crawlers read them.

**The same PDF also carries every other problem in this section**, all of it live and downloadable:

- *"**Final-year** B.Tech Computer Science (Data Science) **student**…"*
- *"Currently **interning** as a Software Developer (C#, NestJS)…"*
- *"…**Seeking a role** to contribute to production AI systems…"*
- Frontend skills: *"React, **Next.js**, HTML5, CSS3"*
- Backend skills: *"Spring Boot, NestJS, **FastAPI**, REST APIs, PHP"*
- AI skills: *"Gemini API, Hugging Face API, **RAG pipelines**, Prompt Engineering, **ChromaDB (Vector DB)**"*
- Databases: *"PostgreSQL, MySQL, **ChromaDB**"*
- Project 1: *"Personal Portfolio with **RAG-based Assistant** (**Next.js, FastAPI**, Gemini API, **ChromaDB**) — **In Progress**"*

This PDF is the origin of the outdated resume the brief described. It is the highest-fidelity
statement of his identity currently reachable on the internet, and every fact in it is wrong or
withdrawn. **A replacement PDF is required before anything else in this engagement matters** —
schema and copy that disagree with a downloadable resume on the same domain will lose to the PDF.

### G. Placeholder / template content

| Location | Content |
|---|---|
| `/scratch` route | `ScratchProjects.tsx:8-11` — *"Scratch page (Phases 7.2–7.4) — proves the API client… Replaced by real pages later."* A dev scaffold publicly routable and returning 200 |
| `data/profile.ts:27-30` + live API + `DataSeeder.java:94` | `https://twitter.com/omkarjadhav` — **you confirmed you have no X/Twitter account (answer #9)** |
| `index.html:30` | `<meta name="twitter:creator" content="@omkarjadhav">` — same |
| `data/profile.ts:35-45` | `funFacts` / `stash` — template filler incl. *"My Hugging Face API calls cost more than my monthly coffee budget"* |
| Live `/api/experience` | *"Web Developer Intern, Dnyanda Solutions Pvt. Ltd., Jul–Sep 2022"* — **you confirmed removal (answer #2)** |
| Live `/api/experience` | B.Tech entry says *"SGPA: 8.0/10 — **Pursuing** specialization"*; confirmed truth is **80%, graduated** |
| `.claude/settings.local.json:58-60` | references `portfolio-backend-9gq3.onrender.com`, a stale backend host (dev tooling only, no user impact) |

No lorem ipsum and no `#` placeholder links were found.

---

## 0.6 Live infrastructure fetch

### Response headers (`GET /`)

```
HTTP/1.1 200 OK
Cache-Control: public, max-age=0, must-revalidate
Content-Security-Policy: default-src 'self'; script-src 'self'; …
Referrer-Policy: no-referrer
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Server: Vercel
```

**`X-Robots-Tag` is absent — good.** The production deployment is *not* carrying an accidental
`noindex`. Security headers are well configured (`frontend/vercel.json:10-23`).

One note for later: `Referrer-Policy: no-referrer` is stricter than needed and will blank out
referrer data in any analytics you add in Phase 5. Not an indexing problem; flagging it so it
isn't a surprise.

### `/robots.txt` — **200, and it points at a dead domain**

```
User-agent: *
Allow: /

Sitemap: https://omkarjadhav.vercel.app/sitemap.xml
```

No AI-crawler directives (you chose to allow all — answer #8 — which still needs to be *stated*
explicitly, since an absent policy and an allow policy are not the same signal to operators).

### `/sitemap.xml` — **200, and every entry is wrong twice over**

All five `<loc>` values point at `https://omkarjadhav.vercel.app`. Three of the five are
**fragment URLs** (`/#about`, `/#projects`, `/#experience`, `/#contact`). Fragments are not
separate resources; search engines discard everything from `#` onward, so the sitemap declares one
real URL four times, on a domain that 404s. `lastmod` is frozen at `2026-06-01`.

### Missing entirely — all return the SPA shell, HTTP 200

| Path | Status | Returns |
|---|---|---|
| `/llms.txt` | 200 | `text/html`, 2,392 bytes — the SPA shell |
| `/humans.txt` | 200 | same |
| `/site.webmanifest` | 200 | same |
| `/favicon.ico` | 200 | same — **`text/html`, not an icon** |

The catch-all rewrite means these do not 404; they serve HTML under a non-HTML filename. A browser
requesting `/favicon.ico` gets an HTML document with `Content-Type: text/html`.

### `<head>` of every route — identical

Confirmed byte-identical across `/`, `/recruiter`, `/mcp`, `/projects/git-portfolio`, `/scratch`,
and a nonexistent URL. Contents (`frontend/index.html`):

- `<html lang="en">` ✅ present
- `<title>` — stale (§5B), and the **same on all routes**
- `<meta name="description">` — stale, same on all routes
- `<link rel="canonical" href="https://omkarjadhav.vercel.app">` — **dead domain, all routes**
- OG: `type`, `url`, `site_name`, `title`, `description`, `image` (+`width`/`height`)
- Twitter: `card`, `title`, `description`, `creator`, `image`
- Google Fonts: `preconnect` ×2 + a **render-blocking stylesheet** for JetBrains Mono
  (`display=swap` is correctly set)
- **No** `rel="icon"`, **no** manifest, **no** JSON-LD, **no** `noscript`

### 🔴 The canonical points at a domain that does not exist

This is the highest-severity finding in the document, so it gets its own statement.

```
$ curl -sSL -o /dev/null -w "%{http_code}" https://omkarjadhav.vercel.app/
404      body: "The page could not be found  NOT_FOUND"
```

The live site is **`jadhavomkar`**.vercel.app. Every canonical, `og:url`, `og:image`,
`twitter:image`, all five sitemap `<loc>`s, and the `robots.txt` `Sitemap:` line name
**`omkarjadhav`**.vercel.app — the two halves of the name are transposed, and that host returns
404.

A self-referential canonical is how a page tells Google *"this is my real address."* This site
tells Google its real address is a page that does not exist. Combined with §0.3 (no content in the
HTML), it is a complete explanation for §0.7 (nothing indexed). Of everything in this document,
this is the cheapest to fix and the most expensive to leave.

---

## 0.7 Indexation and competition check

### Indexation: zero

`site:jadhavomkar.vercel.app` returns **no pages from the domain** — the engine returned unrelated
Vercel-hosted sites and Wikipedia pages for the surname "Jadhav". Combined with §0.6, expected.

Baseline to state plainly: **the site currently has no organic search presence of any kind.** Every
number in Phase 5 starts from zero, and that is the honest starting point.

### Who ranks for "Omkar Jadhav" today — the competitor set

Entity disambiguation is the central technical problem of this engagement, and this is the field
he is competing in. **At least fifteen distinct people named Omkar Jadhav work in technology**, and
several are positioned almost identically to him.

**Tier 1 — direct positional collisions (portfolio sites, same claims):**

| Competitor | Positioning | Why dangerous |
|---|---|---|
| [omkarjadhav0456.netlify.app](https://omkarjadhav0456.netlify.app/) | *"AI & LLM Engineer and Full Stack Developer"*, Java full-stack, GenAI/LLM/prompt engineering | **The most dangerous single competitor.** Ranks #1 for "Omkar Jadhav portfolio developer", and his positioning is nearly a word-for-word match. |
| [omkarkjadhav.netlify.app](https://omkarkjadhav.netlify.app/) | Frontend dev, **Pune**, React/TypeScript/Redux | Same name, same city, same stack family |
| [omkar118.github.io/omkarsite](https://omkar118.github.io/omkarsite/) | Backend/data engineer, 5+ yrs | Older domain, github.io authority |
| [portfolio-test-theta.vercel.app](https://portfolio-test-theta.vercel.app/) | "Omkar Jadhav - Portfolio" | Also on vercel.app |

**Tier 2 — high-authority LinkedIn profiles:**

| Competitor | Positioning |
|---|---|
| [omkar-jadhav-7922aba4](https://www.linkedin.com/in/omkar-jadhav-7922aba4/) | **Software Engineer at Google** — Spring Boot |
| [omkar-jadhav07](https://www.linkedin.com/in/omkar-jadhav07/) | Senior SWE, LTIMindtree, ex-TCS |
| [omkar-jadhav09](https://www.linkedin.com/in/omkar-jadhav09/) | Java Developer @1GEN, **Kolhapur** |
| [omkar-jadhav-768660248](https://www.linkedin.com/in/omkar-jadhav-768660248/) | Full Stack @ Sceniuz, **Shivaji University, Kolhapur** |
| [in/omkar-jadhav](https://in.linkedin.com/in/omkar-jadhav) | Testing/Selenium — **owns the clean `/in/omkar-jadhav` slug** |
| [omkarjjadhav](https://www.linkedin.com/in/omkarjjadhav/) | NXP Semiconductors |
| [omkar-jadhav-00ba872b](https://www.linkedin.com/in/omkar-jadhav-00ba872b/) | Senior Backend Dev, Shory — Spring Boot, **C#** |

**Tier 3 — non-tech entities diluting the name:** Behance designers
([JadhavOmkar](https://www.behance.net/JadhavOmkar?locale=en_US),
[omkarjadhav1](https://www.behance.net/omkarjadhav1)), [omcardesign.com](https://www.omcardesign.com/),
GitHub user `omjego`, plus Wikipedia entities for the surname
([Ravi Jadhav](https://en.wikipedia.org/wiki/Ravi_Jadhav),
[Sanjay Jadhav](https://en.wikipedia.org/wiki/Sanjay_Jadhav),
[Bhaskar Jadhav](https://en.wikipedia.org/wiki/Bhaskar_Jadhav)) that hold knowledge panels and
absorb generic "Jadhav" queries.

### What this means strategically

Two competitors are in **Kolhapur**. One is in **Pune**. One claims **AI/LLM + full-stack**. One
uses **Spring Boot at Google**. One uses **C# + Spring Boot**. Every individual attribute he might
rank on is already claimed by another Omkar Jadhav.

**No single attribute disambiguates him. The defensible position is the intersection:**

> Omkar Jadhav · SDE-I at **Nonstop IO Technologies** (Kharadi, Pune) · **KIT's College of
> Engineering, Kolhapur** B.Tech CSE Data Science 2026 · **C# / NestJS / SQL** on an enterprise
> reporting product

No other Omkar Jadhav holds that combination. Nothing on the live site currently states it —
today the site says he is a student in Kolhapur seeking internships, which places him *closer* to
his competitors rather than further from them.

A second, harder truth: **"Omkar Jadhav" alone is probably not winnable in the short term.** A
Google engineer and several 5+ year seniors hold that query. The reachable targets are
`Omkar Jadhav Nonstop IO`, `Omkar Jadhav KIT Kolhapur`, `Omkar Jadhav SDE-I`, and
`Omkar Jayvant Jadhav` (his full legal name — near-zero competition, and currently stated
**nowhere** on the site; the only place it appears online is inside the outdated resume PDF).
Phase 3 will quantify this.

### A note on `vercel.app` and inherited authority

`vercel.app` is on the [Public Suffix List](https://publicsuffix.org/), so it is treated as a
registrable-domain boundary: `jadhavomkar.vercel.app` inherits **no** authority from `vercel.com`
or from any other `*.vercel.app` site. He is building from zero either way.

**The practical consequence for the migration: there is nothing to lose.** Moving to a custom
domain will not forfeit accumulated authority, because none has accumulated and none could have.

---

## 0.8 Domain-migration readiness report

The custom-domain purchase is treated as certain. Hard Rule 1 requires that the move cost exactly
one configuration change.

### Every place a domain string appears today

| # | Location | Current value | Disposition |
|---|---|---|---|
| 1 | `frontend/index.html:12` | canonical → `omkarjadhav.vercel.app` | env-derive |
| 2 | `frontend/index.html:16` | `og:url` | env-derive |
| 3 | `frontend/index.html:23` | `og:image` | env-derive |
| 4 | `frontend/index.html:35` | `twitter:image` | env-derive |
| 5 | `frontend/public/robots.txt:4` | `Sitemap:` | generate at build |
| 6–10 | `frontend/public/sitemap.xml:4,10,16,22,28` | 5 × `<loc>` | generate at build |
| 11 | `frontend/scripts/generate-og.mjs:31` | `${HANDLE}.vercel.app` **rendered into PNG pixels** | derive from env at generation |
| 12 | `frontend/src/data/projects.ts:18` | `liveUrl: "https://omkarjadhav.vercel.app"` | env-derive |
| 13 | `backend/src/main/java/com/portfolio/seed/DataSeeder.java:119` | same URL, re-seeded on boot | delete with seed data |
| 14 | `frontend/vercel.json:20` | CSP `https://*.onrender.com` | backend origin, not site URL — review separately |
| 15 | `render.yaml:56` | `CORS_ALLOWED_ORIGIN` (dashboard-set, `sync: false`) | **migration-day env change** |
| 16 | `render.yaml:143` | `APP_FRONTEND_URL` | **migration-day env change** |
| 17 | `.claude/settings.local.json:58-60` | stale backend host | dev tooling — out of scope |

**Scope decision (your answer #11 — "decide yourself"):** I am extending the single-source-of-truth
rule to the backend. `CORS_ALLOWED_ORIGIN` and `APP_FRONTEND_URL` are domain strings that must
change on migration day, so leaving them undocumented would break the one-value promise in
practice even if the frontend grep came back clean. They are dashboard-managed (`sync: false`), so
the deliverable is a documented runbook step plus comments in `render.yaml`, not a code change.
I will not deploy or touch the Render dashboard.

### Work required to make the codebase domain-agnostic (Wave 0)

1. Introduce **`VITE_SITE_URL`** (Vite's public env convention; the brief's `NEXT_PUBLIC_SITE_URL`
   equivalent). Single source of truth.
2. Add `frontend/src/lib/site.ts` exporting a validated `SITE_URL` plus an `absoluteUrl(path)`
   helper. Every absolute URL in the app routes through it.
3. **Build-time assertion that fails loudly** when `VITE_SITE_URL` is unset or not a valid absolute
   URL — a Vite plugin that throws in `config`/`buildStart`, so `npm run build` exits non-zero.
   This is what makes the rule enforceable rather than aspirational.
4. `index.html` cannot read env vars — it is static. Use Vite's HTML transform hook (or
   `vite-plugin-html`) to inject `%VITE_SITE_URL%` placeholders at build. *(Once prerendering
   lands in Wave 1, per-route head generation replaces this mechanism; Wave 0 must not paint us
   into a corner that Wave 1 has to undo.)*
5. Generate `robots.txt` and `sitemap.xml` **at build time** from `VITE_SITE_URL` + the route
   manifest, and delete the hand-written files from `public/`. A checked-in sitemap with literal
   URLs is itself a violation of Hard Rule 1.
6. Parameterize `generate-og.mjs` on the same variable.
7. Set `VITE_SITE_URL=https://jadhavomkar.vercel.app` in Vercel (all three environments) and in a
   local `.env.example`; document in `README.md`.
8. Add `render.yaml` comments marking `CORS_ALLOWED_ORIGIN` / `APP_FRONTEND_URL` as
   migration-day values.
9. **Prove it:** `grep -ri "vercel\.app"` across `frontend/src`, `frontend/public`,
   `frontend/index.html`, and `frontend/scripts` must return nothing outside the env plumbing.

### Migration-day runbook

Execute top to bottom. Expect 30–45 minutes plus DNS propagation.

**Before the switch**
1. Buy the domain. Prefer a name that reinforces the entity: `omkarjadhav.dev` or
   `omkarjayvantjadhav.com` (the legal-name variant has near-zero competition).
2. Vercel → project → **Settings → Domains → Add** → enter the apex and `www`.
3. At the registrar, add the DNS records Vercel shows (A record for apex, CNAME for `www`).
   Wait for Vercel to show **Valid Configuration** and issue the TLS certificate.

**The switch**
4. Vercel → Settings → Domains → set the custom domain as **Primary**.
5. On `jadhavomkar.vercel.app`: **Edit → Redirect to** the custom domain. Vercel issues a **308
   Permanent Redirect**. *Do not delete the subdomain* — deleting it drops the redirect and
   strands every existing link.
6. Vercel → Settings → Environment Variables → change **`VITE_SITE_URL`** to the new origin in
   Production, Preview, and Development. **This is the one value.**
7. Render → `portfolio-backend` → Environment → update **`CORS_ALLOWED_ORIGIN`** and
   **`APP_FRONTEND_URL`** to the new origin. Save (triggers a backend redeploy). *Skipping this
   breaks every API call from the new domain — the site will render its "fatal" state.*
8. Vercel → Deployments → **Redeploy** production (do **not** use the build cache).

**Verify — do not skip**
9. `curl -sI https://jadhavomkar.vercel.app/` → expect **308** with `Location:` the new domain.
10. `curl -sL https://<new-domain>/ | grep canonical` → new domain.
11. `curl -s https://<new-domain>/sitemap.xml` and `/robots.txt` → new domain throughout.
12. `curl -s https://<new-domain>/ | grep '"@id"'` → every JSON-LD `@id` on the new domain.
13. Load the site; confirm content renders (proves step 7 worked).
14. `grep -r "vercel.app"` the repo → only the redirect note.

**Search infrastructure**
15. Google Search Console → add the new domain as a **Domain property** (DNS verification).
16. Submit the new sitemap. URL-inspect + request indexing for each important page.
17. GSC → old `jadhavomkar.vercel.app` property → Settings → **Change of Address** → new domain.
    *This requires the old property to still exist — a reason to create it now (Phase 5.1) rather
    than waiting for the domain.*
18. Bing Webmaster Tools → add new site → **Site Move** tool.
19. IndexNow: submit every URL on the new domain.

**External links — every one must be updated**
20. GitHub: profile website field, profile README, pinned-repo descriptions, the
    `personal_portfolio` repo's About/homepage.
21. LinkedIn: website link, Featured section, About.
22. LeetCode profile link.
23. The **resume PDF** (both the file on the site and every copy you send out).
24. Email signature.
25. Any dev.to / Hashnode / directory profiles created in Phase 5.6.

### Expected ranking movement

Setting honest expectations, and separating two cases:

**The case that applies here.** With effectively zero indexed pages and zero backlinks (§0.7),
there is no authority to transfer and therefore **no ranking to lose**. The migration is a fresh
start on a better domain. The only real cost is the ~1–3 weeks Google takes to discover, crawl,
and index the new URLs — during which the site ranks for nothing, exactly as it does today.

**The case that would apply later.** If you migrate *after* building real rankings, expect a
typical 2–6 week fluctuation with 10–30% temporary traffic movement, recovering as 308s are
consumed and Change of Address processes.

**Therefore: migrate as early as you can.** Every week of delay is a week of authority accruing to
a domain you intend to abandon, and a week during which the migration gets more expensive. This is
an argument for buying the domain *before* Wave 5 content work begins, not after.

---

## 9. Open questions / blockers

Carried forward. None block Phase 1; items 1 and 2 block **Wave 2 (entity layer)** and item 3
blocks **Wave 3**.

### 🔴 1. The LinkedIn URL in the brief belongs to a different person — **blocks Wave 2**

The Subject Profile gives `https://linkedin.com/in/omkarjadhav` as a confirmed fact. I fetched it.
It **301-redirects to `in.linkedin.com/in/omkarjadhav`, and that profile is someone else**:

> Name: Omkar Jadhav · Employer: **Dropouts Technologies LLP** · Location: Pune ·
> Education: **University of Pune, 2005–2009** · Headline: *"I love to build great products"* ·
> Services: iOS/Android/mobile app development

That is not you — the education dates alone are 18 years off. This URL is published live today in
`frontend/src/data/profile.ts:24`, in `DataSeeder.java:93`, and in the live `/api/profile` response.

**Your own resume PDF gives a different URL:** `https://www.linkedin.com/in/omkar-jadhav-st/`.
I could not verify it independently (LinkedIn returns HTTP 999 to automated fetches), but it is
the URL you put in your own resume, which is far better evidence than the brief's value.

**Why this is a hard blocker rather than a cleanup item:** `sameAs` is the single mechanism that
separates you from fifteen other Omkar Jadhavs. A wrong `sameAs` does not merely fail to help — it
instructs Google to merge your entity with a stranger's, and reciprocity is checked, so the link
will not resolve back to you. Publishing `/in/omkarjadhav` in JSON-LD would actively make the
disambiguation problem worse than doing nothing.

**Please confirm your real LinkedIn URL.** I will not put either candidate in `sameAs` until you do.

### 🟠 2. LeetCode URL still outstanding — **blocks part of Wave 2**

Not provided (question #4 unanswered). LeetCode is omitted from `sameAs` entirely; I will not
guess a username. The "210+ problems solved" claim will also stay off the site until there is a
verifiable profile to link — an unlinkable metric is exactly the kind of claim that reads as
inflation.

### 🟠 3. `crop-recommendation` — evidence contradicts your answer — **blocks Wave 3 content**

You said *"I have crop-recommendation but never put on GitHub, so it is fine to publish without a
code link."* But `https://github.com/omkarjadhav1011/crop-recommendation` returns **200** and the
GitHub API lists it (language: HTML, last updated 2026-08-20).

Two possibilities: the repo exists and you'd forgotten it, or it is a different project sharing
the name. Please confirm whether to link it.

Separately — I have **no confirmed facts** about this project. The only description that exists is
the one in `DataSeeder.java:128-135`, which is demo seed content with invented metrics
(19 stars, 5 forks, 62 commits). If it goes on the site, **please give me 2–3 sentences in your
own words** about what it actually does and what you built it with. I will not publish the seeded
description.

### 🟠 4. `dev-mobiles` — same problem, plus an internal contradiction

You said to keep it. But its only description is seeded demo text
(`DataSeeder.java:122-127`) that reads: *"built during my **internship at Dnyanda Solutions**"* —
the same employer you instructed me to delete in the same answer. Keeping the project while
deleting the employer leaves a description referencing an experience the site no longer shows.

Also: its published repo link is a **404** (`/dev-mobiles`); the real repo appears to be
`Mobile_Shop`.

**Please confirm:** (a) the correct repo URL, (b) 2–3 sentences of real description, and (c) how
to handle the Dnyanda attribution — drop the mention, or was the internship real and you only want
it off the *experience timeline*?

### 🟡 5. The Text-to-Image project's repo

Not provided (question #2, partially unanswered). I found that the seeded project **`snapsktch`**
matches your Subject Profile's Project 3 exactly (*"Python, Streamlit, Hugging Face API"*), so it
is almost certainly the same project under a name you hadn't mentioned. Its seeded repo link
`github.com/omkarjadhav1011/snapsktch` is a **404**.

Confirm: is `snapsktch` the right project name to publish, and does a repo exist for it?
Absent an answer I will publish it as **"Text-to-Image Generator"** with no code link.

### 🟡 6. The resume PDF must be replaced — needed before Wave 3

Per §5F, the live PDF contradicts every fact we are about to publish and carries the old phone
number. Schema and on-page copy that disagree with a downloadable resume on the same domain will
lose to the PDF — both for human readers and for AI assistants, which weight a résumé heavily.

I can draft replacement content from the confirmed Subject Profile, but **you must generate and
upload the final PDF** (via the admin panel's profile page) — I will not deploy or upload on your
behalf. Flag for planning: what does the new filename need to be? (`Omkar_Jadhav_Ace.pdf` is the
current one.)

### 🟢 7. Confirmed decisions on record

| # | Decision |
|---|---|
| 1 | RAG / pgvector / embeddings **may be described factually for the portfolio project**, but stay out of `knowsAbout` and on-page skill lists (option **b**) |
| 2 | Remove Dnyanda Solutions from experience; keep `dev-mobiles`; `crop-recommendation` publishable without a code link *(pending §9.3–9.4)* |
| 3 | Turn `SEED_DEMO_DATA` **off** |
| 5 | `address` = **Pune**; Kolhapur carried via `alumniOf` + `homeLocation` |
| 6 | Software Developer Intern **2 Feb 2026 – 2 Aug 2026**; **SDE-I from Aug 2026** |
| 7 | **Do not publish the phone number** in any form |
| 8 | **Allow all** AI crawlers (GPTBot, ClaudeBot, PerplexityBot, Google-Extended, CCBot) |
| 9 | **No X/Twitter account** — remove the link and `twitter:creator` |
| 10 | Fix rendering by **prerendering at build time** (option **a**) |
| 11 | Domain-agnostic rule **extends to backend config**; `render.yaml` in scope; no deploys |

---

## Summary — severity-ranked

| # | Finding | Severity | Wave |
|---|---|---|---|
| 1 | Canonical / OG / sitemap / robots all point at `omkarjadhav.vercel.app`, which **404s** | 🔴 Critical | 0–1 |
| 2 | Crawlers receive **zero content** — empty `<div id="root">` on every route | 🔴 Critical | 1 |
| 3 | Live resume PDF carries old phone, "student", "seeking", Next.js, FastAPI, ChromaDB, RAG | 🔴 Critical | 1/3 |
| 4 | "Student" / "open to internships" in title, description, OG image and live API | 🔴 Critical | 1 |
| 5 | Next.js published as a live claimed skill; FastAPI/RAG in copy | 🔴 Critical | 1 |
| 6 | Fabricated star/fork/commit metrics live via `SEED_DEMO_DATA=true` | 🔴 Critical | 1 |
| 7 | Three contradictory versions of his current job live simultaneously | 🔴 Critical | 1 |
| 8 | LinkedIn URL in the brief resolves to a different person | 🔴 Critical | blocked |
| 9 | Every unknown URL returns **200** (soft-404); all routes byte-identical | 🟠 High | 1 |
| 10 | Three published GitHub links are **404s** | 🟠 High | 1 |
| 11 | Single-page architecture — one URL for six query clusters | 🟠 High | 3 |
| 12 | No favicon, manifest, `llms.txt`, or JSON-LD of any kind | 🟠 High | 2/6 |
| 13 | Two competing content sources; fixing the DB alone won't fix the site | 🟠 High | 1 |
| 14 | Sitemap uses fragment URLs; `lastmod` frozen at 2026-06-01 | 🟠 High | 1 |
| 15 | X/Twitter link + `twitter:creator` for an account he doesn't own | 🟠 High | 1 |
| 16 | Content depends on a cold-starting free-tier API (**6.86 s** measured) | 🟠 High | 1/4 |
| 17 | `/scratch` dev scaffold publicly routable | 🟡 Medium | 1 |
| 18 | Two `<h1>` elements on the homepage (`HeroSection:78`, `AboutSection:228`) | 🟡 Medium | 3 |
| 19 | 536 KB raw / 173 KB gzipped main bundle; render-blocking Google Fonts | 🟡 Medium | 4 |
| 20 | `Referrer-Policy: no-referrer` will blank analytics referrers | 🟢 Low | 5 |

**Next:** Phase 1 — `docs/seo/01-SEO-RULEBOOK.md`.
