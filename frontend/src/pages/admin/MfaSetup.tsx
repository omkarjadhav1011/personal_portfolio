import { useState } from "react";
import { apiFetch, ApiError } from "@/lib/api";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

interface SetupResponse {
  secret: string;
  otpauthUri: string;
  qrDataUri: string;
}

interface EnableResponse {
  recoveryCodes: string[];
}

/**
 * MFA enrollment (authenticated admin). Flow: start → scan the QR / enter the secret in an
 * authenticator app → confirm a live code to enable → store the one-time recovery codes shown
 * exactly once. Uses the admin Bearer token via the standard apiFetch.
 */
export default function MfaSetup() {
  useDocumentTitle("Enable 2FA");
  const [setup, setSetup] = useState<SetupResponse | null>(null);
  const [code, setCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function startSetup() {
    setError("");
    setLoading(true);
    try {
      setSetup(await apiFetch<SetupResponse>("/api/auth/mfa/setup", { method: "POST" }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Network error — please try again");
    } finally {
      setLoading(false);
    }
  }

  async function enable() {
    setError("");
    setLoading(true);
    try {
      const res = await apiFetch<EnableResponse>("/api/auth/mfa/enable", {
        method: "POST",
        body: JSON.stringify({ code: code.trim() }),
      });
      setRecoveryCodes(res.recoveryCodes);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Network error — please try again");
    } finally {
      setLoading(false);
    }
  }

  function copyRecoveryCodes() {
    if (recoveryCodes) void navigator.clipboard?.writeText(recoveryCodes.join("\n"));
  }

  function downloadRecoveryCodes() {
    if (!recoveryCodes) return;
    const blob = new Blob([recoveryCodes.join("\n") + "\n"], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "portfolio-admin-recovery-codes.txt";
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="max-w-lg mx-auto font-mono space-y-4">
      <div className="space-y-1">
        <h1 className="text-git-green text-sm">$ mfa enroll</h1>
        <p className="text-text-faint text-xs"># add a TOTP second factor (Google Authenticator / Authy)</p>
      </div>

      {error && <p className="text-git-red text-xs">[✗] {error}</p>}

      {/* Step 1 — start */}
      {!setup && !recoveryCodes && (
        <button
          type="button"
          onClick={startSetup}
          disabled={loading}
          className="py-2.5 px-4 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 transition-colors disabled:opacity-50"
        >
          {loading ? "Generating..." : "$ generate secret"}
        </button>
      )}

      {/* Step 2 — scan + confirm */}
      {setup && !recoveryCodes && (
        <div className="space-y-4 rounded-xl border border-terminal-border bg-terminal-surface p-5">
          <p className="text-text-muted text-xs"># 1. scan this QR in your authenticator app</p>
          <img
            src={setup.qrDataUri}
            alt="MFA QR code"
            className="w-44 h-44 rounded-lg bg-white p-2"
          />
          <p className="text-text-faint text-[11px] break-all">
            # or enter the key manually: <span className="text-text-primary">{setup.secret}</span>
          </p>

          <p className="text-text-muted text-xs"># 2. enter the current 6-digit code to confirm</p>
          <div className="flex gap-2">
            <input
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="123456"
              className="flex-1 bg-terminal-bg border border-terminal-border rounded-lg px-3 py-2 text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 tracking-widest"
            />
            <button
              type="button"
              onClick={enable}
              disabled={loading || code.trim().length < 6}
              className="py-2 px-4 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 transition-colors disabled:opacity-50"
            >
              {loading ? "..." : "enable"}
            </button>
          </div>
        </div>
      )}

      {/* Step 3 — recovery codes (shown once) */}
      {recoveryCodes && (
        <div className="space-y-3 rounded-xl border border-git-yellow/40 bg-git-yellow/5 p-5">
          <p className="text-git-yellow text-xs">[✓] MFA enabled. Save these recovery codes now — shown only once.</p>
          <ul className="grid grid-cols-2 gap-1 text-xs text-text-primary">
            {recoveryCodes.map((c) => (
              <li key={c} className="tracking-wider">{c}</li>
            ))}
          </ul>
          <div className="flex gap-2">
            <button type="button" onClick={copyRecoveryCodes}
              className="py-2 px-3 rounded-lg border border-terminal-border text-text-muted text-xs hover:border-git-blue/50 transition-colors">
              copy
            </button>
            <button type="button" onClick={downloadRecoveryCodes}
              className="py-2 px-3 rounded-lg border border-terminal-border text-text-muted text-xs hover:border-git-blue/50 transition-colors">
              download .txt
            </button>
          </div>
          <p className="text-text-faint text-[10px]"># each code works once if you lose your authenticator device</p>
        </div>
      )}
    </div>
  );
}
