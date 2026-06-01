import { Link } from "react-router-dom";
import { useAuthStore } from "@/store/auth";

/**
 * Placeholder admin landing — the login redirect target. The full admin
 * dashboard (sidebar, editors, reorder) is ported in a later phase.
 */
export default function AdminHome() {
  const token = useAuthStore((s) => s.token);

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-terminal-bg px-4 font-mono text-sm">
        <div className="space-y-3 text-center">
          <p className="text-git-red">[✗] not authenticated</p>
          <Link to="/admin/login" className="text-git-green hover:underline">
            $ sudo admin login →
          </Link>
        </div>
      </div>
    );
  }

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
