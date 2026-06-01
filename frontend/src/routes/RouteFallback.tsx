/** Suspense fallback for routed pages (replaces Next's loading.tsx). */
export function RouteFallback() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-terminal-bg font-mono text-sm text-text-muted">
      <p>
        <span className="text-git-green">$</span> loading
        <span className="ml-1 inline-block h-4 w-2 translate-y-0.5 animate-cursor-blink bg-git-green" />
      </p>
    </div>
  );
}
