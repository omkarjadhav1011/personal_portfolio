/**
 * The public route manifest — the single source of truth for which URLs are
 * part of the indexable site.
 *
 * Consumed by:
 *  - `vite/seo-assets.ts` — to generate `sitemap.xml` at build time
 *  - (from Wave 1) the prerender step — to decide which routes to render to HTML
 *
 * Keep this in sync with `src/router.tsx`. A route that is publicly reachable
 * but missing here is an orphan: it will never appear in the sitemap and will
 * never be prerendered.
 *
 * Deliberately excluded:
 *  - `/admin/**` — private, disallowed in robots.txt and excluded from prerendering
 *  - `/projects/:slug` — dynamic; the concrete slugs are resolved from live
 *                        content at build time by the prerender step
 */
export const PUBLIC_ROUTES = ["/", "/recruiter", "/mcp"] as const;

/** Routes that must never be indexed. Mirrored into robots.txt `Disallow` rules. */
export const DISALLOWED_PATHS = ["/admin/"] as const;

export type PublicRoute = (typeof PUBLIC_ROUTES)[number];
