-- ============================================================================
-- Corrects the live portfolio content.
--
-- WHY THIS EXISTS: the false claims on the site live in the DATABASE, not the
-- repo, so none of the code changes on the seo/overhaul branch can remove them.
-- The prerender build refuses to publish while they are present. Normally you
-- would fix this in the admin panel at /admin; this script is the same set of
-- edits as SQL, for when the backend is down.
--
-- HOW TO RUN
--   Neon console -> project "portfolio" -> SQL Editor -> paste -> Run
--   or:  psql "<your Neon direct connection string>" -f fix-production-content.sql
--
-- SAFETY
--   - Wrapped in a transaction: it all applies or none of it does.
--   - Idempotent: safe to run twice.
--   - It DELETES seeded demo rows. Those were fabricated (invented star/fork/
--     commit counts, an unconfirmed employer) so there is nothing to preserve,
--     but take a Neon branch first if you want a restore point.
--
-- PREREQUISITE: set SEED_DEMO_DATA=false on the Render service first, or the
-- seeder re-inserts the deleted rows on the next restart.
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. PROFILE
--    Was: "B.Tech CSE (Data Science) Student & Full-Stack Developer",
--    "Open to internships & collaborations", available_for_work = true,
--    Kolhapur, and a currentRole of "Full-Stack Developer Intern" at
--    "NonStop io Technologies" starting "Mar 2024" — 23 months before he
--    actually joined.
--
--    The first paragraph of the bio is CANONICAL_STATEMENT from
--    frontend/src/lib/identity.ts, verbatim. It must stay byte-identical to the
--    copy in llms.txt and the JSON-LD description: corroboration across sources
--    works on matching, and a reworded version fragments the signal.
-- ---------------------------------------------------------------------------
UPDATE profile SET
    name               = 'Omkar Jadhav',
    -- The real GitHub username. PRCard falls back to
    -- github.com/<handle>/<slug> when a project has no repoUrl, so a wrong
    -- handle silently generates 404 links.
    handle             = 'omkarjadhav1011',
    headline           = 'Software Development Engineer I at Nonstop IO Technologies',
    bio                = $bio$Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, India. He graduated from KIT's College of Engineering (Autonomous), Kolhapur in 2026 with a B.Tech in Computer Science & Engineering (Data Science), and works on backend development in C#, NestJS and SQL.

He implemented end-to-end user audit functionality on an enterprise reporting product, contributed to its Report Builder module, and builds LLM-integrated applications with the Gemini and Hugging Face APIs.$bio$,
    current_status     = 'Building backend services at Nonstop IO Technologies',
    -- He is employed. This flag drove the "Open to internships" badge.
    available_for_work = false,
    email              = 'jadhavomkar101103@gmail.com',
    location           = 'Pune, Maharashtra, India',
    -- X/Twitter removed: he does not own that account. LinkedIn is the
    -- omkar-jadhav-st slug — NOT the shorter in/omkarjadhav, which belongs to a
    -- different Omkar Jadhav (Dropouts Technologies LLP). A wrong sameAs tells
    -- Google to merge him with a stranger.
    socials            = $json$[{"label":"GitHub","url":"https://github.com/omkarjadhav1011","icon":"github"},{"label":"LeetCode","url":"https://leetcode.com/u/jadhav_omkar1013/","icon":"leetcode"},{"label":"LinkedIn","url":"https://www.linkedin.com/in/omkar-jadhav-st/","icon":"linkedin"}]$json$,
    fun_facts          = $json$["Solved 210+ problems on LeetCode","Took the long route into engineering: diploma at ICRE Gargoti, then B.Tech at KIT Kolhapur","Wrote the audit-logging layer that tracks user actions across a production reporting product"]$json$,
    stash              = $json$["⑂  Built this site end to end: Spring Boot API, React SPA, PostgreSQL","🤖  Wrote a multi-provider LLM failover router so the assistant survives a dead provider","🔐  Encrypts every vault file with its own AES-256-GCM data key"]$json$,
    -- One spelling of the employer everywhere. Three were live at once.
    current_role_json  = $json${"enabled":true,"title":"Software Development Engineer I","company":"Nonstop IO Technologies","monogram":"N","logoUrl":"","url":"https://nonstopio.com","location":"Kharadi, Pune, Maharashtra, India · On-site","startedAt":"Feb 2026","tenure":"7 mos","accent":"#00ff88"}$json$,
    updated_at         = now();

