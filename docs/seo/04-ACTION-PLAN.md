# 04 — YOUR ACTION PLAN

Everything in this document is work only you can do: it needs accounts, credentials, or
decisions that are yours. Written assuming no prior experience with any of these tools.

Six waves of engineering are sitting on the `seo/overhaul` branch. **None of it counts
until Google knows the site exists**, and Google will not find out on its own — a site
with no inbound links and no Search Console property can sit undiscovered indefinitely.

---

## Day 0 — Unblock the deploy (do this first, in this order)

Nothing else in this document matters until the site actually ships. These five steps are
ordered because each depends on the one before it.

### 0.1 — Get the Render backend running

It has been unreachable throughout this work. `/health` and `/actuator/health` both
returned nothing after 82 and 180 seconds.

1. Sign in at **https://dashboard.render.com**
2. Open the **portfolio-backend** service
3. Look at **Events** and **Logs**. The likely cause is the free tier's 750
   instance-hours/month quota being exhausted mid-month — the account has one web service
   plus a Postgres, and a full month is 744 hours, so a single always-on service consumes
   nearly the whole allowance.
4. If suspended: wait for the monthly reset, or upgrade the service.

**Why this blocks everything:** the site is now prerendered at build time, and the build
reads its content from this API. With the backend down, `npm run build:static` fails by
design rather than publishing empty pages.

### 0.2 — Fix the content in the admin panel

The build will refuse to publish until this is done. The prerender step scans every
rendered page for claims that are false, and currently finds seven:

```
/                        "Student"             -> he graduated in 2026
/                        "Open to internships" -> he is employed
/                        "Next.js"             -> withdrawn skill
/                        "FastAPI"             -> withdrawn skill
/                        "Dnyanda"             -> unconfirmed role from demo seed data
/projects/git-portfolio  "Next.js"
/projects/dev-mobiles    "Dnyanda"
```

This content is in the **database**, not the repo, so the code changes already made cannot
remove it. Sign in at `/admin` and fix each section. The corrected values are in the repo
as reference — copy from them:

| Admin section | What to do | Reference file |
|---|---|---|
| **Profile** | Replace headline, bio, status, location, handle. Turn OFF "available for work". | `frontend/src/data/profile.ts` |
| **Profile → socials** | Delete the Twitter/X entry. Add LeetCode and LinkedIn. | same |
| **Profile → current role** | SDE-I, Nonstop IO Technologies, Kharadi Pune, Feb 2026 | same |
| **Experience** | Delete the Dnyanda Solutions entry. Fix the Nonstop IO entries and all three education entries. | `frontend/src/data/experience.ts` |
| **Skills** | Delete Next.js. Rebuild the branches. | `frontend/src/data/skills.ts` |
| **Projects** | Delete all four seeded projects. Add the real ones with **zero or true** star/fork/commit counts. | `frontend/src/data/projects.ts` |

> The bio's first paragraph must be pasted **verbatim** from `CANONICAL_STATEMENT` in
> `frontend/src/lib/identity.ts`. That exact sentence also appears in `llms.txt` and the
> JSON-LD, and the matching is the point — see §5.

### 0.3 — Replace the résumé PDF

The file served at `/api/profile/resume` is still `Omkar_Jadhav_Ace.pdf`. Its text
contains:

- the old phone number **+91 7378729692**
- *"Final-year B.Tech … student"*
- *"Seeking a role to contribute to production AI systems"*
- **Next.js, FastAPI, RAG pipelines, ChromaDB (Vector DB)** in the skills list
- Project 1 described as *"Personal Portfolio with RAG-based Assistant (Next.js, FastAPI,
  Gemini API, ChromaDB) — In Progress"*

Google indexes PDFs and AI assistants weight résumés heavily. Right now `/resume` (correct)
and the PDF (wrong) **contradict each other on the same domain**, and in that conflict the
polished PDF usually wins.

Regenerate it against the facts on `/resume`, then upload via **Admin → Profile → résumé**.

### 0.4 — Set the environment variables in Vercel

1. **https://vercel.com** → your portfolio project → **Settings** → **Environment Variables**
2. Add, ticking **Production, Preview and Development** for each:

