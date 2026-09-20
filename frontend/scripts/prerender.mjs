/**
 * Build-time prerender: turns every public route into real, crawlable HTML.
 *
 * Runs after `vite build` (client) and `vite build --ssr` (server). For each
 * route it seeds a React Query cache from the live API, renders the route with
 * `react-dom/server`, injects the result plus per-route <head> tags into the
 * built `index.html`, and writes `dist/<route>/index.html`.
 *
 * It fails the build — loudly, non-zero — if any route renders a loading or
 * error state. That guard is the whole point: this prerenders against a
 * free-tier API that cold-starts, and a silent failure would bake skeletons (or
 * the literal string "fatal: failed to load portfolio data from the backend")
 * into the published HTML. It would look fine in a browser, because hydration
 * repairs it, while every crawler recorded the broken version.
 */
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { loadEnv } from "vite";

const frontendDir = fileURLToPath(new URL("..", import.meta.url));
const distDir = resolve(frontendDir, "dist");
const ssrEntry = resolve(frontendDir, "dist-ssr/entry-server.js");

const env = loadEnv(process.env.NODE_ENV ?? "production", frontendDir);

// Resolved in main() once the SSR bundle is loaded: `normalizeSiteUrl` lives in
// TypeScript, and re-implementing it here would give the domain a second place
// to be wrong — exactly what VITE_SITE_URL exists to prevent. The built bundle
// re-exports it, so this script uses the same validator as the app.
let SITE_URL = "";

/**
 * Where to read content from at build time. PRERENDER_API_URL exists so a local
 * build can point at localhost:8081 without changing the value the client ships
 * with. In CI/Vercel, VITE_API_URL is the Render backend and is what gets used.
 */
const API_URL = (process.env.PRERENDER_API_URL ?? env.VITE_API_URL ?? "").replace(/\/+$/, "");
if (!API_URL) {
  fail(
    "No API base URL.\n" +
      "Prerendering reads the site's content from the backend, so one of these must be set:\n" +
      "  PRERENDER_API_URL  (build-time only, e.g. http://localhost:8081)\n" +
      "  VITE_API_URL       (the value the client ships with)\n",
  );
}

/** Query keys, mirrored from src/api/*.ts. A drift here silently un-seeds a page. */
const ENDPOINTS = [
  { key: ["profile"], path: "/api/profile" },
  { key: ["projects"], path: "/api/projects" },
  { key: ["skill-branches"], path: "/api/skills/branches" },
  { key: ["skill-diff"], path: "/api/skills/diff" },
  { key: ["experience"], path: "/api/experience" },
];

/**
 * Sentinels that mean a route rendered the wrong thing. Drawn from the actual
 * copy in Home.tsx and ProjectDetail.tsx rather than guessed.
 */
const FAILURE_MARKERS = [
  "failed to load portfolio data", // Home.tsx, all five queries failed
  "did not match any project", // ProjectDetail.tsx, unknown slug
  // Every skeleton renders through SkeletonContainer, which sets aria-busy.
  // This replaced a check for the Tailwind class "animate-pulse", which also
  // matches a decorative status dot on the avatar and flagged good renders.
  'aria-busy="true"',
];

/** Proof the render actually produced content, not an empty shell. */
const REQUIRED_MARKER = "Omkar Jadhav";

/** A real page is tens of KB; anything this small is a shell or an error card. */
const MIN_MARKUP_BYTES = 4096;

/**
 * Claims that must never be published again, checked against the rendered text
 * of every page.
 *
 * These are not style preferences — each one was live on the site and each one
 * is false. He graduated in 2026 and is employed; he is not a student, not an
 * intern, and not looking for work. Next.js, FastAPI, ChromaDB and vector
 * databases were withdrawn from his skill set. The Dnyanda Solutions role and
 * the 7378729692 number are stale.
 *
 * Deliberately NOT listed: "RAG". The portfolio project may describe its own
 * retrieval implementation factually — that is a statement about software, not
 * a personal skill claim.
 *
 * Most of this content lives in the database rather than the repo, so fixing
 * the code alone cannot prevent it coming back. This check can: the build stops
 * before a false claim reaches a crawler. Set SKIP_CONTENT_CHECK=1 to override,
 * but understand what that means before you do.
 */
