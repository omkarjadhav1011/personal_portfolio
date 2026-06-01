import { Link } from "react-router-dom";

/**
 * Blank routed index page (Phase 8.1) — confirms a route renders inside
 * RootLayout. Real section assembly replaces this in a later phase.
 */
export default function Home() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-terminal-bg bg-grid-pattern bg-grid font-mono text-text-primary">
      <div className="space-y-2 text-center">
        <p className="text-git-green">✓ routed page rendered inside RootLayout</p>
        <p className="text-sm text-text-muted">
          <span className="text-text-faint">→</span>{" "}
          <Link to="/scratch" className="hover:text-git-green">
            /scratch
          </Link>{" "}
          for the API + hooks demo
        </p>
      </div>
    </main>
  );
}