| Name | Value |
|---|---|
| `VITE_SITE_URL` | `https://jadhavomkar.vercel.app` |
| `VITE_API_URL` | your Render backend origin, no trailing slash |

`VITE_SITE_URL` is required — the build fails loudly without it, by design. It is the
single value that owns the domain (§4).

3. While you are in Settings, check **Build & Development Settings**:
   - **Root Directory** must be `frontend`
   - **Build Command** should be empty or `npm run build:static` — if it is overridden to
     `npm run build`, prerendering never runs and the site ships as an empty shell again.

### 0.5 — Merge and deploy

```bash
git checkout dev && git merge seo/overhaul
git push origin dev        # or main, per your Vercel production branch
```

Then in Vercel → **Deployments** → **Redeploy**, with **"Use existing Build Cache" unticked**.

**Verify before moving on:**

```bash
curl -sL https://jadhavomkar.vercel.app/ | grep -c "Nonstop IO"      # expect > 0
curl -sI https://jadhavomkar.vercel.app/nope-12345 | head -1          # expect 404
curl -s  https://jadhavomkar.vercel.app/llms.txt | head -3            # expect markdown
curl -sL https://jadhavomkar.vercel.app/ | grep -o 'rel="canonical"[^>]*'
```

Also open the site and check the browser console for React **hydration warnings** — that
is the one thing from Wave 1 never verified in a real browser.

---

## 1 — Google Search Console

Free. This is how Google tells you what it thinks of your site, and how you ask it to look
at a page now instead of eventually.

### 1.1 — Create the property

1. Go to **https://search.google.com/search-console** and sign in with your Google account.
2. You will be offered two property types. **Choose "URL prefix"**, the right-hand box.
   - *Domain* properties verify by DNS TXT record. You do not control DNS for
     `vercel.app`, so that option cannot work. When you buy your own domain you will use
     Domain instead — see §4.
3. Enter exactly: `https://jadhavomkar.vercel.app` — then **Continue**.

### 1.2 — Verify ownership — use the HTML tag, not the HTML file

Google offers several methods. **Pick "HTML tag".**

> **Why this specifically.** The obvious choice is "HTML file upload", and on most sites it
> is the easiest. It will give you trouble here: `frontend/vercel.json` sets
> `"cleanUrls": true`, which makes Vercel strip `.html` and redirect `/googleabc.html` to
> `/googleabc`. Google's verifier requests the `.html` URL and expects a direct 200. The
> meta tag has no such problem, and because every page is prerendered from one template,
> one tag covers all 14 URLs.

1. Expand **HTML tag** and copy the `content` value — it looks like
   `<meta name="google-site-verification" content="AbC123..." />`
2. Open `frontend/index.html` and paste the tag inside `<head>`, just after the
   `<meta name="viewport">` line.
3. Commit, push, and let Vercel deploy.
4. Confirm it is live: `curl -sL https://jadhavomkar.vercel.app/ | grep google-site-verification`
5. Back in Search Console, click **Verify**.

Leave the tag in place permanently. Removing it un-verifies the property.

### 1.3 — Submit the sitemap

1. Left sidebar → **Sitemaps**
2. In "Add a new sitemap", type `sitemap.xml` and **Submit**
3. Status becomes "Success" within minutes to a day. It should report **14 discovered URLs**.

### 1.4 — Ask Google to index each page now

Submitting a sitemap is a hint. URL Inspection is a request.

For each of these, paste the full URL into the **search box at the top** of Search Console,
wait for the check, then click **Request Indexing**:

```
https://jadhavomkar.vercel.app/
https://jadhavomkar.vercel.app/about
https://jadhavomkar.vercel.app/projects
https://jadhavomkar.vercel.app/experience
https://jadhavomkar.vercel.app/education
https://jadhavomkar.vercel.app/resume
https://jadhavomkar.vercel.app/mcp
https://jadhavomkar.vercel.app/recruiter
```

There is a daily quota of roughly 10–12 requests, so the six project pages can wait a day.

While you are there, on the homepage inspection click **View Crawled Page → HTML** and
confirm you can see your name and "Nonstop IO Technologies" in it. That is the single check
that proves the prerendering did its job.

### 1.5 — Reading the reports

