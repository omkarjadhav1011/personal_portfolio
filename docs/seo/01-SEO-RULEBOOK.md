# 01 — SEO RULEBOOK

The standard this site is audited against in [`02-AUDIT.md`](./02-AUDIT.md). Written to be
self-contained: a developer who has never seen this repo should be able to apply it.

**Every rule states: what it is → why it matters → how to verify → what "pass" looks like.**

Rules are tuned to the stack detected in [`00-RECON.md`](./00-RECON.md) — **Vite 5 + React 18 SPA
on Vercel, prerendered at build time; Spring Boot 3.5 API on Render; PostgreSQL**. Advice that
only applies to Next.js, or to a server-rendered-on-request app, has been deliberately excluded.

**Rule IDs are stable.** `02-AUDIT.md` references them; do not renumber.

### A note on why this rulebook is weighted the way it is

A conventional SEO rulebook optimizes for traffic. This one optimizes for **entity
disambiguation**, because the constraint here is not volume — it is that at least fifteen people
share the subject's name, several with better-established web presences
(`00-RECON.md` §0.7). Sections **E** (structured data), **I** (AI answer engines) and **J**
(off-page entity authority) therefore carry more weight than they would on a typical site, and
Section **C** argues against the architecture the site currently has.

The single sentence that governs everything below:

> **The job is to make one machine-readable claim — "this specific person is the subject of this
> site" — and to make that claim consistently, in crawlable HTML, everywhere a machine can check.**

---

## Section A — Crawlability and indexation

### A1 · robots.txt is permissive, correct, and names an absolute sitemap
**What.** A `robots.txt` at the origin root that allows crawling of all indexable content, blocks
nothing that is needed to render the page, and ends with an absolute `Sitemap:` line.
**Why.** It is the first file a crawler requests. A wrong `Sitemap:` URL orphans the sitemap. A
`Disallow` on CSS/JS makes rendered indexing impossible.
**Verify.** `curl -s $SITE/robots.txt`. Confirm `Content-Type: text/plain`. Follow the `Sitemap:`
URL and confirm it returns 200 XML.
**Pass.** Returns 200 `text/plain`; no `Disallow` covering public routes or `/assets/`; admin
routes disallowed; `Sitemap:` is absolute, built from the site-URL variable, and resolves to 200.

### A2 · An XML sitemap listing every indexable URL — and only those
**What.** Valid `<urlset>` with one `<url>` per canonical, indexable URL. Accurate `<lastmod>` in
W3C datetime. No `<priority>`/`<changefreq>` — Google ignores both.
**Why.** The sitemap is the discovery seed for a site with no inbound links. Every wrong entry
spends crawl budget teaching the crawler something false.
**Verify.** `curl -s $SITE/sitemap.xml | xmllint --noout -`. Cross-check the URL list against the
route manifest. Confirm each `<loc>` returns 200 and self-canonicalizes.
**Pass.** Every public route present exactly once; **no fragment URLs** (see A3); no admin,
`/scratch`, or 404 URLs; `<lastmod>` reflects real content change; generated at build from the
site-URL variable, never hand-maintained.

### A3 · Fragment URLs are never sitemap entries or canonical targets
**What.** `https://example.com/#about` is not a distinct resource. Everything from `#` onward is
stripped before the request leaves the browser.
**Why.** Listing `/#about`, `/#projects`, `/#contact` declares the same single URL four times.
It does not create four indexable pages — it signals confusion about the site's own structure.
**Verify.** `grep '#' sitemap.xml` → no matches inside `<loc>`.
**Pass.** Zero `#` characters in any `<loc>` or `rel=canonical`.

### A4 · Every page has a self-referential absolute canonical
**What.** `<link rel="canonical" href="https://{site}/{path}">` naming *that page's own* absolute
URL.
**Why.** It consolidates duplicates (trailing slash, query params, protocol variants) onto one
address. A canonical pointing elsewhere transfers all indexing signals to that other URL — and if
that URL 404s, the page is asking to be dropped.
**Verify.** For every route: `curl -sL $SITE/$route | grep -i canonical`, then fetch the canonical
target and confirm **200**, not 3xx or 404.
**Pass.** Present on every route; absolute; host matches the live host; unique per route; resolves
200; built from the site-URL variable.

### A5 · Correct HTTP status codes — no soft 404s
**What.** 200 for real content, 404 for nonexistent, 301/308 for permanent moves, 410 for
deliberate removal.
**Why.** An SPA catch-all rewrite serves 200 + the app shell for *every* URL, so an unbounded
number of nonexistent URLs look like valid pages. Google calls this a soft 404; it wastes crawl
budget and can suppress indexing of the real pages.
**Verify.** `curl -o /dev/null -w "%{http_code}" $SITE/definitely-not-a-real-url-12345`.
**Pass.** Nonexistent URLs return **404**. On Vercel + Vite, achieved by prerendering known routes
to real files and adding a `404.html` that Vercel serves with a 404 status, rather than a blanket
`/(.*)` → `/index.html` rewrite.

### A6 · Admin and utility routes are excluded from indexing
**What.** `/admin/**`, dev scaffolds, and OAuth callbacks kept out of the index.
**Why.** Thin, duplicate, sometimes sensitive. Login pages in the index dilute a small site.
**Verify.** Confirm absent from the sitemap; confirm `Disallow` in robots.txt; for any that could
leak, confirm `X-Robots-Tag: noindex`.
**Pass.** Not in the sitemap, disallowed in robots.txt, and — because robots.txt prevents crawling
but not indexing of a linked URL — `noindex` on any that are externally reachable.
**Note.** Never *both* `Disallow` and `noindex` on the same URL: a disallowed page can't be
crawled, so the `noindex` is never read. Pick one — `noindex` if the URL might be linked.

### A7 · Redirect hygiene — no chains, no loops
**What.** One hop from any deprecated URL to its final destination.
**Why.** Each hop loses a little signal and costs crawl budget; loops make a page permanently
unreachable.
**Verify.** `curl -sIL $URL | grep -E "^(HTTP|Location)"` — count the 3xx hops.
**Pass.** At most one redirect between any entry URL and a 200.

