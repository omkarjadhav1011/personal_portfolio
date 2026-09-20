/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the backend API. Empty in dev (relative paths use the Vite proxy). */
  readonly VITE_API_URL?: string;
  /**
   * The site's own public origin, e.g. "https://example.com".
   * The single source of truth for every absolute URL the site emits.
   * Required — the build fails without it (see frontend/vite/seo-assets.ts).
   */
  readonly VITE_SITE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