-- ---------------------------------------------------------------------------
-- 2. EXPERIENCE
--    Removes the "Web Developer Intern, Dnyanda Solutions Pvt. Ltd." entry —
--    it came from demo seed data and is not a confirmed role.
--    Replaces the single "SDE-I / NonstopIO / Aug 2026" row with the real
--    two-step progression, and rewrites education in past tense under the full
--    official institution names (schema alumniOf cannot resolve "KIT College").
-- ---------------------------------------------------------------------------
DELETE FROM commit_entry WHERE hash = 'f3a9b1c';          -- Dnyanda Solutions
DELETE FROM commit_entry WHERE org ILIKE '%dnyanda%';     -- any re-seeded copy

-- Wipe the work/education/cert rows this script owns, then reinsert them.
DELETE FROM commit_entry
 WHERE hash IN ('9c4e1a7','3b7d0f2','0d3f9e1','1e2b4c7','2c3d5e8',
                'a2d8e4f','b5c7f2a','c8e1a94')
    OR org ILIKE '%nonstop%';

INSERT INTO commit_entry
  (id, hash, type, title, org, date, date_end, description, branch, branch_color,
   color_key, tags, url, sort_order, created_at, updated_at)
VALUES
  (gen_random_uuid(), '9c4e1a7', 'job',
   'Software Development Engineer I', 'Nonstop IO Technologies', 'Aug 2026', NULL,
   $json$["Backend development on an enterprise reporting product, contributing to live production modules","Implemented end-to-end user audit functionality tracking and logging user actions across the application for compliance and traceability","Contributed to the Report Builder module and wrote optimized SQL queries for reporting, audit logs, and data-retrieval flows"]$json$,
   'work/nonstop-io', '#00ff88', 'green',
   $json$["C#","NestJS","SQL"]$json$, 'https://nonstopio.com', 1, now(), now()),

  (gen_random_uuid(), '3b7d0f2', 'job',
   'Software Developer Intern', 'Nonstop IO Technologies', 'Feb 2026', 'Aug 2026',
   $json$["Joined the backend team working on the enterprise reporting product in C#, NestJS and SQL","Converted to Software Development Engineer I in August 2026"]$json$,
   'work/nonstop-io', '#00ff88', 'green',
   $json$["C#","NestJS","SQL"]$json$, 'https://nonstopio.com', 2, now(), now()),

  (gen_random_uuid(), '0d3f9e1', 'education',
   'B.Tech, Computer Science & Engineering (Data Science)',
   'KIT''s College of Engineering (Autonomous), Kolhapur', '2023', '2026',
   $json$["Graduated in 2026 with 80%","Coursework: Data Structures & Algorithms, DBMS, Operating Systems, Computer Networks, Data Science"]$json$,
   'edu/btech-cse', '#58a6ff', 'blue',
   $json$["DSA","DBMS","Data Science"]$json$, NULL, 3, now(), now()),

  (gen_random_uuid(), '1e2b4c7', 'education',
   'Diploma, Computer Engineering',
   'Institute of Civil and Rural Engineering, Gargoti', '2020', '2023',
   $json$["Graduated in 2023 with 87%"]$json$,
   'edu/diploma-cse', '#58a6ff', 'blue',
   $json$["C++","DBMS"]$json$, NULL, 4, now(), now()),

  (gen_random_uuid(), '2c3d5e8', 'education',
   'High School (SSC)',
   'Shankar Chakru Patil Madhyamik Vidhyalaya, Dindewadi', '2019', '2020',
   $json$["Completed in 2020 with 94%"]$json$,
   'edu/high-school', '#58a6ff', 'blue',
   $json$[]$json$, NULL, 5, now(), now()),

  -- ⚠ CERTIFICATION DATES ARE UNCONFIRMED. "Jan 2023" and "Mar 2023" were
  -- carried over from pre-existing static data and never verified; the AI/ML
  -- bootcamp has no date at all and is listed by year. Correct or reduce all
  -- three to year-only.
  (gen_random_uuid(), 'a2d8e4f', 'achievement',
   'Python Bootcamp: Zero to Hero', 'Udemy', 'Jan 2023', NULL,
   $json$["Completed the Python programming bootcamp"]$json$,
   'cert/python-bootcamp', '#e3b341', 'yellow',
   $json$["Python","Udemy"]$json$, NULL, 6, now(), now()),

  (gen_random_uuid(), 'b5c7f2a', 'achievement',
   'Java Programming: Beginner to Master', 'Udemy', 'Mar 2023', NULL,
   $json$["Completed the Java programming course covering core Java and OOP"]$json$,
   'cert/java-programming', '#e3b341', 'yellow',
   $json$["Java","OOP","Udemy"]$json$, NULL, 7, now(), now()),

  (gen_random_uuid(), 'c8e1a94', 'achievement',
   'AI, Machine Learning & Data Science Bootcamp', 'Udemy', '2023', NULL,
   $json$["Completed the AI, machine learning and data science bootcamp"]$json$,
   'cert/ai-ml-ds', '#e3b341', 'yellow',
   $json$["Udemy"]$json$, NULL, 8, now(), now());