Nothing useful appears for **3–7 days**. Do not panic at an empty dashboard.

- **Pages** (once called Coverage) — how many URLs are indexed and why the rest are not.
  Normal reasons: *"Crawled – currently not indexed"* (Google saw it, judged it low value —
  common for a brand-new site), *"Discovered – currently not indexed"* (queued).
  **Not normal:** *"Excluded by 'noindex' tag"*, *"Redirect error"*, *"Not found (404)"*,
  *"Alternate page with proper canonical tag"* on a page that should be canonical itself.
- **Enhancements / Rich results** — where the JSON-LD shows up. Expect `Breadcrumbs` to
  appear. `Person` will not produce a rich result; that is fine and expected, because the
  entity graph exists for disambiguation and AI answers, not for a SERP badge.
- **Performance** — impressions (you appeared), clicks, average position, and the actual
  **queries** people used. This is the only place you learn what the site really ranks for.
  Set the date range to **3 months** once you have data.

### 1.6 — Also worth doing once

Run the **Rich Results Test** at https://search.google.com/test/rich-results on
`https://jadhavomkar.vercel.app/` and on one project page. This is the validation that
could not be run during development, because it requires a live public URL. Expect clean,
with an informational note that `Person` is not eligible for a rich result.

---

## 2 — Bing Webmaster Tools and IndexNow

Worth twenty minutes even though Bing's market share is small: **ChatGPT's web search is
Bing-backed**, so Bing indexing feeds an AI surface you specifically care about.

### 2.1 — Account and import

1. **https://www.bing.com/webmasters** → sign in (Microsoft, Google or Facebook account).
2. Choose **Import from Google Search Console** — it carries the property and the
   verification across, and is far quicker than verifying again.
3. If you would rather not connect the accounts: **Add site manually**, enter
   `https://jadhavomkar.vercel.app`, and verify with the **meta tag** option, added next to
   the Google one in `frontend/index.html`.

### 2.2 — Sitemap and URL submission

1. **Sitemaps** → **Submit sitemap** → `https://jadhavomkar.vercel.app/sitemap.xml`
2. **URL Submission** → paste all 14 URLs at once. Bing's quota is generous (thousands per
   day) compared with Google's ~10.

### 2.3 — IndexNow

IndexNow pushes changed URLs to participating engines instead of waiting to be crawled.
**Google does not participate** — this is for Bing, Yandex, Seznam and Naver.

1. In Bing Webmaster Tools → **IndexNow** → **Generate key**. Copy it.
2. Create `frontend/public/<your-key>.txt` containing **only the key**, nothing else.
3. Commit, push, deploy. Verify: `curl -s https://jadhavomkar.vercel.app/<your-key>.txt`
   - The `cleanUrls` problem from §1.2 does not apply here — Vercel only strips `.html`.
4. Submit on each deploy with a single request:

```bash
curl -X POST https://api.indexnow.org/indexnow \
  -H "Content-Type: application/json" \
  -d '{
    "host": "jadhavomkar.vercel.app",
    "key": "YOUR_KEY",
    "keyLocation": "https://jadhavomkar.vercel.app/YOUR_KEY.txt",
    "urlList": ["https://jadhavomkar.vercel.app/", "https://jadhavomkar.vercel.app/about"]
  }'
```

A 200 or 202 means accepted.

---

## 3 — Analytics

### 3.1 — You already have the analytics that matter

Before adding anything: the backend already records engagement server-side.
`EngagementRecorder` writes `engagement_event` rows for chat, recruiter, MCP and résumé
hits, `TelemetryService` rolls them up for the admin API, and there is a scheduled weekly
digest.

For a portfolio, those **are** the meaningful events — a résumé download or a recruiter
running your fit-match tool tells you far more than a pageview. Check **Admin → Dashboard**
first; you may not need a client-side tool at all.

### 3.2 — If you want pageview data: use Vercel Analytics

**This is a technical constraint, not a preference.** The site ships a strict
Content-Security-Policy:

```
script-src 'self'; connect-src 'self' https://*.onrender.com
```

