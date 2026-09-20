import { normalizeSiteUrl } from "@/lib/site-url";

/**
 * The single source of truth for this site's public origin, for browser code.
 *
 * Every absolute URL the site emits — canonicals, Open Graph tags, the sitemap,
 * robots.txt, JSON-LD `@id` and `url` fields, OG image URLs — must be derived
 * from {@link SITE_URL} or built with {@link absoluteUrl}. Nothing may hardcode
 * the domain.
 *
 * Why: the site currently lives on a temporary platform subdomain and will
 * move to a purchased custom domain. That migration must cost exactly one value
 * change (`VITE_SITE_URL` in the Vercel dashboard) plus a redeploy — never a
 * repo-wide search and replace.
 *
 * The variable is validated twice, deliberately:
 *  1. At build and dev-server start by the `seo-assets` Vite plugin, which fails
 *     loudly if it is missing or malformed. That is the real gate.
 *  2. Here, at module load, as a defence-in-depth invariant.
 */
export const SITE_URL: string = normalizeSiteUrl(import.meta.env.VITE_SITE_URL);

/**
 * Builds an absolute URL for a site-relative path.
 *
 * Pass-through for values that are already absolute, so callers can hand it a
 * mix of internal paths and external URLs without branching.
 *
 * @example absoluteUrl("/projects/expense-tracker")
 *          -> "https://example.com/projects/expense-tracker"
 * @example absoluteUrl("/") -> "https://example.com/"
 */
export function absoluteUrl(path: string = "/"): string {
  if (/^[a-z][a-z0-9+.-]*:/i.test(path)) return path;
  return `${SITE_URL}/${path.replace(/^\/+/, "")}`;
}