-- ---------------------------------------------------------------------------
-- 3. SKILLS
--    Removes Next.js, which was published as a live claimed skill, and rebuilds
--    the branches from the confirmed skills list. This list is the on-page
--    mirror of the schema knowsAbout array — structured data may only claim
--    what a reader can see, so the two must match.
--
--    ⚠ ALSO REMOVED, and absent from the confirmed list — re-add only if you
--    want them: Tailwind CSS, C++, MongoDB, NumPy, Pandas, Scikit-learn,
--    Jupyter, jQuery, Bootstrap.
-- ---------------------------------------------------------------------------
DELETE FROM skill_branch;   -- cascades to skill via fk_skill_branch

INSERT INTO skill_branch (id, branch_name, color, branch_offset, created_at, updated_at) VALUES
  ('11111111-0000-4000-8000-000000000001', 'feature/languages', '#58a6ff', 0, now(), now()),
  ('11111111-0000-4000-8000-000000000002', 'feature/backend',   '#00ff88', 1, now(), now()),
  ('11111111-0000-4000-8000-000000000003', 'feature/frontend',  '#f0883e', 2, now(), now()),
  ('11111111-0000-4000-8000-000000000004', 'feature/ai',        '#d2a8ff', 3, now(), now()),
  ('11111111-0000-4000-8000-000000000005', 'feature/tools',     '#e3b341', 4, now(), now()),
  ('11111111-0000-4000-8000-000000000006', 'feature/core-cs',   '#7ee787', 5, now(), now());

INSERT INTO skill (id, name, level, tag, icon, branch_id, created_at, updated_at) VALUES
  (gen_random_uuid(), 'C#',          4, NULL, '#',  '11111111-0000-4000-8000-000000000001', now(), now()),
  (gen_random_uuid(), 'SQL',         4, NULL, '🗄', '11111111-0000-4000-8000-000000000001', now(), now()),
  (gen_random_uuid(), 'JavaScript',  4, NULL, 'JS', '11111111-0000-4000-8000-000000000001', now(), now()),
  (gen_random_uuid(), 'TypeScript',  3, NULL, 'TS', '11111111-0000-4000-8000-000000000001', now(), now()),
  (gen_random_uuid(), 'Python',      4, NULL, '🐍', '11111111-0000-4000-8000-000000000001', now(), now()),
  (gen_random_uuid(), 'Java',        3, NULL, '☕', '11111111-0000-4000-8000-000000000001', now(), now()),

  (gen_random_uuid(), 'NestJS',      4, NULL, '🐱', '11111111-0000-4000-8000-000000000002', now(), now()),
  (gen_random_uuid(), 'Spring Boot', 3, NULL, '🌱', '11111111-0000-4000-8000-000000000002', now(), now()),
  (gen_random_uuid(), 'REST APIs',   4, NULL, '🔌', '11111111-0000-4000-8000-000000000002', now(), now()),
  (gen_random_uuid(), 'PostgreSQL',  3, NULL, '🐘', '11111111-0000-4000-8000-000000000002', now(), now()),
  (gen_random_uuid(), 'MySQL',       4, NULL, '🗃', '11111111-0000-4000-8000-000000000002', now(), now()),
  (gen_random_uuid(), 'PHP',         3, NULL, '🐘', '11111111-0000-4000-8000-000000000002', now(), now()),

  (gen_random_uuid(), 'React',       3, NULL, '⚛', '11111111-0000-4000-8000-000000000003', now(), now()),
  (gen_random_uuid(), 'HTML5',       5, NULL, '🌐', '11111111-0000-4000-8000-000000000003', now(), now()),
  (gen_random_uuid(), 'CSS3',        4, NULL, '🎨', '11111111-0000-4000-8000-000000000003', now(), now()),

  (gen_random_uuid(), 'Gemini API',        4, NULL, '✦',  '11111111-0000-4000-8000-000000000004', now(), now()),
  (gen_random_uuid(), 'Hugging Face API',  3, NULL, '🤗', '11111111-0000-4000-8000-000000000004', now(), now()),
  (gen_random_uuid(), 'Prompt Engineering',3, NULL, '💬', '11111111-0000-4000-8000-000000000004', now(), now()),

  (gen_random_uuid(), 'Git',      5, NULL, '⑂',  '11111111-0000-4000-8000-000000000005', now(), now()),
  (gen_random_uuid(), 'GitHub',   4, NULL, '🐙', '11111111-0000-4000-8000-000000000005', now(), now()),
  (gen_random_uuid(), 'Docker',   3, NULL, '🐳', '11111111-0000-4000-8000-000000000005', now(), now()),
  (gen_random_uuid(), 'Postman',  4, NULL, '📮', '11111111-0000-4000-8000-000000000005', now(), now()),
  (gen_random_uuid(), 'Streamlit',3, NULL, '📊', '11111111-0000-4000-8000-000000000005', now(), now()),
  (gen_random_uuid(), 'VS Code',  5, NULL, '💻', '11111111-0000-4000-8000-000000000005', now(), now()),

  (gen_random_uuid(), 'Data Structures & Algorithms', 4, NULL, '🧮', '11111111-0000-4000-8000-000000000006', now(), now()),
  (gen_random_uuid(), 'OOP',              4, NULL, '🧱', '11111111-0000-4000-8000-000000000006', now(), now()),
  (gen_random_uuid(), 'DBMS',             4, NULL, '🗂', '11111111-0000-4000-8000-000000000006', now(), now()),
  (gen_random_uuid(), 'MVC Architecture', 3, NULL, '🏛', '11111111-0000-4000-8000-000000000006', now(), now());

