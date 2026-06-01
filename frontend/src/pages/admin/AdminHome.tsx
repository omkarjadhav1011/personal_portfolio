import { Link } from "react-router-dom";

/**
 * Placeholder admin landing — rendered only when authenticated (RequireAuth
 * guards the route). The full admin dashboard is ported in a later phase.
 */
export default function AdminHome() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-terminal-bg px-4 font-mono text-sm">
      <div className="space-y-2 text-center">
        <p className="text-git-green">✓ authenticated — admin session active</p>
        <p className="text-text-muted text-xs">Admin dashboard is ported in a later phase.</p>
        <Link to="/" className="text-text-faint hover:text-git-green text-xs">
          ← back to site
        </Link>
      </div>
    </div>
  );
}
