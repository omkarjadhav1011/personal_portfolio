/**
 * Validation for the site-URL environment variable, kept free of any
 * browser- or Vite-specific globals so it can run in three places:
 *
 *  - the browser bundle, via `src/lib/site.ts`
 *  - the Vite config / build, via `vite/seo-assets.ts`
 *  - plain Node scripts, via `scripts/generate-og.mjs`
 *
 * It must therefore never touch `import.meta.env`, `window`, or `process`.
 */

/** The variable every absolute URL on this site is derived from. */
export const SITE_URL_ENV = "VITE_SITE_URL";

/**
 * Validates and normalizes a site-URL value.
 *
 * Normalization strips any trailing slash so callers can concatenate a
 * leading-slash path without producing a double slash.
 *
 * @throws if the value is absent, not a valid absolute URL, or not http(s).
 */
export function normalizeSiteUrl(value: unknown): string {
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(
      `${SITE_URL_ENV} is not set.\n` +
        `Set it to the site's public origin, e.g. https://example.com\n` +
        `  - locally: copy frontend/.env.example to frontend/.env\n` +
        `  - on Vercel: Settings -> Environment Variables ` +
        `(set it for Production, Preview and Development)`,
    );
  }

  const trimmed = value.trim();
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    throw new Error(
      `${SITE_URL_ENV} must be an absolute URL including the scheme, ` +
        `e.g. https://example.com - received: "${trimmed}"`,
    );
  }

  if (url.protocol !== "https:" && url.hostname !== "localhost") {
    throw new Error(
      `${SITE_URL_ENV} must use https (localhost may use http) - received: "${trimmed}"`,
    );
  }

  // Origin + path, minus any trailing slash. Supports a sub-path deployment
  // while guaranteeing `${SITE_URL}/foo` never yields "//foo".
  return `${url.origin}${url.pathname}`.replace(/\/+$/, "");
}
