import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";
import { apiFetch, ApiError, BASE_URL } from "@/lib/api";

interface LoginResponse {
  token: string;
  expiresIn: number;
  mfaRequired?: boolean;
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

/** Google "G" mark — multicolor official brand logo, sized to match the mono text. */
function GoogleIcon() {
  return (
    <svg viewBox="0 0 48 48" aria-hidden="true" className="h-4 w-4 shrink-0">
      <path fill="#4285F4" d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z" />
      <path fill="#34A853" d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z" />
      <path fill="#FBBC05" d="M11.69 28.18c-.44-1.32-.69-2.73-.69-4.18s.25-2.86.69-4.18v-5.7H4.34A21.99 21.99 0 0 0 2 24c0 3.55.85 6.91 2.34 9.88l7.35-5.7z" />
      <path fill="#EA4335" d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z" />
    </svg>
  );
}

/** GitHub mark — single-color so it picks up the button's currentColor. */
function GitHubIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" fill="currentColor" className="h-4 w-4 shrink-0">
      <path d="M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56 0-.27-.01-1.16-.02-2.1-3.2.7-3.88-1.36-3.88-1.36-.52-1.33-1.28-1.69-1.28-1.69-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11.1 11.1 0 0 1 2.9-.39c.98 0 1.97.13 2.9.39 2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.84 1.19 3.1 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14 0 1.55-.01 2.79-.01 3.17 0 .31.21.68.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.73 18.27.5 12 .5z" />
    </svg>
  );
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
      if (res.mfaRequired) {
        // First factor OK; carry the PRE_AUTH token to the verify page (not the auth store).
        navigate("/admin/mfa/verify", { state: { preAuthToken: res.token } });
        return;
      }
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
            <div className="flex items-center justify-center gap-3">
              <button
                type="button"
                onClick={() => startOAuth("google")}
                aria-label="Login with Google"
                className="flex items-center justify-center p-2.5 rounded-lg border border-terminal-border bg-terminal-bg text-text-primary hover:border-git-blue/50 hover:bg-git-blue/5 transition-all duration-200"
              >
                <GoogleIcon />
              </button>
              <button
                type="button"
                onClick={() => startOAuth("github")}
                aria-label="Login with GitHub"
                className="flex items-center justify-center p-2.5 rounded-lg border border-terminal-border bg-terminal-bg text-text-primary hover:border-git-blue/50 hover:bg-git-blue/5 transition-all duration-200"
              >
                <GitHubIcon />
              </button>
            </div>
          </div>
        </div>

        <p className="text-center text-text-faint text-xs font-mono mt-4">Portfolio Admin Panel</p>
      </div>
    </div>
  );
}