Vercel Analytics serves its script and receives its beacons from `/_vercel/insights/*` on
**your own origin**, so it works under that policy unchanged. Plausible, Umami, Fathom and
Google Analytics all load a third-party script and post to a third-party domain — each
would require loosening both `script-src` and `connect-src` in `frontend/vercel.json`. That
is a real security trade-off for data you may not need.

To enable:

1. Vercel dashboard → your project → **Analytics** tab → **Enable**
2. `cd frontend && npm install @vercel/analytics`
3. In `frontend/src/main.tsx`, render `<Analytics />` from `@vercel/analytics/react` inside
   the app tree
4. Deploy, then check the Analytics tab after a day

Check Vercel's current free-tier event limits before relying on it.

> **One caveat.** `frontend/vercel.json` sets `Referrer-Policy: no-referrer`, which blanks
> the referrer on outbound requests — so "where did this visitor come from" will be mostly
> empty. If that data matters, change it to `strict-origin-when-cross-origin`, which is
> still a safe default.

### 3.3 — What to actually track

Ignore vanity metrics. For this site the questions worth answering are:

1. Do people who land on `/` reach `/projects` or `/about`? (is the hub working)
2. How many résumé downloads? (the strongest intent signal you have)
3. Does anyone use `/recruiter` or the MCP server? (your two differentiators)
4. Which **Search Console queries** bring people in? (the only real feedback on §5 and §6)

---

## 4 — Domain migration day

You will buy a custom domain. This is the complete runbook. Expect 30–45 minutes plus DNS
propagation.

> **Do this sooner rather than later.** `vercel.app` is on the Public Suffix List, so the
> subdomain inherits no authority from Vercel — and with nothing indexed yet, there is
> nothing to lose in a move. Every week you wait is a week of work accruing to an address
> you intend to abandon. **Buy it before you publish blog content.**
>
> Suggested names, in order: `omkarjadhav.dev`, `omkarjayvantjadhav.com`. The legal-name
> variant has essentially no competition.

### Before the switch

- [ ] **1.** Buy the domain from any registrar (Namecheap, Cloudflare, Porkbun).
- [ ] **2.** Vercel → project → **Settings → Domains → Add**. Add the apex (`example.dev`)
      and `www`.
- [ ] **3.** At your registrar, create the DNS records Vercel displays — an `A` record for
      the apex, a `CNAME` for `www`.
- [ ] **4.** Wait for Vercel to show **Valid Configuration** and issue the TLS certificate.
      Usually minutes; can take hours.

### The switch

- [ ] **5.** Vercel → Settings → Domains → set the custom domain as **Primary**.
- [ ] **6.** On the `jadhavomkar.vercel.app` row: **Edit → Redirect to** your new domain.
      Vercel issues a **308 Permanent Redirect**.
      ⚠️ **Do not delete the subdomain.** Deleting it drops the redirect and strands every
      link that already exists.
- [ ] **7.** Settings → Environment Variables → change **`VITE_SITE_URL`** to the new
      origin, for **Production, Preview and Development**. **This is the one value.**
- [ ] **8.** Render dashboard → portfolio-backend → Environment → update
      **`CORS_ALLOWED_ORIGIN`** and **`APP_FRONTEND_URL`** to the new origin. Save.
      ⚠️ Skipping this blocks every API call from the new domain and the site renders its
      failure state.
- [ ] **9.** Vercel → Deployments → **Redeploy**, **without** the build cache.

### Verify — do not skip

- [ ] **10.** `curl -sI https://jadhavomkar.vercel.app/ | head -1` → expect **308**
- [ ] **11.** `curl -sL https://NEW-DOMAIN/ | grep canonical` → new domain
- [ ] **12.** `curl -s https://NEW-DOMAIN/sitemap.xml` and `/robots.txt` and `/llms.txt` →
      new domain throughout
- [ ] **13.** `curl -sL https://NEW-DOMAIN/ | grep -o '"@id":"[^"]*"' | head` → every
      JSON-LD `@id` on the new domain
- [ ] **14.** Load the site — content renders (proves step 8 worked)

### Search infrastructure

- [ ] **15.** Search Console → **Add property** → this time choose **Domain** (you now
      control DNS) → verify with the TXT record your registrar lets you add.
- [ ] **16.** Submit the sitemap on the new property. URL-inspect and request indexing for
      the main pages.