### A8 · Trailing-slash consistency
**What.** One canonical form site-wide — `/projects/foo` or `/projects/foo/`, never both.
**Why.** Both forms served with 200 is duplicate content.
**Verify.** Request both forms; one must 200, the other must 301/308 to it.
**Pass.** Enforced in `vercel.json` (`"trailingSlash": false` recommended — matches
`react-router` route definitions), and every internal link, canonical, and sitemap entry uses that
form.

### A9 · No orphan pages
**What.** Every indexable URL reachable by following `<a href>` from the homepage.
**Why.** Sitemap presence aids discovery but not importance. A page with no internal links reads
as unimportant.
**Verify.** Crawl from `/` with JS disabled; diff the reachable set against the sitemap.
**Pass.** Sitemap set == crawlable set. Critically: the links must be **real `<a href>` elements in
the prerendered HTML**, not `onClick` handlers (see B4).

### A10 · Crawl budget is not wasted
**What.** Don't make a crawler fetch near-identical or valueless URLs.
**Why.** Less of an issue at this scale, but the soft-404 pattern (A5) makes the URL space
effectively infinite, which *is* a real problem at any scale.
**Verify.** After A5 is fixed, confirm the number of 200-returning URLs equals the number of real
pages.
**Pass.** Finite, enumerable URL space matching the sitemap.

### A11 · IndexNow for instant submission to participating engines
**What.** A generated key file at the origin root; ping `api.indexnow.org` with changed URLs on
deploy.
**Why.** Bing, Yandex, Seznam and Naver index within hours instead of weeks. Cheap to add.
**Verify.** Confirm the key file returns 200 `text/plain`; confirm the ping returns 200/202.
**Pass.** Key file live; deploy hook submits changed URLs.
**Honest caveat.** **Google does not participate in IndexNow.** It helps Bing — which matters more
than its market share suggests, because **ChatGPT's web search is Bing-backed**, making IndexNow
part of Section I as much as Section A.

---

## Section B — Rendering

### B1 · Every indexable fact exists in the server-returned HTML
**What.** The HTML delivered by the server — before any JavaScript runs — contains the page's full
substantive content.
**Why.** Google renders JS, but on a delay, on a budget, and with no guarantee. Bing, and the AI
crawlers in Section I, render far less reliably or not at all. **`GPTBot`, `ClaudeBot`,
`PerplexityBot` and `CCBot` do not execute JavaScript.** For a site whose purpose is to be cited
by AI assistants, client-only rendering is not a handicap — it is total invisibility.
**Verify.** `curl -sL $SITE/$route | sed 's/<[^>]*>//g' | tr -s '[:space:]' ' '` — read what's
left. That is the crawler's view.
**Pass.** Name, role, employer, education, skills, project descriptions and contact route all
present as text in raw HTML on the page that owns them. An empty `<div id="root">` is an automatic
fail for every rule in this section.

### B2 · Prerender at build time (the strategy chosen for this stack)
**What.** After `vite build`, visit each public route in a headless browser, wait for content, and
write the resulting HTML to `dist/<route>/index.html`.
**Why.** It delivers B1 without abandoning the SPA, the admin panel, or any backend subsystem.
Hydration then takes over for interactivity — the user experience is unchanged.
**Verify.** After build, `cat dist/index.html` and `cat dist/recruiter/index.html` — they must
differ, and both must contain real text.
**Pass.** One HTML file per public route, each with route-specific content and route-specific
`<head>`. `dist/` contains no route whose body is an empty root div.
**Applies to:** `/`, `/projects/:slug` (one file per real slug), `/recruiter`, `/mcp`, and the
Section C pages once they exist. **Not** admin routes — those stay client-only by design.

### B3 · The build fails loudly when prerendering fails
**What.** If the data source is unreachable or a route renders an error/skeleton state, the build
exits non-zero.
**Why.** This stack prerenders against a live API on a **free-tier host that cold-starts (6.86 s
measured)**. A silent failure would bake a skeleton — or the literal string *"fatal: failed to
load portfolio data from the backend"* — into the HTML and publish it. That is worse than not
prerendering, because it looks fine in a browser after hydration while serving garbage to crawlers.
**Verify.** Run the build with the API unreachable. It must fail.
**Pass.** Prerender step asserts on each route that (a) no skeleton/error sentinel is present and
(b) an expected content string is, then throws on violation. Generous retry/timeout for cold
starts, but **never a silent fallback to the empty shell**.

### B4 · Interaction-gated content is invisible content
**What.** Any substantive fact reachable only after a click, keypress, hover, or scroll-triggered
fetch does not exist for search.
**Why.** Crawlers do not click. Neither do AI crawlers, which don't run JS at all.
**Verify.** With JS disabled, read the page. Anything you cannot read is not indexed.
**Pass.** Every fact about the subject exists as crawlable HTML somewhere. Specifically for this
site: everything the **chatbot**, the **Ctrl+K command palette**, and the **terminal** can state
must also exist on a real page. Accordions and tabs are acceptable *only* if their content is in
the DOM and hidden with CSS — never if mounted on click.
**Corollary.** The assistant is a convenience layer over public content. It must never be the only
place a fact lives.

### B5 · Prerendered HTML and hydrated DOM must agree
**What.** No hydration mismatch, and no content that only appears post-hydration.
**Why.** A mismatch can blank content; content that only appears after hydration fails B1.
**Verify.** Diff the raw HTML text against the rendered DOM text:
```bash
curl -sL $SITE/ | sed 's/<[^>]*>//g' | tr -s '[:space:]' '\n' | sort -u > /tmp/raw.txt
# compare against document.body.innerText from a headless browser
```
**Pass.** No React hydration warnings in console; no substantive text present only in the
hydrated version.

