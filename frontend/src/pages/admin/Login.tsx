import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";
import { apiFetch, ApiError } from "@/lib/api";

interface LoginResponse {
  token: string;
  expiresIn: number;
}

/**
 * Admin login (ports admin/login/LoginForm.tsx). Per the Bearer decision: on
 * success it stores the JWT (auth store → memory + localStorage), invalidates
 * cached queries, and navigates to /admin (replacing Next's router.push +
 * router.refresh).
 */
export default function Login() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setToken = useAuthStore((s) => s.setToken);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
            <span className="w-3 h-3 rounded-full bg-[#ff5f57]" />
            <span className="w-3 h-3 rounded-full bg-[#febc2e]" />
            <span className="w-3 h-3 rounded-full bg-[#28c840]" />
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
                  className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 text-xs text-text-primary placeholder-text-faint focus:outline-none focus:border-git-blue/50 focus:ring-1 focus:ring-git-blue/20 transition-colors"
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
                  className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 text-xs text-text-primary placeholder-text-faint focus:outline-none focus:border-git-blue/50 focus:ring-1 focus:ring-git-blue/20 transition-colors"
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
        </div>

        <p className="text-center text-text-faint text-xs font-mono mt-4">Portfolio Admin Panel</p>
      </div>
    </div>
  );
}