-- Skills diff. Removes the "Next.js 14 App Router" and "async + FastAPI" rows.
DELETE FROM skill_diff;
INSERT INTO skill_diff (id, name, type, note, sort_order, created_at, updated_at) VALUES
  (gen_random_uuid(), 'C#',          'added',    'day-to-day backend work at Nonstop IO', 0, now(), now()),
  (gen_random_uuid(), 'NestJS',      'added',    'services on the enterprise reporting product', 1, now(), now()),
  (gen_random_uuid(), 'TypeScript',  'added',    'typed everything, including this site', 2, now(), now()),
  (gen_random_uuid(), 'Docker',      'added',    'containerized local dependencies', 3, now(), now()),
  (gen_random_uuid(), 'SQL',         'modified', 'optimizing reporting, audit-log and data-retrieval queries', 4, now(), now()),
  (gen_random_uuid(), 'Spring Boot', 'modified', 'the API behind this portfolio', 5, now(), now());

-- ---------------------------------------------------------------------------
-- 4. PROJECTS
--    Deletes the four seeded rows. Every one carried invented engagement
--    metrics (19 stars, 84 commits, "last commit: just now") that match nothing
--    in the real GitHub account, and three of their repo URLs are 404s.
--
--    Metrics below are ZERO on purpose. Fill them in only with numbers that can
--    be verified.
-- ---------------------------------------------------------------------------
DELETE FROM project WHERE slug IN
  ('git-portfolio', 'dev-mobiles', 'crop-recommendation', 'snapsktch',
   'portfolio-ai-assistant', 'ai-interview-preparation-system',
   'text-to-image-generator', 'expense-tracker');