- [ ] **17.** Old property → **Settings → Change of Address** → select the new property.
      ⚠️ This requires the old property to still exist — which is why §1 says create it now
      rather than waiting for the domain.
- [ ] **18.** Bing Webmaster Tools → add the new site → use the **Site Move** tool.
- [ ] **19.** IndexNow: new key file on the new domain, resubmit all URLs.

### External links — every one

- [ ] **20.** GitHub: profile **website** field, profile README, pinned repo descriptions,
      and the `personal_portfolio` repo's **About → Website**
- [ ] **21.** LinkedIn: Contact info website, Featured section, About text
- [ ] **22.** LeetCode profile website field
- [ ] **23.** The résumé PDF — the file on the site **and** every copy you have sent out
- [ ] **24.** Email signature
- [ ] **25.** Any dev.to / Hashnode / directory profiles from §6

### What to expect

With nothing indexed today, there is no ranking to lose. The cost is the **1–3 weeks**
Google takes to discover and index the new URLs — during which the site ranks for nothing,
exactly as it does now.

(If you migrate *after* building real rankings, the usual pattern is a 2–6 week fluctuation
with 10–30% temporary traffic movement while the 308s are consumed.)

---

## 5 — Profiles and reciprocal linking

**This is the highest-value section in the document**, and the one most likely to be
skipped because it is not technical.

`sameAs` in your JSON-LD claims that the person on this site is the same person as those
GitHub, LinkedIn and LeetCode accounts. Google **checks that claim by looking for a link
back**. Right now none of the three links to your site, so the strongest disambiguation
signal you have is only half-built — and you are one of at least fifteen software engineers
named Omkar Jadhav.

### 5.1 — Things currently wrong on your live profiles

Found by reading them:

| Where | What it says | Problem |
|---|---|---|
| GitHub bio | *"SDE **Intern** @ Nonstop IO \| … \| **Final-year** CS @ KIT, 2026"* | Two false claims. You are an SDE-I and you graduated. |
| GitHub profile | no website link | The reciprocal link that makes `sameAs` work |
| GitHub profile | no profile README | Your highest-authority controllable page, unused |
| Repo `full-notes` | description *"Notes for AI with **fastAPI**"* | A withdrawn technology, publicly visible on your profile |
| Repos `portfoilio`, `interview_prepartion` | misspelled names | Public and permanent-looking |
| `Image_Restoration`, `Image-Restoration`, `Image_Restoration-MP` | three near-duplicates | Clutter |
| Most repos | no description, no topics | Invisible to GitHub search and to anyone skimming |

### 5.2 — GitHub

**Bio** (Settings → Public profile) — replace with:

```
Software Development Engineer I at Nonstop IO Technologies, Pune. Backend in C#, NestJS and SQL.
```

**Website** field: `https://jadhavomkar.vercel.app` — this is the reciprocal link. Do not skip it.
**Location**: `Pune, India`

**Profile README.** Create a repository named exactly **`omkarjadhav1011`** (same as your
username), tick "Add a README", and open `README.md`. Start it with the canonical statement
**verbatim** — copy from `CANONICAL_STATEMENT` in `frontend/src/lib/identity.ts`:

```markdown
# Omkar Jayvant Jadhav

Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO Technologies in
Kharadi, Pune, India. He graduated from KIT's College of Engineering (Autonomous),
Kolhapur in 2026 with a B.Tech in Computer Science & Engineering (Data Science), and works
on backend development in C#, NestJS and SQL.

- Portfolio: https://jadhavomkar.vercel.app
- LinkedIn: https://www.linkedin.com/in/omkar-jadhav-st/
- LeetCode: https://leetcode.com/u/jadhav_omkar1013/

## What I work with
C# · NestJS · SQL · Java · Spring Boot · React · TypeScript · Python · PostgreSQL · Docker
```

**Pinned repos** — pin six, in the order they appear on `/projects`, each with a
description and topics:

