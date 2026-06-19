import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/store/auth";
import { apiFetch } from "@/lib/api";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

interface LoginResponse {
  token: string;
  expiresIn: number;
}

/**
 * Lands the OAuth2 redirect. The backend put only a single-use 60s `code` in the URL
 * (never the JWT); we POST it to `/api/auth/oauth/exchange`, store the returned token
 * (in-memory, like password login), and navigate to /admin. Any error → back to login.
 *
 * Sits OUTSIDE RequireAuth (it runs before a token exists) and OUTSIDE RedirectIfAuthed
 * (it is the thing that sets the token).
 */
export default function OAuthCallback() {
  useDocumentTitle("Authenticating");
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setToken = useAuthStore((s) => s.setToken);
  const [params] = useSearchParams();
  // The exchange code is SINGLE-USE — guard against React StrictMode's double-invoke so
  // we never POST the same code twice (the second call would 400).
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const error = params.get("error");
    const code = params.get("code");
    if (error || !code) {
      navigate(`/admin/login?error=${encodeURIComponent(error ?? "oauth_failed")}`, { replace: true });
      return;
    }

    (async () => {
      try {
        const res = await apiFetch<LoginResponse>("/api/auth/oauth/exchange", {
          method: "POST",
          body: JSON.stringify({ code }),
        });
        setToken(res.token);
        await queryClient.invalidateQueries();
        navigate("/admin", { replace: true });
      } catch {
        navigate("/admin/login?error=oauth_failed", { replace: true });
      }
    })();
  }, [params, navigate, queryClient, setToken]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-terminal-bg px-4">
      <div className="w-full max-w-sm font-mono text-xs">
        <p className="text-git-green">$ git authenticate --oauth</p>
        <p className="text-text-muted mt-2">
          <span className="animate-pulse"># </span>exchanging authorization code…
        </p>
      </div>
    </div>
  );
}