INSERT INTO project
  (id, slug, repo_name, description, language, language_color, stars, forks,
   commits, last_commit, last_commit_msg, tags, live_url, repo_url, status,
   pinned, long_description, sort_order, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'portfolio-ai-assistant', 'personal_portfolio',
   'This portfolio: a Spring Boot API and React SPA with an AI assistant, multi-provider LLM failover, and a public MCP server.',
   'Java', '#b07219', 0, 0, 0, '', '',
   $json$["Java","Spring Boot","React","TypeScript","PostgreSQL","Docker"]$json$,
   NULL, 'https://github.com/omkarjadhav1011/personal_portfolio', 'active', true,
   'A Git-themed developer portfolio built as a full application rather than a static site. The backend is Spring Boot 3.5 on Java 21 with PostgreSQL and Flyway migrations; the frontend is React 18 with Vite and TypeScript. Beyond the public pages it carries an admin panel, an encrypted document vault using per-file AES-256-GCM data keys, TOTP multi-factor auth, and an AI assistant. The assistant runs on an ordered provider chain (Groq, Cerebras, Mistral, Gemini, OpenRouter) with per-provider circuit breaking and daily quotas, so a dead or rate-limited provider fails over instead of failing. Retrieval is backed by pgvector with Gemini embeddings, and a public read-only MCP server exposes the same query layer so a recruiter''s own AI client can evaluate the work directly.',
   0, now(), now()),

  (gen_random_uuid(), 'ai-interview-preparation-system', 'InterviewAI',
   'Voice-driven interview practice: speech recognition transcribes spoken answers, the Gemini API scores them and returns feedback.',
   'Python', '#3572A5', 0, 0, 0, '', '',
   $json$["Python","Gemini API","Speech Recognition"]$json$,
   NULL, 'https://github.com/omkarjadhav1011/InterviewAI', 'active', true,
   'A voice-driven interview practice system that runs question-and-answer sessions with automated feedback. Speech Recognition converts the spoken answer to text, which feeds an evaluation pipeline; the Gemini API scores the response and generates actionable feedback using structured prompt templates.',
   1, now(), now()),

  -- No repo_url: the previously published /snapsktch link is a 404 and no
  -- repository has been confirmed. A dead code link costs more credibility
  -- than a missing one.
  (gen_random_uuid(), 'text-to-image-generator', 'Text-to-Image Generator',
   'A Streamlit web app that turns a text prompt into an image using a Hugging Face hosted model.',
   'Python', '#3572A5', 0, 0, 0, '', '',
   $json$["Python","Streamlit","Hugging Face API"]$json$,
   NULL, NULL, 'active', true,
   'A Streamlit web app that takes a text prompt and generates a corresponding image via a Hugging Face hosted model. Handles the API integration, the request/response flow, and error handling for failed or slow generations.',
   2, now(), now()),

  (gen_random_uuid(), 'expense-tracker', 'expense-tracker',
   'Full-stack expense manager: React frontend, Spring Boot REST API, PostgreSQL schema with category and monthly aggregation.',
   'Java', '#b07219', 0, 0, 0, '', '',
   $json$["React","Spring Boot","PostgreSQL","REST APIs"]$json$,
   NULL, 'https://github.com/omkarjadhav1011/expense-tracker', 'active', true,
   'A full-stack expense management application with a React frontend and Spring Boot REST API backend. Includes the PostgreSQL schema design and the queries for adding, filtering and aggregating expenses, producing monthly and category-wise spending breakdowns.',
   3, now(), now()),

  -- ⚠ The two below carry DRAFT descriptions, reconstructed by reading the
  -- repositories rather than supplied by the owner. Review before publishing.
  (gen_random_uuid(), 'crop-recommendation', 'crop-recommendation',
   'A Flask web app that recommends a crop from seven soil and climate readings using a trained classifier.',
   'Python', '#3572A5', 0, 0, 0, '', '',
   $json$["Python","Flask","Machine Learning"]$json$,
   NULL, 'https://github.com/omkarjadhav1011/crop-recommendation', 'active', false,
   'DRAFT - pending review. A Flask application that recommends a crop from seven soil and climate inputs: nitrogen, phosphorus, potassium, temperature, humidity, pH and rainfall. A classifier trained on a 2,200-row dataset covering 22 crops is loaded from a pickle file and served behind a small multi-page UI.',
   4, now(), now()),

  (gen_random_uuid(), 'dev-mobiles', 'Mobile_Shop',
   'A PHP and MySQL e-commerce site for a mobile phone store, with customer accounts, cart, orders and an admin back office.',
   'PHP', '#4F5D95', 0, 0, 0, '', '',
   $json$["PHP","MySQL","JavaScript","HTML5","CSS3"]$json$,
   NULL, 'https://github.com/omkarjadhav1011/Mobile_Shop', 'archived', false,
   'DRAFT - pending review. A server-rendered e-commerce application for a mobile phone store, branded "Dev Mobiles", built in PHP against a 13-table MySQL schema. The storefront covers registration and login, product browsing by brand, search, wishlist, cart, checkout with a generated order number, order tracking and invoices. A separate admin area manages products, brands, inventory, order status, customer reviews, enquiries and date-range sales reports.',
   5, now(), now());

COMMIT;

-- ============================================================================
-- VERIFY — every one of these should return zero rows.
-- ============================================================================
SELECT 'profile still stale' AS check, headline FROM profile
 WHERE headline ILIKE '%student%' OR current_status ILIKE '%internship%' OR available_for_work;

SELECT 'withdrawn skill' AS check, name FROM skill
 WHERE name ILIKE '%next.js%' OR name ILIKE '%fastapi%' OR name ILIKE '%chromadb%';

SELECT 'unconfirmed employer' AS check, org FROM commit_entry WHERE org ILIKE '%dnyanda%';

SELECT 'fabricated metrics' AS check, slug, stars, forks, commits FROM project
 WHERE stars > 0 OR forks > 0 OR commits > 0;

SELECT 'dead repo link' AS check, slug, repo_url FROM project
 WHERE repo_url IN ('https://github.com/omkarjadhav1011/git-portfolio',
                    'https://github.com/omkarjadhav1011/dev-mobiles',
                    'https://github.com/omkarjadhav1011/snapsktch');
