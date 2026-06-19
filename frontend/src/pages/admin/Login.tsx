import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";
import { apiFetch, ApiError, BASE_URL } from "@/lib/api";

interface LoginResponse {
  token: string;
  expiresIn: number;
}

/** Maps a backend `?error=` code from the OAuth redirect to a user-facing message. */
function oauthErrorMessage(code: string | null): string {
  switch (code) {
    case "oauth_denied":
      return "Access denied — this account is not authorized for the admin panel.";
    case null:
      return "";
    default:
      return "OAuth sign-in failed — please try again.";
  }
}

/** Full-page redirect to the backend's OAuth2 authorization endpoint (not apiFetch). */
function startOAuth(provider: "google" | "github") {
  window.location.assign(`${BASE_URL}/oauth2/authorization/${provider}`);
}

/**
 * Admin login (ports admin/login/LoginForm.tsx). Per the Bearer decision: on
 * success it stores the JWT (auth store → memory + localStorage), invalidates
 * cached queries, and navigates to /admin (replacing Next's router.push +
 * router.refresh).
 */
export default function Login() {
  useDocumentTitle("Admin Login");
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setToken = useAuthStore((s) => s.setToken);

  const [searchParams] = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Surface an OAuth redirect error (e.g. non-allowlisted account) on the same error line.
  useEffect(() => {
    const message = oauthErrorMessage(searchParams.get("error"));
    if (message) setError(message);
  }, [searchParams]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await apiFetch<LoginResponse>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(res.token);
      await queryClient.invalidateQueries();
      navigate("/admin");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Network error — please try again");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-terminal-bg px-4">
      <div className="w-full max-w-sm">
        {/* Terminal header */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden shadow-terminal">
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <span className="w-3 h-3 rounded-full bg-dot-red" />
            <span className="w-3 h-3 rounded-full bg-dot-yellow" />
            <span className="w-3 h-3 rounded-full bg-dot-green" />
            <span className="ml-2 text-xs text-text-muted font-mono">admin@portfolio: ~</span>
          </div>

          <form onSubmit={handleSubmit} className="p-6 space-y-4 font-mono">
            <div className="space-y-1">
              <p className="text-git-green text-xs">$ sudo admin login</p>
              <p className="text-text-faint text-xs"># enter your credentials</p>
            </div>

            <div className="space-y-3">
              <div>
                <label className="block text-xs text-text-muted mb-1">username</label>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                  autoComplete="username"
                  className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 transition-colors"
                  placeholder="admin"
                />
              </div>
              <div>
                <label className="block text-xs text-text-muted mb-1">password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                  className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 transition-colors"
                  placeholder="••••••••"
                />
              </div>
            </div>

            {error && <p className="text-git-red text-xs">[✗] {error}</p>}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs font-mono hover:bg-git-green/20 hover:border-git-green/70 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Authenticating..." : "$ git authenticate --admin"}
            </button>
          </form>

          {/* OAuth2 sign-in — full-page redirect, not apiFetch. */}
          <div className="px-6 pb-6 space-y-3 font-mono">
            <div className="flex items-center gap-3">
              <span className="h-px flex-1 bg-terminal-border" />
              <span className="text-text-faint text-[10px] uppercase tracking-wider">or</span>
              <span className="h-px flex-1 bg-terminal-border" />
            </div>
            <button
              type="button"
              onClick={() => startOAuth("google")}
              className="w-full py-2.5 rounded-lg border border-terminal-border bg-terminal-bg text-text-primary text-xs hover:border-git-blue/50 hover:bg-git-blue/5 transition-all duration-200"
            >
              $ login --with google
            </button>
            <button
              type="button"
              onClick={() => startOAuth("github")}
              className="w-full py-2.5 rounded-lg border border-terminal-border bg-terminal-bg text-text-primary text-xs hover:border-git-blue/50 hover:bg-git-blue/5 transition-all duration-200"
            >
              $ login --with github
            </button>
          </div>
        </div>

        <p className="text-center text-text-faint text-xs font-mono mt-4">Portfolio Admin Panel</p>
      </div>
    </div>
  );
}
