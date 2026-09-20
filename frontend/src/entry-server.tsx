import { StrictMode } from "react";
import { renderToString } from "react-dom/server";
import {
  createStaticHandler,
  createStaticRouter,
  StaticRouterProvider,
} from "react-router-dom/server";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { RouteObject } from "react-router-dom";

import { RootLayout } from "@/routes/RootLayout";
import { MainLayout } from "@/routes/MainLayout";
import { NotFound } from "@/routes/NotFound";

// Re-exported so scripts/prerender.mjs (plain Node, cannot import TypeScript)
// validates VITE_SITE_URL with the very same function the app uses.
export { normalizeSiteUrl } from "@/lib/site-url";
import Home from "@/pages/Home";
import ProjectDetail from "@/pages/ProjectDetail";
import RecruiterPage from "@/pages/RecruiterPage";
import McpPage from "@/pages/McpPage";

/**
 * Server entry used by `scripts/prerender.mjs` to turn each public route into
 * real HTML at build time.
 *
 * Why this exists: the site is a client-rendered SPA, so the HTML a crawler
 * received was `<div id="root"></div>` and nothing else — no name, no employer,
 * no projects. Google may eventually render JavaScript; GPTBot, ClaudeBot,
 * PerplexityBot and CCBot do not run it at all, which made the site invisible to
 * exactly the surface it most needs to reach.
 *
 * Why `react-dom/server` rather than a headless browser: no 170 MB Chromium
 * download in the build, deterministic output, and no "wait for network idle"
 * heuristics. It works because the app turned out to be SSR-clean — the only
 * module-scope browser access in `src/` is already guarded with
 * `typeof window !== "undefined"`.
 *
 * ⚠ The route tree below is deliberately a duplicate of the public half of
 * `src/router.tsx`, with EAGER imports. `renderToString` does not wait on
 * Suspense, so a `React.lazy()` page would render its fallback — a loading
 * spinner — straight into the published HTML. Admin routes are absent on
 * purpose: they are never prerendered. Keep this list in sync with
 * `PUBLIC_ROUTES` in `src/lib/routes.ts`.
 */
const routes: RouteObject[] = [
  {
    path: "/",
    element: <RootLayout />,
    children: [
      {
        element: <MainLayout />,
        children: [
          { index: true, element: <Home /> },
          { path: "projects/:slug", element: <ProjectDetail /> },
          { path: "recruiter", element: <RecruiterPage /> },
          { path: "mcp", element: <McpPage /> },
          // Rendered once to dist/404.html, which Vercel serves — with a real
          // 404 status — for anything that has no prerendered file. Previously
          // the SPA rewrite answered every unknown URL with HTTP 200 and the app
          // shell, so the site presented an unbounded set of duplicate pages.
          { path: "*", element: <NotFound /> },
        ],
      },
    ],
  },
];

/** A cache entry to seed before rendering: the query key and its raw API payload. */
export interface SeedEntry {
  key: readonly unknown[];
  data: unknown;
}

/**
 * Renders one route to an HTML string.
 *
 * `seed` pre-populates the React Query cache with data the build fetched over
 * plain Node `fetch`, so no component ever hits the network during render and
 * nothing resolves to a loading state. Anything not seeded would render its
 * skeleton — which is why `prerender.mjs` asserts on the output.
 */
export async function render(url: string, seed: SeedEntry[]): Promise<string> {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        // The build owns this cache for a single render pass; retries and
        // refetches would only add latency and nondeterminism.
        retry: false,
        staleTime: Infinity,
        gcTime: Infinity,
      },
    },
  });

  for (const entry of seed) {
    queryClient.setQueryData(entry.key, entry.data);
  }

  const handler = createStaticHandler(routes);
  const context = await handler.query(new Request(url));

  if (context instanceof Response) {
    throw new Error(
      `Route ${url} returned a Response (status ${context.status}) instead of a render context.`,
    );
  }

  const router = createStaticRouter(routes, context);

  return renderToString(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <StaticRouterProvider router={router} context={context} nonce={undefined} />
      </QueryClientProvider>
    </StrictMode>,
  );
}
