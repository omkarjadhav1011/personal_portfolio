# GitHub profile — exact content to paste

Everything here is copy-paste. I cannot make these edits for you: the `gh` CLI is not
installed and I have no GitHub credentials, so all of this needs your account.

Time: about 15 minutes. It is the highest value-per-minute work left in the whole project,
because **the reciprocal link is half of your strongest disambiguation signal** and it does
not currently exist.

---

## Why the website link specifically matters

The JSON-LD shipped in Wave 2 contains this claim:

```json
"sameAs": [
  "https://github.com/omkarjadhav1011",
  "https://leetcode.com/u/jadhav_omkar1013/",
  "https://www.linkedin.com/in/omkar-jadhav-st/"
]
```

That is your site asserting *"the person described here is also the person behind those
three accounts."* Google verifies that assertion by checking whether those accounts link
back. None of the three currently does, so the claim is unverified — and you are one of at
least fifteen software engineers named Omkar Jadhav.

One field on each profile closes the loop.

---

## 1. Bio and website — 2 minutes

**github.com → your avatar → Settings → Public profile**

Current bio:

> SDE **Intern** @ Nonstop IO | Java · Spring Boot · PostgreSQL · React | **Final-year** CS @ KIT, 2026

Two false claims: you are an SDE-I, and you graduated. Replace with:

```
Software Development Engineer I at Nonstop IO Technologies, Pune. Backend in C#, NestJS and SQL.
```

| Field | Value |
|---|---|
| **Bio** | the line above |
| **URL** | `https://jadhavomkar.vercel.app` ← **the reciprocal link — do not skip** |
| **Company** | `@nonstopio` (or `Nonstop IO Technologies`) |
| **Location** | `Pune, India` |

Click **Update profile**.

---

## 2. Profile README — 5 minutes

A repository named exactly the same as your username renders on your profile page. You
don't have one, which means your highest-authority controllable page is blank.

1. **github.com/new**
2. Repository name: **`omkarjadhav1011`** — GitHub will show "✨ You found a secret area"
3. Public · tick **Add a README file** · **Create repository**
4. Edit `README.md` and paste:

````markdown
# Omkar Jayvant Jadhav

Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO Technologies in
Kharadi, Pune, India. He graduated from KIT's College of Engineering (Autonomous),
Kolhapur in 2026 with a B.Tech in Computer Science & Engineering (Data Science), and works
on backend development in C#, NestJS and SQL.

- **Portfolio:** https://jadhavomkar.vercel.app
- **LinkedIn:** https://www.linkedin.com/in/omkar-jadhav-st/
- **LeetCode:** https://leetcode.com/u/jadhav_omkar1013/

## What I work with

**Languages** C# · SQL · JavaScript · TypeScript · Python · Java
**Backend** NestJS · Spring Boot · REST APIs · PostgreSQL · MySQL · PHP
**Frontend** React · HTML5 · CSS3
**AI** Gemini API · Hugging Face API · Prompt Engineering
**Tools** Git · GitHub · Docker · Postman · Streamlit · VS Code

## At work

Backend development on an enterprise reporting product at Nonstop IO Technologies, in C#,
NestJS and SQL. I implemented end-to-end user audit functionality that tracks and logs user
actions across the application for compliance and traceability, and contributed to the
Report Builder module, writing optimized SQL for reporting, audit logs and data-retrieval
flows.

## Things I have built