| Repo | Description | Topics |
|---|---|---|
| `personal_portfolio` | Git-themed portfolio: Spring Boot API, React SPA, AI assistant with multi-provider LLM failover, public MCP server | `spring-boot` `react` `typescript` `postgresql` `mcp` `java` |
| `InterviewAI` | Voice-driven interview practice — speech recognition plus the Gemini API to score spoken answers | `python` `gemini-api` `speech-recognition` |
| `expense-tracker` | Full-stack expense manager: React frontend, Spring Boot REST API, PostgreSQL | `react` `spring-boot` `postgresql` `rest-api` |
| `Mobile_Shop` | PHP and MySQL e-commerce site with customer accounts, cart, orders and an admin back office | `php` `mysql` `ecommerce` |
| `crop-recommendation` | Flask app recommending a crop from seven soil and climate readings | `python` `flask` `machine-learning` |
| `student-management-rest-api` | Spring Boot 3 REST API with JPA, validation and pagination | `spring-boot` `jpa` `rest-api` |

Also: set **About → Website** on `personal_portfolio` to your site URL, and fix the
`full-notes` description so FastAPI is not on your profile.

### 5.3 — LinkedIn

Your URL is `https://www.linkedin.com/in/omkar-jadhav-st/`.

> ⚠️ **Note for the future:** `linkedin.com/in/omkarjadhav` — the shorter slug — belongs to
> a **different Omkar Jadhav** (Dropouts Technologies LLP, University of Pune 2005–2009).
> It was published on your site for months. Never "simplify" your URL to it.

- **Headline:** `Software Development Engineer I at Nonstop IO Technologies | C# · NestJS · SQL`
- **About:** open with the canonical statement **verbatim**, then two or three sentences on
  the audit-logging and Report Builder work, then a line pointing at the portfolio.
- **Experience:** two entries, not one —
  *Software Development Engineer I*, Nonstop IO Technologies, **Aug 2026 – Present**, and
  *Software Developer Intern*, same company, **Feb 2026 – Aug 2026**. The progression reads
  better than a single merged entry and it matches your site and schema exactly.
- **Education:** the full official names — *KIT's College of Engineering (Autonomous),
  Kolhapur*; *Institute of Civil and Rural Engineering, Gargoti*. Abbreviations will not
  match the `alumniOf` entities.
- **Contact info → Website:** your site URL. **This is the reciprocal link.**
- **Featured:** add the site, plus your two strongest project pages.

### 5.4 — LeetCode

Profile → Edit → **Website**: your site URL. That completes the third reciprocal link.

### 5.5 — The rule underneath all of this

Every fact must be **identical** across the site, the résumé, GitHub and LinkedIn — same
job title, same employer spelling ("Nonstop IO Technologies", not "NonStop io" or
"NonstopIO"), same dates, same institution names. Contradiction between sources is exactly
what stops an entity resolving. Three surfaces agreeing is worth more than any single one
being eloquent.

---

## 6 — Backlinks you can realistically get

You have zero. Volume is not achievable and not the goal; relevance is. Nothing here is a
scheme — no paid links, no exchanges, no directories that exist only for links.

**Do these first — you control them completely:**

1. **GitHub profile README** → your site (§5.2). Highest value, five minutes.
2. **Repo About → Website** on every public repo worth showing.
3. **GitHub topics** on each pinned repo — real discovery surface for tech queries.
4. **LinkedIn** website field + Featured (§5.3).
5. **LeetCode** website field (§5.4).

**Then, as you publish the Wave 5 articles:**

6. **dev.to** and **Hashnode** — cross-post each article with `canonical_url` pointing at
   your site. Both support it explicitly. You get their reach; your domain keeps the
   ranking credit. Never cross-post without the canonical.
7. **Peerlist** (`peerlist.io`) — strong in the Indian developer market, real profile page,
   links out to your site.

**Slower, higher-value:**

8. **KIT Kolhapur alumni / placement pages.** Ask the placement cell whether they publish
   alumni profiles. A `.edu`-adjacent institutional link is worth more than a dozen
   directory entries, and it directly reinforces the `alumniOf` claim.
9. **Pune tech meetups** — speaker and attendee profiles usually link out.
10. **Open-source contributions.** A merged PR often earns a contributor listing. Your MCP
    server work is unusual enough to be worth writing up where Spring AI users gather.