const FORBIDDEN_CONTENT = [
  [/\bstudents?\b/i, "he graduated in 2026 — present tense student copy is false"],
  [/open to internships/i, "he is employed at Nonstop IO Technologies"],
  [/\bfreshers?\b/i, "he is employed"],
  [/final[\s-]year/i, "he graduated"],
  [/looking for opportunit/i, "he is employed"],
  [/\bNext\.js\b/i, "withdrawn from his claimed skills"],
  [/\bFastAPI\b/i, "withdrawn from his claimed skills"],
  [/\bChromaDB\b/i, "withdrawn from his claimed skills"],
  [/vector database/i, "withdrawn from his claimed skills"],
  [/Dnyanda/i, "not a confirmed role — came from demo seed data"],
  [/7378729692/, "old phone number"],
];

function fail(message) {
  console.error(`\n[prerender] BUILD FAILED\n\n${message}\n`);
  process.exit(1);
}

/**
 * Fetches JSON with retries. The backend is on Render's free tier and cold
 * starts have been measured at ~7s, so the first attempt routinely times out on
 * an idle service. Retrying is the difference between a reliable deploy and one
 * that fails whenever nobody has visited the site recently.
 */
async function fetchJson(path, { attempts = 4, timeoutMs = 45_000 } = {}) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(`${API_URL}${path}`, { signal: controller.signal });
      if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
      return await res.json();
    } catch (error) {
      lastError = error;
      const wait = attempt * 3000;
      console.warn(
        `[prerender] ${path} attempt ${attempt}/${attempts} failed (${error.message})` +
          (attempt < attempts ? ` — retrying in ${wait / 1000}s` : ""),
      );
      if (attempt < attempts) await new Promise((r) => setTimeout(r, wait));
    } finally {
      clearTimeout(timer);
    }
  }
  throw new Error(`${path}: ${lastError?.message ?? "unknown error"}`);
}

/**
 * Escapes a JSON payload for embedding in a <script type="application/json">
 * block. Only the angle brackets matter: they are what would otherwise let a
 * "</script>" sequence inside the data close the element early. The U+2028 and
 * U+2029 escaping usually paired with this is unnecessary — those characters
 * are only a hazard to a JavaScript parser, and this block is never parsed as
 * JavaScript.
 */
function toScriptJson(value) {
  return JSON.stringify(value).replace(/</g, "\\u003c").replace(/>/g, "\\u003e");
}

function escapeAttr(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function absolute(route) {
  return `${SITE_URL}/${route.replace(/^\/+/, "")}`;
}

/**
 * Rewrites the built index.html for one route: swaps in the rendered markup,
 * the cache seed, and route-specific <title>, description and canonical.
 *
 * Per-route <head> tags have to be written here because the app has no head
 * manager — `useDocumentTitle` sets `document.title` from an effect, which never
 * runs during server rendering and is invisible to a crawler regardless. Until
 * this step existed, every URL on the site served one identical <head>.
 */
function buildPage(template, { markup, seed, title, description, canonical, noindex, jsonLd }) {
  let html = template;

  html = html.replace(
    '<div id="root"></div>',
    // The seed rides along as a JSON data block, not executable JavaScript. A
    // <script type="application/json"> element is never run, so the site's
    // Content-Security-Policy keeps script-src at 'self'; an inline script
    // would have forced 'unsafe-inline' on every page.
    `<div id="root">${markup}</div>\n    <script type="application/json" id="__rq-seed__">${toScriptJson(seed)}</script>`,
  );

  html = html.replace(/<title>[\s\S]*?<\/title>/, `<title>${escapeAttr(title)}</title>`);

  html = html.replace(
    /<meta\s+name="description"[\s\S]*?\/>/,
    `<meta name="description" content="${escapeAttr(description)}" />`,
  );

  html = html.replace(
    /<link rel="canonical"[^>]*\/>/,
    `<link rel="canonical" href="${escapeAttr(canonical)}" />`,
  );

  // og:url must track the canonical, or a share card claims a different page.
  html = html.replace(
    /<meta property="og:url"[^>]*\/>/,
    `<meta property="og:url" content="${escapeAttr(canonical)}" />`,
  );

  if (jsonLd) {
    // Emitted in <head>, once per page, carrying the whole @graph. Every node
    // has a stable @id and cross-references it, so several pages describing the
    // same Person contribute evidence to ONE entity rather than creating
    // several competing ones.
    html = html.replace(
      "</head>",
      `  <script type="application/ld+json">${toScriptJson(jsonLd)}</script>\n  </head>`,
    );
  }

  if (noindex) {
    html = html.replace(
      "</head>",
      '  <meta name="robots" content="noindex" />\n  </head>',
    );
  }

  return html;
}

function writePage(route, html) {
  const outPath =
    route === "/" ? join(distDir, "index.html") : join(distDir, route, "index.html");
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, html, "utf8");
  return outPath;
}

