import { useState, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

/** One seeded React Query cache entry, written into the page by the prerenderer. */
interface SeedEntry {
  key: readonly unknown[];
  data: unknown;
}

/** Element id of the JSON block written by `scripts/prerender.mjs`. */
const SEED_ELEMENT_ID = "__rq-seed__";

/**
 * Reads the build-time cache seed.
 *
 * This is what makes hydration safe: the build rendered the page from this exact
 * data, so priming the client cache with it means the first client render
 * produces identical markup. Without it every query would start as `isPending`,
 * React would replace the real content with skeletons on mount, and hydration
 * would mismatch.
 *
 * The seed is carried in a `<script type="application/json">` block rather than
 * an inline script assigning to `window`. Browsers never execute a JSON script
 * element, so the site's CSP keeps `script-src 'self'` — an inline script would
 * have required `'unsafe-inline'` across every page.
 *
 * Absent on the dev server, which serves an empty root; the empty array then
 * lets every query fetch normally.
 */
function readSeed(): SeedEntry[] {
  if (typeof document === "undefined") return [];
  const raw = document.getElementById(SEED_ELEMENT_ID)?.textContent;
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    // A corrupt seed must not take the page down — fall back to fetching.
    return [];
  }
}

/** App-wide providers. Holds the single TanStack Query client. */
export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => {
    const client = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          retry: 1,
          refetchOnWindowFocus: false,
        },
      },
    });
    for (const entry of readSeed()) {
      client.setQueryData(entry.key, entry.data);
    }
    return client;
  });

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