**Never:** paid links, guest-post farms, comment spam, link exchanges, or anything
described as "SEO backlink packages". They are detectable and the penalty outlasts the
benefit.

---

## 7 — Wikidata and knowledge panels

**The honest answer: not now, and chasing it would be a waste of your time.**

A Wikidata item requires **notability** — roughly, being the subject of significant
coverage in multiple independent, reliable sources. Not your own site, not your LinkedIn,
not your employer's page. An item created for someone who does not meet that bar gets
deleted, and creating one about yourself is explicitly discouraged.

A Google knowledge panel is downstream of the same thing: Google needs both high confidence
about the entity and enough independent corroboration to justify the space.

**What would actually change it,** over years rather than months: conference talks with
published material, technical writing that other people cite, significant open-source work
with real adoption, or press coverage of something you built.

**What to do instead:** the `Person` entity graph already shipped in Wave 2 is the
*achievable* version of this. It will not draw a panel, but it does let Google and AI
assistants resolve "Omkar Jadhav" — with your employer, your institutions and your code —
to one person rather than fifteen. That is the realistic win, and it is already built.

---

## 8 — Monitoring

### Weekly — 5 minutes

- Search Console → **Performance**. Are impressions non-zero and trending up?
- Search Console → **Pages**. Did the indexed count drop?
- Google `site:jadhavomkar.vercel.app` — how many pages are indexed?

### Monthly — 20 minutes

- Search **your own name** and the qualified variants — `Omkar Jadhav Nonstop IO`,
  `Omkar Jadhav KIT Kolhapur`, `Omkar Jayvant Jadhav`. Note the position. These are the
  realistic targets; the bare name is a long game.
- Ask **ChatGPT, Claude, Perplexity and Google AI Overviews**: *"Who is Omkar Jadhav?"*
  Record whether the answer describes **you** and whether it cites your site. This is the
  actual scoreboard for Wave 6, and it means nothing until the site has been crawled — give
  it 4–8 weeks after §1.
- Search Console → **Performance → Queries**. What are people actually searching?
- Check the three reciprocal links still exist and still point at the right URL.

### Quarterly — 1 hour

- Re-run the Rich Results Test on `/` and a project page.
- PageSpeed Insights on `/` — mobile and desktop. Wave 4's numbers were build-output
  analysis, not field data; this is where you get the real ones.
- Re-read `/about` and `/resume` against reality. Job title still right? Tenure string in
  the profile is **manual and does not recompute** — check it.
- Check the résumé PDF still matches the site.

### What actually signals a problem

| Signal | Severity | Likely cause |
|---|---|---|
| Indexed pages drop to 0 | 🔴 Critical | `noindex` shipped, robots.txt broken, or canonical pointing somewhere wrong — exactly the failure this whole project started from |
| Search Console shows a **Manual action** | 🔴 Critical | Read it immediately; almost always bad links or spam markup |
| "Crawled – currently not indexed" on most pages | 🟠 High | Google finds the content too thin or too new. More likely §6 than a bug |
| Impressions flat at zero after 6 weeks | 🟠 High | Nothing is linking to you — work §5 and §6 |
| Rich results errors appear | 🟡 Medium | Schema broke, probably because content changed underneath it |
| Position drifting down slowly | 🟢 Normal | Competitors moving. Not a fire |

**A realistic timeline.** Indexing in 1–2 weeks. First impressions in 2–4 weeks. Ranking
for qualified name queries in 2–4 months. The bare `Omkar Jadhav` query, against a Google
engineer and several seniors, is a 12-month-plus project — and may never be fully winnable.
That is not a reason to skip the work; it is a reason to target the queries you *can* win,
which is what `03-KEYWORD-MAP.md` lays out.

---

## The short version

If you only do five things:

1. **Fix the admin content and replace the résumé PDF** (§0.2, §0.3) — until then the site
   publishes claims that are false, and the build will refuse to deploy.
2. **Deploy** (§0.5).
3. **Create the Search Console property and submit the sitemap** (§1) — six waves of work
   are invisible until you do.
4. **Update the GitHub bio and add the website link** (§5.2) — ten minutes, and it is half
   of your strongest disambiguation signal.
5. **Buy the domain** (§4) — sooner is strictly cheaper.