/** Strips tags so the content check reads what a human reads, not attributes. */
function visibleText(markup) {
  return markup
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ");
}

/**
 * Collects every forbidden claim across every page, so one run reports the full
 * list rather than failing on the first hit and hiding the rest.
 */
function findForbidden(route, markup) {
  const text = visibleText(markup);
  const hits = [];
  for (const [pattern, why] of FORBIDDEN_CONTENT) {
    const match = text.match(pattern);
    if (!match) continue;
    const at = text.indexOf(match[0]);
    hits.push({
      route,
      term: match[0],
      why,
      context: text.slice(Math.max(0, at - 60), at + 60).trim(),
    });
  }
  return hits;
}

function writeSitemap(routes) {
  const urls = routes
    .map((r) => `  <url>\n    <loc>${escapeAttr(absolute(r))}</loc>\n  </url>`)
    .join("\n");
  writeFileSync(
    join(distDir, "sitemap.xml"),
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
      `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
    "utf8",
  );
}

async function main() {
  const { render, normalizeSiteUrl, buildGraph } = await import(pathToFileURL(ssrEntry).href);
  SITE_URL = normalizeSiteUrl(env.VITE_SITE_URL);

  console.log(`[prerender] site:    ${SITE_URL}`);
  console.log(`[prerender] content: ${API_URL}`);

  // 1 — pull the content the pages are built from.
  const seed = [];
  for (const endpoint of ENDPOINTS) {
    const data = await fetchJson(endpoint.path).catch((error) =>
      fail(
        `Could not read content from the backend.\n  ${error.message}\n\n` +
          `Prerendering needs the API to be reachable. If the service is asleep, wake it\n` +
          `and retry; if it is genuinely down, do not deploy — the build would publish\n` +
          `empty pages to every crawler.`,
      ),
    );
    seed.push({ key: endpoint.key, data });
  }

  const seeded = (name) => seed.find((s) => s.key[0] === name)?.data ?? [];
  const projects = seeded("projects");
  const experience = seeded("experience");
  const skillBranches = seeded("skill-branches");
  const projectRoutes = projects
    .map((p) => p?.slug)
    .filter(Boolean)
    .map((slug) => `/projects/${slug}`);

  // 2 — the route list: static public routes plus one page per real project.
  const profile = seed.find((s) => s.key[0] === "profile")?.data ?? {};
  const headline = profile.headline ?? "Software Development Engineer I";

  const pages = [
    {
      route: "/",
      title: `Omkar Jadhav — ${headline}`,
      description:
        "Omkar Jadhav is a Software Development Engineer I at Nonstop IO Technologies, " +
        "Pune. B.Tech CSE (Data Science), KIT Kolhapur. C#, NestJS, SQL, Spring Boot.",
    },
    {
      route: "/recruiter",
      title: "Recruiter Fit Match — Omkar Jadhav",
      description:
        "Paste a job description and see how Omkar Jadhav's experience in C#, NestJS, " +
        "SQL and Spring Boot scores against it, with the matching projects listed.",
    },
    {
      route: "/mcp",
      title: "MCP Server — Evaluate Omkar Jadhav with your own AI",
      description:
        "A public, read-only Model Context Protocol server exposing Omkar Jadhav's " +
        "projects, skills and experience as tools any MCP-capable AI client can query.",
    },
    ...projectRoutes.map((route) => {
      const slug = route.split("/").pop();
      const project = projects.find((p) => p.slug === slug) ?? {};
      const name = project.repoName ?? slug;
      const language = project.language ? `${project.language} ` : "";
      return {
        route,
        title: `${name} — ${language}Project by Omkar Jadhav`,
        description: (
          project.description ?? `${name}, a project by Omkar Jadhav.`
        ).slice(0, 300),
      };
    }),
  ];

  // 3 — render.
  const template = readFileSync(join(distDir, "index.html"), "utf8");

  const forbidden = [];

  for (const page of pages) {
    const markup = await render(absolute(page.route), seed).catch((error) =>
      fail(`Rendering ${page.route} threw:\n  ${error.stack ?? error.message}`),
    );

    forbidden.push(...findForbidden(page.route, markup));

    const hit = FAILURE_MARKERS.find((marker) => markup.includes(marker));
    if (hit) {
      fail(
        `${page.route} rendered a loading or error state (matched "${hit}").\n\n` +
          `That means the data this page needs was missing or wrong at build time.\n` +
          `Publishing it would serve that broken state to every crawler, so the build\n` +
          `stops here rather than shipping it.`,
      );
    }
    if (!markup.includes(REQUIRED_MARKER)) {
      fail(`${page.route} rendered without "${REQUIRED_MARKER}" — the output is not real content.`);
    }
    if (markup.length < MIN_MARKUP_BYTES) {
      fail(
        `${page.route} rendered only ${markup.length} bytes (minimum ${MIN_MARKUP_BYTES}).\n` +
          `A real page is tens of KB — this is a shell, not content.`,
      );
    }

    // One graph per page, built from the same payload that rendered the
    // visible markup — structured data may only claim what a reader can see.
    const jsonLd = buildGraph({
      siteUrl: SITE_URL,
      assetOrigin: API_URL,
      route: page.route,
      profile,
      projects,
      experience,
      skillBranches,
    });

    const out = writePage(
      page.route,
      buildPage(template, {
        markup,
        seed,
        jsonLd,
        title: page.title,
        description: page.description,
        canonical: absolute(page.route),
      }),
    );
    console.log(`[prerender] ✓ ${page.route.padEnd(38)} ${(markup.length / 1024).toFixed(1)} KB  → ${out.replace(frontendDir, "")}`);
  }

  // 4 — the 404 page. Rendered from a URL that cannot match a real route, and
  //     marked noindex so a crawler that reaches it never files it as content.
  const notFoundMarkup = await render(absolute("/__not-found__"), seed).catch((error) =>
    fail(`Rendering the 404 page threw:\n  ${error.stack ?? error.message}`),
  );
  writeFileSync(
    join(distDir, "404.html"),
    buildPage(template, {
      markup: notFoundMarkup,
      seed: [],
      title: "Page not found — Omkar Jadhav",
      description: "That page does not exist on Omkar Jadhav's portfolio.",
      canonical: absolute("/"),
      noindex: true,
    }),
    "utf8",
  );
  console.log("[prerender] ✓ 404.html");

  // 4b — the admin shell. Admin routes stay client-only, so they need a page
  //      with an EMPTY #root: serving them the prerendered home page would make
  //      React hydrate home markup into an admin route and mismatch. An empty
  //      root also makes main.tsx take its createRoot path instead of hydrating.
  //      noindex because a login screen has no business in an index.
  writeFileSync(
    join(distDir, "admin-shell.html"),
    buildPage(template, {
      markup: "",
      seed: [],
      title: "Admin — Omkar Jadhav",
      description: "Private administration area.",
      canonical: absolute("/"),
      noindex: true,
    }),
    "utf8",
  );
  console.log("[prerender] ✓ admin-shell.html");

  // 5 — the sitemap now knows the real project URLs, which the Vite plugin
  //     could not: slugs only exist once content has been fetched.
  writeSitemap(pages.map((p) => p.route));
  console.log(`[prerender] ✓ sitemap.xml (${pages.length} URLs)`);

  // 6 — refuse to publish claims that are not true.
  if (forbidden.length > 0) {
    const lines = forbidden
      .map((f) => `  ${f.route}\n    "${f.term}" — ${f.why}\n    …${f.context}…`)
      .join("\n\n");
    const message =
      `${forbidden.length} false or withdrawn claim(s) in the rendered pages:\n\n${lines}\n\n` +
      `These pages are built from the live backend, so this content is in the DATABASE,\n` +
      `not the repo — correcting the source files cannot remove it. Fix it in the admin\n` +
      `panel at /admin (Profile, Experience, Projects, Skills), then rebuild.\n\n` +
      `The build stops here on purpose: a portfolio that calls an employed engineer a\n` +
      `student looking for internships, and claims technologies he has withdrawn, is\n` +
      `worse than no portfolio. Set SKIP_CONTENT_CHECK=1 to publish anyway.`;

    if (process.env.SKIP_CONTENT_CHECK === "1") {
      console.warn(`\n[prerender] ⚠ CONTENT CHECK SKIPPED\n\n${message}\n`);
    } else {
      fail(message);
    }
  }
}

main().catch((error) => fail(error.stack ?? String(error)));