### B6 · No dependence on a runtime API for first paint of indexable content
**What.** Indexable content is baked in at build; runtime fetches are for interactive features
only.
**Why.** Otherwise B1 is contingent on a third-party service being warm during the crawl.
**Verify.** Block all XHR/fetch in devtools, hard-reload. The content must still be there.
**Pass.** Public pages fully readable with the API unreachable. Chat, recruiter and contact may
degrade — they are interactions, not content.
**Trade-off to accept explicitly.** Content freshness now requires a rebuild. Editing via the
admin panel must trigger a Vercel deploy hook, or content changes won't reach the prerendered HTML.
Document this so it isn't discovered as a bug.

---

## Section C — Information architecture

### C1 · One URL cannot rank for many query clusters
**What.** A single-page scrolling portfolio has a hard ranking ceiling.
**Why.** Google ranks *pages*. One page has one `<title>`, one canonical, one primary topic. Anchor
links (`/#projects`) do not create pages (A3). A site with one URL competes for one query cluster
and is outgunned on every other by anyone with a dedicated page — which, given fifteen competing
namesakes with multi-page sites and LinkedIn profiles, is most of the field.
**Verify.** Count indexable URLs. If it is 1, this fails regardless of content quality.
**Pass.** One page per query cluster, per C2.

### C2 · Target structure
Each page owns one cluster, one `<title>`, one primary keyword:

| URL | Owns | Primary query shape |
|---|---|---|
| `/` | Identity hub — who he is, links to everything | "Omkar Jadhav" |
| `/about` | Full biography, `ProfilePage` + `Person` schema | "Omkar Jadhav software engineer" |
| `/projects` | Project index | "Omkar Jadhav projects" |
| `/projects/{slug}` | **One page per project** | project name, tech stack + problem |
| `/skills` | Technical skills with evidence | "Omkar Jadhav C# NestJS" |
| `/experience` | Employment history | "Omkar Jadhav Nonstop IO" |
| `/education` | KIT Kolhapur, ICRE Gargoti, SSC | "Omkar Jadhav KIT College Kolhapur" |
| `/resume` | HTML résumé (+ PDF link) | "Omkar Jadhav resume" |
| `/contact` | Contact routes | "Omkar Jadhav contact" |
| `/blog` + `/blog/{slug}` | Technical writing | topic queries (Section I/J) |

**Why `/projects/{slug}` matters most.** Project pages are the only pages that can rank for
queries *not containing his name* — the Section J entry point for someone who has never heard of
him. They are also where `SoftwareSourceCode` schema attaches.
**Why an HTML `/resume` matters.** A PDF is a poor ranking asset and a worse AI-citation asset.
An HTML résumé is crawlable, linkable, and can carry schema; the PDF becomes a download, not the
source of truth.

### C3 · URL slug rules
**What.** Lowercase, hyphen-separated, short, descriptive, stable, no dates, no stop words, no IDs.
**Why.** Slugs are a ranking signal and a user-facing signal in the SERP.
**Verify.** Read the URL list cold — each should be guessable from its title.
**Pass.** `/projects/ai-interview-preparation-system` ✅ · `/projects/proj-2?id=7` ❌.
**Stability.** A published slug is a permanent commitment. Changing one requires a 301 (A7).

### C4 · Breadcrumbs on every nested page
**What.** A visible breadcrumb trail plus matching `BreadcrumbList` schema (E7).
**Why.** Communicates hierarchy and can render in the SERP, taking more pixels from competitors.
**Verify.** Visible trail present; schema mirrors it exactly (E10).
**Pass.** `Home › Projects › AI Interview Preparation System` visible in the prerendered HTML, with
`BreadcrumbList` matching position-for-position.

### C5 · Every page within three clicks of the homepage
**What.** Shallow click depth from `/`.
**Why.** Click depth correlates with crawl frequency and perceived importance.
**Verify.** BFS from `/` following `<a href>` in raw HTML.
**Pass.** Max depth ≤ 3. At this site's size, ≤ 2 is achievable.

### C6 · Contextual internal links with descriptive anchor text
**What.** Body-copy links between related pages, using descriptive anchors.
**Why.** Anchor text tells Google what the target is about; navigation links all look the same,
body links don't.
**Verify.** For each page, list inbound internal anchors. Flag "click here", "read more", bare URLs.
**Pass.** Every page has ≥ 2 contextual inbound internal links with meaningful anchor text; no
generic anchors.
**Example.** `/experience` → "wrote optimized SQL queries for the
[Report Builder module](/projects/...)", not "see more [here](/projects/...)".

---

## Section D — On-page

### D1 · Title tag formulas per page type
**What.** Unique, 50–60 char `<title>`, primary keyword first, name-anchored.
**Why.** Still the strongest on-page signal, and the SERP headline. For identity queries the name
must be leftmost — it is the matched term.
**Verify.** Extract `<title>` from every route's raw HTML; check uniqueness and pixel length.
**Pass.** Formulas:

| Page | Formula |
|---|---|
| `/` | `Omkar Jadhav — Software Development Engineer I at Nonstop IO` |
| `/about` | `About Omkar Jadhav — Backend Developer in Pune, India` |
| `/projects/{slug}` | `{Project Name} — {Primary Tech} Project by Omkar Jadhav` |
| `/experience` | `Experience — Omkar Jadhav, SDE-I at Nonstop IO Technologies` |
| `/education` | `Education — Omkar Jadhav, B.Tech CSE (Data Science), KIT Kolhapur` |
| `/skills` | `Skills — Omkar Jadhav: C#, NestJS, Spring Boot, SQL` |
| `/blog/{slug}` | `{Article Title} — Omkar Jadhav` |

**Disambiguation rule.** Every title carries at least one disambiguator — employer, institution,
city, or the full legal name. A title of just `Omkar Jadhav` competes with fifteen people; one
that says `Omkar Jadhav — SDE-I at Nonstop IO` competes with none.

### D2 · Unique, written meta descriptions
**What.** 140–160 chars, unique per page, active voice, naming the entity explicitly.
**Why.** Not a ranking factor; it *is* the SERP click-through pitch, and AI crawlers read it as a
summary.
**Verify.** Extract all descriptions; check uniqueness and length.
**Pass.** Unique per page, in range, containing the name and at least one disambiguator, no
truncation mid-word, no keyword lists.