- **[Portfolio with an AI assistant](https://github.com/omkarjadhav1011/personal_portfolio)**
  — Spring Boot API and React SPA, with a multi-provider LLM failover router, an encrypted
  document vault, and a public read-only MCP server.
- **[InterviewAI](https://github.com/omkarjadhav1011/InterviewAI)** — voice-driven interview
  practice; speech recognition transcribes spoken answers and the Gemini API scores them.
- **[Expense Tracker](https://github.com/omkarjadhav1011/expense-tracker)** — React frontend
  over a Spring Boot REST API with a PostgreSQL schema.
````

> **The first paragraph must stay byte-identical** to the one on your site, in `llms.txt`
> and in the JSON-LD `description`. Source of truth is `CANONICAL_STATEMENT` in
> `frontend/src/lib/identity.ts`. Corroboration across sources works on *matching* —
> rewording it here fragments the signal rather than reinforcing it.

---

## 3. Pinned repositories — 5 minutes

**Your profile → Customize your pins.** Pin these six, in this order — it mirrors
`/projects` on the site.

For each, also open the repo → **About** (gear icon, top right) → set the description, add
the topics, and for `personal_portfolio` set the **Website** field.

| Repo | Description | Topics |
|---|---|---|
| `personal_portfolio` | Git-themed portfolio: Spring Boot API, React SPA, AI assistant with multi-provider LLM failover, public MCP server | `spring-boot` `react` `typescript` `postgresql` `mcp` `java` |
| `InterviewAI` | Voice-driven interview practice — speech recognition plus the Gemini API to score spoken answers | `python` `gemini-api` `speech-recognition` `ai` |
| `expense-tracker` | Full-stack expense manager: React frontend, Spring Boot REST API, PostgreSQL | `react` `spring-boot` `postgresql` `rest-api` |
| `Mobile_Shop` | PHP and MySQL e-commerce site with customer accounts, cart, orders and an admin back office | `php` `mysql` `ecommerce` |
| `crop-recommendation` | Flask app recommending a crop from seven soil and climate readings | `python` `flask` `machine-learning` |
| `student-management-rest-api` | Spring Boot 3 REST API with JPA, validation and pagination | `spring-boot` `jpa` `rest-api` `java` |

**Set `personal_portfolio` → About → Website to `https://jadhavomkar.vercel.app`.** That is
a second reciprocal link and it costs one click.

---

## 4. Clean-up — 3 minutes

Found by reading your public repository list:

| Repo | Problem | Fix |
|---|---|---|
| `full-notes` | Description reads *"Notes for AI with **fastAPI**"* | FastAPI is a withdrawn technology and this puts it on your public profile. Reword or clear the description. |
| `portfoilio` | Misspelled name, public | Rename, archive, or make private. It is also a duplicate of `portfolio`. |
| `interview_prepartion` | Misspelled name ("prepartion") | Rename to `interview-preparation`. GitHub redirects the old URL. |
| `Image_Restoration`, `Image-Restoration`, `Image_Restoration-MP` | Three near-duplicates | Keep the best one, archive the others. |
| Most repos | No description | Add one line each. Repos with no description are invisible in GitHub search and read as abandoned. |

Renaming a repo is safe — **Settings → General → Repository name → Rename**. GitHub keeps
redirects from the old URL.

---

## 5. LinkedIn and LeetCode — 5 minutes

Same principle, and both are needed for the `sameAs` set to verify.

**LinkedIn** (`linkedin.com/in/omkar-jadhav-st/`)

- **Contact info → Website** → `https://jadhavomkar.vercel.app` ← the reciprocal link
- **Headline** → `Software Development Engineer I at Nonstop IO Technologies | C# · NestJS · SQL`
- **About** → open with the canonical statement, verbatim, then the work paragraph above
- **Experience** → two entries, not one:
  - *Software Development Engineer I* · Nonstop IO Technologies · **Aug 2026 – Present**
  - *Software Developer Intern* · Nonstop IO Technologies · **Feb 2026 – Aug 2026**
- **Education** → full official names: *KIT's College of Engineering (Autonomous), Kolhapur*
  and *Institute of Civil and Rural Engineering, Gargoti*. Abbreviations will not match the
  `alumniOf` entities in your schema.

> ⚠️ Never shorten your LinkedIn URL to `linkedin.com/in/omkarjadhav`. That belongs to a
> **different Omkar Jadhav** — Dropouts Technologies LLP, University of Pune 2005–2009. It
> was published on your site for months before it was caught.

**LeetCode** (`leetcode.com/u/jadhav_omkar1013/`)

- Profile → Edit → **Website** → `https://jadhavomkar.vercel.app`

---

## Checklist

- [ ] GitHub bio replaced (no "Intern", no "Final-year")
- [ ] GitHub **URL field** set ← the one that matters most
- [ ] Profile README repo created and filled
- [ ] Six repos pinned, each with a description and topics
- [ ] `personal_portfolio` → About → Website set
- [ ] `full-notes` description fixed (removes FastAPI from your profile)
- [ ] LinkedIn website field set
- [ ] LinkedIn headline, About, Experience ×2, Education updated
- [ ] LeetCode website field set

When all three website fields are set, the `sameAs` graph is reciprocal and verifiable —
which is the single strongest thing you can do to separate yourself from the other fourteen.
