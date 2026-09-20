import type { Plugin } from "vite";
import { normalizeSiteUrl, SITE_URL_ENV } from "../src/lib/site-url";
import { DISALLOWED_PATHS, PUBLIC_ROUTES } from "../src/lib/routes";

/**
 * Generates `robots.txt` and `sitemap.xml` at build time from a single
 * environment variable, and fails the build loudly when that variable is
 * missing or malformed.
 *
 * Both files used to be hand-written under `public/` with the domain baked in —
 * and the baked-in host was a transposition of the real one, so it returned 404.
 * Every canonical hint the site published therefore named a page that does not
 * exist. Generating them removes the whole class of bug: there is now exactly
 * one place the domain can be wrong, and an unset value stops the build instead
 * of shipping.
 *
 * The validation runs in the `config` hook, which fires for `vite build` *and*
 * `vite dev`, so a missing variable is caught the moment anyone starts work
 * rather than at deploy time.
 */
export function seoAssets(siteUrlFromEnv: string | undefined): Plugin {
  let siteUrl = "";
  let isSsrBuild = false;

  return {
    name: "seo-assets",

    // Runs for both `serve` and `build`.
    config() {
      try {
        siteUrl = normalizeSiteUrl(siteUrlFromEnv);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        // Vite prints plugin errors with a stack trace that buries the cause, so
        // lead with a banner that survives a noisy terminal.
        throw new Error(
          `\n\n[seo-assets] Cannot build: ${SITE_URL_ENV} is invalid.\n\n${message}\n`,
        );
      }
    },

    configResolved(resolved) {
      // `vite build --ssr` runs this plugin too; without the flag it would drop
      // a stray robots.txt and sitemap.xml into dist-ssr/, which is a build
      // artifact directory that never gets served.
      isSsrBuild = Boolean(resolved.build.ssr);
    },

    // Build only — robots/sitemap are irrelevant to the dev server.
    generateBundle() {
      if (isSsrBuild) return;
      this.emitFile({
        type: "asset",
        fileName: "robots.txt",
        source: renderRobotsTxt(siteUrl),
      });
      this.emitFile({
        type: "asset",
        fileName: "sitemap.xml",
        source: renderSitemapXml(siteUrl),
      });
    },
  };
}

/**
 * AI crawlers, named explicitly and allowed.
 *
 * An absent policy and an explicit allow are different signals: silence leaves
 * operators to infer intent, naming the agent states it. The owner's decision
 * was to allow all of them, and the reasoning is straightforward — the single
 * strongest real-world use of this site is somebody asking an AI assistant
 * "who is Omkar Jadhav?", and blocking the crawlers that answer that question
 * forfeits exactly the surface the site most needs. There is nothing here worth
 * withholding: it is a public professional profile.
 *
 * Google-Extended is the one people get wrong. It governs Gemini training and
 * grounding ONLY — it has no effect on Google Search indexing or ranking, so
 * allowing it costs nothing in search terms.
 */
const AI_CRAWLERS: [string, string][] = [
  ["GPTBot", "OpenAI - model training"],
  ["OAI-SearchBot", "OpenAI - ChatGPT search index"],
  ["ChatGPT-User", "OpenAI - fetches a page a user asked about"],
  ["ClaudeBot", "Anthropic - model training"],
  ["Claude-User", "Anthropic - fetches a page a user asked about"],
  ["Claude-SearchBot", "Anthropic - search index"],
  ["PerplexityBot", "Perplexity - search index"],
  ["Perplexity-User", "Perplexity - user-initiated fetch"],
  ["Google-Extended", "Gemini training/grounding - does NOT affect Google Search"],
  ["Applebot-Extended", "Apple Intelligence"],
  ["meta-externalagent", "Meta AI"],
  ["Amazonbot", "Amazon"],
  ["CCBot", "Common Crawl - feeds many downstream models"],
];

function renderRobotsTxt(siteUrl: string): string {
  const disallow = DISALLOWED_PATHS.map((path) => `Disallow: ${path}`).join("\n");

  const aiGroups = AI_CRAWLERS.flatMap(([agent, why]) => [
    `# ${why}`,
    `User-agent: ${agent}`,
    "Allow: /",
    disallow,
    "",
  ]);

  return [
    "# Generated at build time by vite/seo-assets.ts — do not edit by hand.",
    `# Every absolute URL below is derived from ${SITE_URL_ENV}.`,
    "",
    "User-agent: *",
    "Allow: /",
    disallow,
    "",
    "# AI crawlers, allowed explicitly. This site exists to be the accurate",
    "# answer when somebody asks an assistant about Omkar Jadhav; blocking the",
    "# crawlers that answer that question would defeat the point.",
    "",
    ...aiGroups,
    "# A curated summary written for LLM clients:",
    `# ${siteUrl}/llms.txt`,
    "",
    `Sitemap: ${siteUrl}/sitemap.xml`,
    "",
  ].join("\n");
}

function renderSitemapXml(siteUrl: string): string {
  // `<lastmod>` is deliberately omitted rather than stamped with the build date:
  // a build is not a content change, and a lastmod that moves on every deploy
  // teaches crawlers to distrust it. Real per-page dates arrive with the
  // prerender step, which knows when each page's content actually changed.
  //
  // `<priority>` and `<changefreq>` are omitted because Google ignores both.
  const urls = PUBLIC_ROUTES.map((route) => {
    const loc = `${siteUrl}/${route.replace(/^\/+/, "")}`;
    return `  <url>\n    <loc>${escapeXml(loc)}</loc>\n  </url>`;
  }).join("\n");

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    urls,
    "</urlset>",
    "",
  ].join("\n");
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}