### D3 · Exactly one `<h1>` per page
**What.** One `<h1>` naming the page's subject.
**Why.** It is the in-page statement of topic. Two `<h1>`s split the signal; zero leaves it
undefined.
**Verify.** `curl -sL $SITE/$route | grep -o '<h1' | wc -l` → must be `1`.
**Pass.** Exactly one, containing the primary keyword, near the top of the DOM.
**For `/`:** the `<h1>` should carry name **and** role — `Omkar Jadhav` alone wastes the strongest
heading on the site's hardest query. `Omkar Jadhav — Software Development Engineer I` is the ask.

### D4 · Logical heading order, no level skips
**What.** `h1` → `h2` → `h3` in DOM order, never skipping down a level.
**Why.** Headings are the document outline for crawlers and screen readers alike.
**Verify.** Extract headings **in DOM order** (not visual order) and check the sequence.
**Pass.** No skips; headings describe content, not styling; never chosen for font size.

### D5 · Semantic HTML5 landmarks
**What.** `<header>`, `<nav>`, `<main>` (exactly one), `<article>`, `<section>`, `<aside>`,
`<footer>`.
**Why.** Distinguishes primary content from chrome, for crawlers and assistive tech.
**Verify.** Inspect raw HTML for landmark elements.
**Pass.** Exactly one `<main>` wrapping the unique content; boilerplate outside it.

### D6 · Keyword placement without stuffing
**What.** Primary keyword in title, `h1`, first 100 words, one `h2`, one image alt, and the URL —
then write naturally.
**Why.** Placement signals topic; repetition past that point is a spam signal.
**Verify.** Keyword density < ~2%; read it aloud — if it sounds strange, it is stuffed.
**Pass.** Natural prose that a human would write, hitting the placements above once each.

### D7 · Minimum content depth per page type
**What.** Enough text to substantiate the page's claim.
**Why.** Thin pages don't rank and can drag sitewide quality assessment down.
**Pass.** `/` ≥ 400 words · `/about` ≥ 600 · `/projects/{slug}` ≥ 300 · `/experience` ≥ 400 ·
`/education` ≥ 250 · `/skills` ≥ 400 · `/blog/{slug}` ≥ 800.
**Qualifier.** Word counts are a floor, not a target. Padding to hit a number fails D8. If a
project genuinely warrants 300 words, write 300 — do not inflate to 800.

### D8 · Unique content per page — no boilerplate duplication
**What.** Each page's main content substantially differs from every other page's.
**Verify.** Diff the main-content text across pages; look for shared blocks.
**Pass.** No page shares > ~20% of its body text with another.

### D9 · Descriptive anchor text
Covered in C6. **Pass.** No "click here", "read more", "link", or bare URLs as anchor text.

### D10 · Image alt text
**What.** Every `<img>` has `alt`. Decorative images get `alt=""`. Meaningful images describe
content; images-as-links describe the destination.
**Why.** Accessibility requirement and an image-search ranking signal.
**Verify.** List every `<img>`; flag missing/empty-but-meaningful/stuffed alts.
**Pass.** `alt="Omkar Jadhav, Software Development Engineer I at Nonstop IO Technologies"` on the
profile photo ✅ · `alt="photo"` or `alt="omkar jadhav developer pune kolhapur c# nestjs"` ❌.

### D11 · Copy uses present-tense, accurate employment facts
**What.** No "student", "final year", "fresher", "seeking", "looking for opportunities", or
"intern" as a *current* descriptor. Education in past tense.
**Why.** He is a graduate and employed. Beyond accuracy, stale status copy positions him *closer*
to his early-career namesakes, which is the opposite of disambiguation.
**Verify.** `grep -riE "student|final.year|fresher|seeking|looking for opportunit|intern" src/`
(excluding legitimate history, e.g. the Feb–Aug 2026 internship as a *past* role).
**Pass.** Zero matches in user-facing copy, metadata, the OG image, and the résumé PDF.

---

## Section E — Structured data: the entity layer

**The highest-leverage section in this engagement.** Sections A–D help a page rank. Section E is
what tells Google *which human being* the page is about.

### E0 · How Google assembles a person entity (context for E1–E11)
Google builds an entity by finding claims about an identifier and corroborating them across
independent sources. `sameAs` is the claim: *"the entity at this `@id` is also the entity at these
URLs."* It is **verified for reciprocity** — a link from the site to a LinkedIn profile is weak
until that profile links back. Reciprocity is the mechanism that separates this subject from
fourteen namesakes; nothing else in this rulebook substitutes for it.

**Consequence: a wrong `sameAs` is worse than a missing one.** It instructs Google to merge the
subject with a different person, and the missing reciprocity guarantees it stays ambiguous.
Publish a profile URL only when ownership is confirmed.

### E1 · A single `Person` node, with a stable `@id`, as the graph root
**What.** One `Person` with `@id` = `{SITE_URL}/#person` — a stable IRI, never a page URL that
might change.
**Why.** The `@id` is the identifier every other node points at. Duplicating the Person inline on
each page creates several entities that may not merge.
**Verify.** Every route's JSON-LD; confirm one `Person` and an identical `@id` everywhere.
**Pass.** Required properties:

| Property | Value |
|---|---|
| `name` | `Omkar Jayvant Jadhav` |
| `alternateName` | `["Omkar Jadhav", "Omkar J. Jadhav", "Omkar Jaywant Jadhav"]` |
| `jobTitle` | `Software Development Engineer I` |
| `description` | One sentence naming role, employer, city |
| `url` | `{SITE_URL}/` |
| `mainEntityOfPage` | `{SITE_URL}/about` |
| `image` | → `ImageObject` (E9) |
| `email` | `mailto:jadhavomkar101103@gmail.com` |
| `sameAs` | Every **confirmed** profile URL (E2) |
| `knowsAbout` | The approved skills list only (E3) |
| `alumniOf` | → `EducationalOrganization` nodes (E4) |
| `hasCredential` | → `EducationalOccupationalCredential` nodes (E5) |
| `worksFor` | → `Organization` (E6) |
| `address` | → `PostalAddress`, Pune |
| `homeLocation` | → `Place`, Kolhapur |
| `nationality` | `Indian` |

