import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";
import { apiFetch, ApiError } from "@/lib/api";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

interface LoginResponse {
  token: string;
  expiresIn: number;
  mfaRequired?: boolean;
}

/**
 * Second-factor gate. Reached after the first factor (password or OAuth) when MFA is enabled,
 * carrying a short-lived PRE_AUTH token in router state (never persisted). Submitting a valid
 * TOTP or recovery code swaps it for the real ADMIN token. Sits OUTSIDE both route guards.
 */
export default function MfaVerify() {
  useDocumentTitle("Two-Factor");
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const setToken = useAuthStore((s) => s.setToken);

  const preAuthToken = (location.state as { preAuthToken?: string } | null)?.preAuthToken;
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // No PRE_AUTH token (direct navigation, or a refresh wiped router state) → restart at login.
  if (!preAuthToken) {
    return <Navigate to="/admin/login" replace />;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await apiFetch<LoginResponse>("/api/auth/mfa/verify", {
        method: "POST",
        headers: { Authorization: `Bearer ${preAuthToken}` },
        body: JSON.stringify({ code: code.trim() }),
      });
      setToken(res.token);
      await queryClient.invalidateQueries();
      navigate("/admin", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Network error — please try again");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-terminal-bg px-4">
      <div className="w-full max-w-sm">
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden shadow-terminal">
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <span className="w-3 h-3 rounded-full bg-dot-red" />
            <span className="w-3 h-3 rounded-full bg-dot-yellow" />
            <span className="w-3 h-3 rounded-full bg-dot-green" />
            <span className="ml-2 text-xs text-text-muted font-mono">admin@portfolio: ~/2fa</span>
          </div>

          <form onSubmit={handleSubmit} className="p-6 space-y-4 font-mono">
            <div className="space-y-1">
              <p className="text-git-green text-xs">$ verify --second-factor</p>
              <p className="text-text-faint text-xs"># enter the 6-digit code from your authenticator</p>
            </div>

            <div>
              <label className="block text-xs text-text-muted mb-1">code</label>
              <input
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
                placeholder="123456 or recovery code"
                className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 transition-colors tracking-widest"
              />
            </div>

            {error && <p className="text-git-red text-xs">[✗] {error}</p>}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs font-mono hover:bg-git-green/20 hover:border-git-green/70 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Verifying..." : "$ git authenticate --verify"}
            </button>

            <p className="text-text-faint text-[10px]"># lost your device? use a one-time recovery code</p>
          </form>
        </div>
      </div>
    </div>
  );
}