**On `alternateName`.** Include the common misspelling *Omkar Jaywant Jadhav* — it costs nothing
and catches a real query variant. **Do not** include "Omkar Jadhav Pune" or "Omkar Jadhav KIT
Kolhapur"; those are search queries, not names, and putting them in `alternateName` is keyword
stuffing in structured data.

### E2 · `sameAs` lists only confirmed, reciprocal profiles
**What.** Every URL in `sameAs` is verified as belonging to the subject **and** links back.
**Verify.** For each: open it, confirm identity, confirm a link back to `{SITE_URL}`.
**Pass.** Confirmed today: GitHub `https://github.com/omkarjadhav1011`. **Blocked pending
confirmation:** LinkedIn (the brief's URL resolves to a different person — `00-RECON.md` §9.1) and
LeetCode (no URL supplied). **Omit** any unconfirmed URL entirely; an empty `sameAs` entry is
strictly better than a wrong one.

### E3 · `knowsAbout` contains only approved skills
**What.** The confirmed skills list, verbatim.
**Why.** Structured data is a formal claim. Claiming a withdrawn technology is a false claim in
machine-readable form.
**Pass.** Exactly: Python, Java, JavaScript, TypeScript, C#, SQL, React, HTML5, CSS3, Spring Boot,
NestJS, REST APIs, PHP, Gemini API, Hugging Face API, Prompt Engineering, PostgreSQL, MySQL, Git,
GitHub, Docker, Streamlit, Postman, VS Code, Data Structures & Algorithms, OOP, DBMS, MVC
Architecture.
**Never:** Next.js, FastAPI, ChromaDB, vector databases, RAG pipelines.
**Boundary note.** The portfolio project may *describe* its own pgvector/embedding implementation
in prose (a factual statement about software). That description must not leak into `knowsAbout`,
`skills`, or any on-page skill list — those are personal-capability claims, and they are governed
by the approved list.

### E4 · `alumniOf` → `EducationalOrganization` nodes
**What.** One node per institution, each with `@id`, `name`, `address`, and where known `sameAs`
(official site / Wikipedia).
**Why.** Institutions are established entities with their own knowledge-graph presence. Linking to
them borrows disambiguating power — "the Omkar Jadhav connected to *KIT's College of Engineering,
Kolhapur*" is far more specific than "Omkar Jadhav".
**Pass.** Nodes for KIT's College of Engineering (Autonomous), Kolhapur; Institute of Civil and
Rural Engineering, Gargoti; Shankar Chakru Patil Madhyamik Vidhyalaya, Dindewadi. Full names as
the institutions write them — abbreviations like "KIT Kolhapur" or "ICRE Gargoti" won't match.

### E5 · `hasCredential` for degrees and certifications
**What.** `EducationalOccupationalCredential` for the B.Tech, the Diploma, the SSC, and the three
Udemy certificates.
**Pass.** Each with `credentialCategory` (`degree` / `certificate`), `name`, `recognizedBy` → the
issuing organization, and a date. Percentages (80%, 87%, 94%) only where visibly on-page (E10).

### E6 · `worksFor` → `Organization`
**Pass.** `Nonstop IO Technologies`, with `address` → `PostalAddress` (Kharadi, Pune, Maharashtra,
IN), `url` `https://nonstopio.com`, and a stable `@id`. **One spelling everywhere** — the live site
currently uses three ("NonStop io Technologies", "NonstopIO", "Nonstop IO Technologies"), which
defeats the purpose of naming an employer at all.
**Employment dates** belong in visible on-page copy and may be represented with an
`OrganizationRole` carrying `startDate`: Software Developer Intern 2026-02-02 → 2026-08-02;
SDE-I 2026-08 → present.

### E7 · `BreadcrumbList` on every nested page
**Pass.** Mirrors the visible breadcrumb exactly, `position` starting at 1, each `item` an absolute
URL.

### E8 · `SoftwareSourceCode` per project
**Pass.** Per project: `name`, `description`, `programmingLanguage`, `codeRepository` (**omit if no
repo exists** — never link a 404), `url` (the project page), `image`, `author` → `{"@id":
"{SITE}/#person"}`, `dateCreated`.
**Why it matters here.** These are the nodes that associate the person with technologies
*independently of his name* — the Section J entry point.

### E9 · `ImageObject` for the profile photo
**Pass.** `contentUrl`, `width`, `height`, `caption`. Referenced by `Person.image` via `@id`.

### E10 · Structured data mirrors visible content — no exceptions
**What.** Never mark up anything a human cannot see on that page.
**Why.** It is a Google Search spam policy violation with manual-action risk, and it makes the
markup unverifiable.
**Verify.** For every property, find the corresponding visible text on the same page.
**Pass.** A one-to-one mapping. If `hasCredential` claims 80% in the B.Tech, "80%" is visible on
`/education`. If it isn't visible, it isn't marked up.

### E11 · `@id` cross-linking so every node resolves once
**What.** Define each node **once** with `@id`; reference it elsewhere as `{"@id": "..."}`.
**Why.** Without it, `Person` repeated on ten pages may be read as up to ten entities. With it,
every page contributes evidence to the *same* node.
**Verify.** Validate each route; confirm a single `Person` node and no duplicate `@id`s with
differing content.
**Pass.** Stable IRI scheme, all built from the site-URL variable:

```
{SITE}/#person           Person            (root)
{SITE}/#website          WebSite           publisher → #person
{SITE}/#organization     Organization      Nonstop IO
{SITE}/#kit-kolhapur     EducationalOrganization
{SITE}/about#profilepage ProfilePage       mainEntity → #person
{SITE}/projects/{slug}#software  SoftwareSourceCode  author → #person
```

**Implementation rule.** One typed module generating the graph, imported by every page. Never
hand-written JSON blobs per page — they drift, and drift is what creates duplicate entities.

### E12 · `WebSite` and `ProfilePage`
**Pass.** `WebSite` with `url`, `name`, `publisher` → `#person`, `inLanguage: "en"`.
`ProfilePage` on `/about` with `mainEntity` → `#person` and `dateModified`.
**Omit `SearchAction`** unless a real on-site search endpoint exists — marking up a search box
that doesn't exist fails E10.

### E13 · `FAQPage` only where genuine visible Q&A exists
**Pass.** Applied only to a page with real, visible question-and-answer pairs. Never invented to
win a rich result.
**Honest note.** Google heavily restricted FAQ rich results in 2023 — they now show mainly for
authoritative government/health sites. Implement for **AI-answer-engine parsing** (Section I),
not for a SERP feature that probably won't appear.

### E14 · Validation is part of the definition of done
**Verify.** Google Rich Results Test, Schema.org validator, and `@id` resolution across routes.
**Pass.** Zero errors. Warnings triaged and either fixed or documented with a reason.

---

## Section F — Performance and Core Web Vitals

Thresholds are **75th percentile of real users**, per metric, per device class. Lab tools
approximate; field data (CrUX / Search Console) is the scoreboard.

### F1 · LCP ≤ 2.5 s
**What.** Time until the largest in-viewport element renders.
**Why.** Ranking factor, and it correlates with bounce.
**Verify.** Lighthouse (lab); Search Console Core Web Vitals (field); PageSpeed Insights (both).
State which you used.
**Pass.** ≤ 2.5 s at p75 mobile. **On this stack the dominant lever is B2/B6** — prerendering
converts LCP from "cold API round trip + JS parse + render" into "parse HTML".

### F2 · INP ≤ 200 ms
**What.** Interaction to Next Paint — responsiveness across all interactions. (Replaced FID in
March 2024.)
**Verify.** Field data; lab approximation via Lighthouse Total Blocking Time.
**Pass.** ≤ 200 ms at p75. Main risks here: a 536 KB main bundle and framer-motion animations on
scroll.

### F3 · CLS ≤ 0.1
**What.** Cumulative Layout Shift.
**Verify.** Lighthouse; record the page with throttling and watch for jumps.
**Pass.** ≤ 0.1 at p75. Requires explicit `width`/`height` (or `aspect-ratio`) on every image,
reserved space for anything injected late, and `font-display: swap` **with a metric-compatible
fallback** — swap without one is itself a shift source.

### F4 · Modern image formats with explicit dimensions
**Pass.** AVIF or WebP with a fallback; `width` and `height` on every `<img>`; `srcset`/`sizes`
for anything rendered at multiple widths.

### F5 · `fetchpriority="high"` on the LCP image; lazy-load below the fold
**Pass.** LCP image: `fetchpriority="high"`, **no** `loading="lazy"` (lazy-loading the LCP element
delays it — a common own-goal). Everything below the fold: `loading="lazy"` + `decoding="async"`.

### F6 · Self-hosted fonts with `font-display: swap`
**What.** Serve font files from the site's own origin.
**Why.** A Google Fonts `<link>` in `<head>` is **render-blocking** and adds a third-party
connection (DNS + TLS + fetch) to the critical path. `preconnect` reduces but does not remove it.
**Verify.** Waterfall — no font-stylesheet request blocking first paint.
**Pass.** Fonts self-hosted as `woff2`, `@font-face` inlined in the critical CSS, `font-display:
swap`, only the weights actually used, subset where possible. `preload` the font used by the LCP
text.

### F7 · JS bundle budget
**Pass.** ≤ 200 KB gzipped for the initial public-route bundle; ≤ 300 KB total initial transfer.
Measure with `vite build` output and the network panel (state which).

### F8 · Code-split heavy interactive widgets
**What.** Anything not needed for first paint loads on demand.
**Why.** A widget that blocks first paint costs LCP for every visitor, including those who never
use it.
**Pass.** The **chatbot / command palette**, the **recruiter tool**, and the **entire admin bundle**
are separate chunks, never in the initial public payload. Admin routes must contribute **zero**
bytes to a public page load.

### F9 · No render-blocking resources in `<head>`
**Pass.** Critical CSS inline or minimal; non-critical CSS deferred; no synchronous third-party
scripts; no blocking font stylesheet (F6).

### F10 · Caching headers
**Pass.** Fingerprinted assets (`/assets/*`): `Cache-Control: public, max-age=31536000, immutable`.
HTML: `public, max-age=0, must-revalidate` so deploys are picked up. Set in `vercel.json`.

---

## Section G — Mobile and accessibility

### G1 · Mobile-first indexing
**What.** Google indexes the **mobile** rendering. Desktop-only content is invisible.
**Verify.** Fetch with a mobile UA; compare content to desktop.
**Pass.** Full content parity. Nothing hidden at mobile breakpoints via `display: none` that
matters for ranking.

### G2 · Viewport meta
**Pass.** `<meta name="viewport" content="width=device-width, initial-scale=1">`. No
`maximum-scale` or `user-scalable=no` — both break pinch-zoom and fail WCAG 1.4.4.

### G3 · Tap targets ≥ 44×44 px with ≥ 8 px spacing
**Verify.** Lighthouse accessibility audit; manual check on a real phone.

### G4 · No horizontal scroll at any width
**Verify.** At 320 px, `document.documentElement.scrollWidth <= window.innerWidth`.

### G5 · Mobile/desktop content parity
**Pass.** Same headings, same body text, same structured data, same internal links on both.

### G6 · Accessibility overlaps that affect SEO
**Pass.** Heading order (D4) · alt text (D10) · contrast ≥ 4.5:1 for body text, 3:1 for large text
and UI boundaries · full keyboard operability with a visible `:focus-visible` ring · ARIA landmarks
matching the semantic structure (D5) · `prefers-reduced-motion` honored · `<html lang="en">`.
**Why it is in an SEO rulebook.** Contrast and keyboard access aren't ranking factors, but heading
structure, alt text, landmarks and `lang` are consumed directly by crawlers. They are the same work.

---

## Section H — Social and sharing

### H1 · Complete Open Graph per page
**Pass.** `og:type`, `og:url` (absolute, self-referential, env-derived), `og:title`,
`og:description`, `og:site_name`, `og:locale`, `og:image` + `og:image:width` (1200) +
`og:image:height` (630) + `og:image:alt`. `og:type` is `profile` for `/about`, `article` for blog
posts, `website` elsewhere.

### H2 · Twitter/X card tags
**Pass.** `twitter:card="summary_large_image"`, `twitter:title`, `twitter:description`,
`twitter:image`, `twitter:image:alt`.
**Rule.** Include `twitter:creator`/`twitter:site` **only** for an account the subject actually
owns. Pointing at someone else's handle attributes his content to them — a Section E entity problem
wearing a social-tag costume, not a cosmetic issue.

### H3 · Per-page OG images
**Pass.** Homepage/about get a portrait card; project pages get a card naming the project. Generated
at build from the site-URL variable, so no domain string is ever baked into pixels.
**Content rule.** Text rendered into an image cannot be edited by a metadata fix. Any fact in an OG
image (job title, availability status, domain) must be generated from the same source as the page
copy, or it will silently go stale.

### H4 · Complete favicon set and web manifest
**Pass.** `favicon.ico` (multi-size), `favicon.svg`, `apple-touch-icon.png` (180×180),
`site.webmanifest` with `name`, `short_name`, `theme_color`, `background_color`, and 192/512 icons.
Each must return its correct `Content-Type` — a favicon served as `text/html` is a failure of A5,
not just H4.

---

## Section I — AI answer-engine optimization (GEO/AEO)

**Why this section matters unusually much here.** The strongest real-world use of this site is
someone — a recruiter, a colleague, an interviewer — asking ChatGPT, Claude, Perplexity or Google
AI Overviews *"who is Omkar Jadhav?"*. Ranking #1 on a SERP nobody visits is worth less than being
the source the assistant quotes. And because these crawlers **do not execute JavaScript**, every
rule in Section B is a prerequisite for this section.

### I1 · `llms.txt` at the origin root
**What.** A Markdown file at `/llms.txt` summarizing the site and linking key pages with
descriptions.
**Why.** An emerging convention giving LLM crawlers a curated map instead of an inferred one. Low
cost; no downside.
**Verify.** `curl -s $SITE/llms.txt` → 200 `text/plain` or `text/markdown` — **not** `text/html`.
**Pass.** Contains: full legal name, current role and employer, location, education, a one-line
description per key page with absolute env-derived URLs, and the canonical disambiguating
statement. Kept in sync with the site.

### I2 · Explicit AI crawler policy in robots.txt
**What.** Named `User-agent` groups for `GPTBot`, `OAI-SearchBot`, `ChatGPT-User`, `ClaudeBot`,
`Claude-User`, `PerplexityBot`, `Google-Extended`, `CCBot`, `Bytespider`, `Amazonbot`.
**Why.** An absent policy and an explicit allow are different signals; being explicit is
unambiguous and future-proof.
**Decision on record:** **allow all** (`00-RECON.md` §9.7). Note the distinction: `Google-Extended`
controls Gemini/Vertex training and grounding — it does **not** affect Google Search ranking, so
allowing it costs nothing in search terms.
**Pass.** Each agent explicitly allowed; admin paths disallowed for all.

### I3 · Citation-friendly writing
**What.** Unambiguous, self-contained, explicitly-entity-named factual sentences.
**Why.** An LLM retrieves a passage, not a page. A paragraph that says "he works there" is
unusable once separated from its context; a paragraph that names the entity survives extraction.
**Verify.** Read any paragraph in isolation. Can a reader tell who and what it is about?
**Pass.**
- ✅ *"Omkar Jadhav works as a Software Development Engineer I at Nonstop IO Technologies in Pune,
  India, where he builds backend services in C#, NestJS and SQL for an enterprise reporting
  product."*
- ❌ *"He works there as an SDE-I, building backend services."*

Rules: name the entity in the first sentence of every section · never open with a pronoun ·
concrete dates, not "recently" or "currently" · one fact per sentence for key claims · front-load
the answer, then elaborate.

### I4 · Q&A formatting for high-intent questions
**What.** Explicit question headings with immediate, self-contained answers.
**Why.** Matches the retrieval shape of an answer engine and maps to `FAQPage` (E13).
**Pass.** `/about` answers, in `<h2>`s: *Who is Omkar Jadhav? · Where does Omkar Jadhav work? ·
What technologies does Omkar Jadhav work with? · Where did Omkar Jadhav study?* Each answered in
1–3 self-contained sentences directly beneath the heading.

### I5 · One canonical disambiguating statement, repeated verbatim
**What.** A single sentence distinguishing this Omkar Jadhav, used identically on the site, in
`llms.txt`, in schema `description`, in the meta description, in the GitHub bio, and on LinkedIn.
**Why.** Corroboration across independent sources is how an entity gets established. Identical
phrasing maximizes the match; paraphrases fragment it.
**Pass.** Something of this shape, once confirmed:
> *Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO Technologies in Pune,
> India. He graduated from KIT's College of Engineering (Autonomous), Kolhapur, with a B.Tech in
> Computer Science & Engineering (Data Science) in 2026, and works on backend development in C#,
> NestJS and SQL.*

### I6 · Facts are consistent across every surface
**What.** Job title, employer spelling, dates, institution names and skill lists are **identical**
on the site, in schema, in `llms.txt`, in the résumé PDF, on GitHub and on LinkedIn.
**Why.** Contradiction is the thing that prevents entity resolution. Three job titles across three
surfaces (the site's current state) means no source can be trusted, so none is used confidently.
**Verify.** Build a fact matrix: rows = facts, columns = surfaces. Every row must be uniform.
**Pass.** Zero contradictions. **This includes the downloadable résumé** — a PDF that disagrees
with the site undermines both.

### I7 · Content is extractable without JavaScript
Restates B1 for this section because the failure mode differs: Google *may* render and still index
you; an AI crawler will simply record nothing.
**Verify.** `curl -sL $SITE/ | sed 's/<[^>]*>//g'` — if the subject's name isn't in the output, no
AI assistant can cite the site.
**Pass.** Full content present in raw HTML on every public route.

---

## Section J — Off-page and entity authority

### J1 · `sameAs` reciprocity
**What.** Site → profile **and** profile → site, for every profile.
**Why.** The single most effective disambiguation mechanism available. See E0.
**Verify.** For each profile, confirm a live outbound link to `{SITE_URL}`.
**Pass.** GitHub profile website field **and** profile README link to the site; LinkedIn website
field and About link to the site; LeetCode website field links to the site. All reciprocated in
`sameAs`.

### J2 · Fact consistency across external profiles
Restates I6 for off-site surfaces, which are the ones most likely to drift.
**Verify.** Read each profile's headline, bio, location and current role against the fact matrix.
**Pass.** Uniform. **Currently failing:** the GitHub bio reads *"SDE Intern @ Nonstop IO | Java ·
Spring Boot · PostgreSQL · React | **Final-year CS @ KIT, 2026**"* — stale title, and a student
claim, on the highest-authority profile he controls.

### J3 · Realistic backlink sources for an early-career engineer
**Why.** Backlinks remain a major ranking factor, and this site has none. Volume is not achievable;
relevance is.
**Pass — a genuine, non-manipulative list:**
1. **GitHub profile README** linking the site (highest-value, fully controlled)
2. **Repo About/homepage fields** on every public repo
3. **GitHub topics** on each repo — improves discovery for tech queries
4. **LinkedIn** website field, Featured section, About
5. **LeetCode / HackerRank** profile website fields
6. **dev.to / Hashnode** cross-posts with `rel=canonical` back to the site
7. **Peerlist / Polywork**-style developer directories
8. **College alumni features** — KIT Kolhapur placement or alumni pages
9. **Conference / meetup speaker profiles** (Pune tech community)
10. **Open-source contributions** — a merged PR often yields a contributor link
**Never.** Paid links, PBNs, link exchanges, comment spam, directory blasts.

### J4 · GitHub optimization
**Pass.** Profile README with the I5 statement, the site link, and pinned projects · bio matching
the fact matrix · location `Pune, India` · website field set · pinned repos ordered to match the
site's featured projects · every pinned repo has a description and topics · the portfolio repo's
About names the live site.

### J5 · LinkedIn optimization
**Pass.** Headline: `Software Development Engineer I at Nonstop IO Technologies | C# · NestJS ·
SQL` · About opening with the I5 statement verbatim · Experience entry for SDE-I with the correct
start date **and** the preceding internship as a separate, completed entry · Education entries
matching E4 exactly · custom URL claimed · website link to the site · Featured section linking the
site and top projects.

### J6 · Every fact about him that exists publicly is one he controls
**What.** Audit what the web says about him and correct what's wrong at the source.
**Why.** Google corroborates across sources. An uncorrected stale profile is a competing claim.
**Pass.** Every profile he controls states the same facts. Anything wrong that he cannot edit is
documented as a known contradiction.

---

## Section K — Anti-patterns

Each is an automatic fail wherever found.

| ID | Anti-pattern | Why it's fatal | How to detect |
|---|---|---|---|
| **K1** | Leftover staging `noindex` | Silently removes the site from the index | `curl -sI $URL \| grep -i x-robots-tag`; grep for `noindex` |
| **K2** | Blocked CSS/JS in robots.txt | Google can't render; may judge the page broken | Check `Disallow` against asset paths |
| **K3** | Duplicate titles/descriptions | Pages compete with each other; SERP looks templated | Extract all, check uniqueness |
| **K4** | Thin pages | Don't rank; drag sitewide quality | Word count vs D7 |
| **K5** | Broken links (internal or outbound) | Wastes crawl budget; signals neglect; a 404'd repo link damages credibility | Crawl + check every status |
| **K6** | Mixed content (http on https) | Browser blocks; breaks rendering | Grep for `http://` in src |
| **K7** | Missing canonicals | Duplicate variants compete | Check every route |
| **K8** | Autogenerated filler | Spam policy violation | Read it |
| **K9** | Placeholder social links | Points authority at a stranger; corrupts entity resolution | Verify ownership of every profile URL |
| **K10** | **Hardcoded domain strings** | Breaks Hard Rule 1; migration becomes a repo-wide search-and-replace | `grep -ri "vercel.app\|https://{domain}" src/ public/ scripts/` |
| **K11** | Fabricated metrics | Publishing invented star/fork/commit counts is a truthfulness failure before it is an SEO one | Cross-check against the real source |
| **K12** | Soft 404s | Infinite duplicate URL space | A5 |
| **K13** | Fragment URLs as pages | Declares nonexistent pages | A3 |
| **K14** | Schema not matching visible content | Spam policy violation; manual-action risk | E10 |
| **K15** | Stale downloadable documents (PDF résumé) | A crawlable, AI-readable document contradicting the site | Extract and read every served PDF |
| **K16** | Keyword stuffing, hidden text, cloaking, doorway pages | Manual action | Read the rendered page; compare to raw HTML |

---

## Verification quick-reference

```bash
SITE=https://jadhavomkar.vercel.app

# B1/I7 — what a crawler actually sees
curl -sL $SITE/ | sed 's/<[^>]*>//g' | tr -s '[:space:]' ' '

# A4 — canonical resolves
curl -sL $SITE/ | grep -i canonical
curl -o /dev/null -w "%{http_code}\n" <canonical-url>

# A5 — soft 404
curl -o /dev/null -w "%{http_code}\n" $SITE/definitely-not-real-12345   # want 404

# D3 — exactly one h1
curl -sL $SITE/ | grep -o '<h1' | wc -l                                  # want 1

# K1 — no accidental noindex
curl -sI $SITE/ | grep -i x-robots-tag                                   # want nothing

# K10 — no hardcoded domains
grep -ri "vercel\.app" frontend/src frontend/public frontend/index.html frontend/scripts

# A1/A2/I1 — infra files, correct content types
for f in robots.txt sitemap.xml llms.txt favicon.ico site.webmanifest; do
  curl -o /dev/null -w "$f  %{http_code}  %{content_type}\n" $SITE/$f
done
```

---

**Next:** Phase 2 — audit this site against these rules → [`02-AUDIT.md`](./02-AUDIT.md).
