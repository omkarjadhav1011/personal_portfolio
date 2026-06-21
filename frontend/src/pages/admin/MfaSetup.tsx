import { useState } from "react";
import { Link } from "react-router-dom";
import {
  ShieldCheck,
  QrCode,
  KeyRound,
  Smartphone,
  Copy,
  Download,
  Check,
  ArrowRight,
  Loader2,
  AlertTriangle,
} from "lucide-react";
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

type Step = 1 | 2 | 3;

const STEPS: { n: Step; label: string }[] = [
  { n: 1, label: "generate" },
  { n: 2, label: "scan & confirm" },
  { n: 3, label: "recovery codes" },
];

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
  const [copied, setCopied] = useState<"secret" | "codes" | null>(null);

  const step: Step = recoveryCodes ? 3 : setup ? 2 : 1;

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

  function flashCopied(which: "secret" | "codes") {
    setCopied(which);
    window.setTimeout(() => setCopied((c) => (c === which ? null : c)), 1500);
  }

  function copySecret() {
    if (setup) {
      void navigator.clipboard?.writeText(setup.secret);
      flashCopied("secret");
    }
  }

  function copyRecoveryCodes() {
    if (recoveryCodes) {
      void navigator.clipboard?.writeText(recoveryCodes.join("\n"));
      flashCopied("codes");
    }
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
    <div className="max-w-2xl mx-auto px-4 py-8 font-mono space-y-6">
      {/* ── Header ─────────────────────────────────────────────── */}
      <header className="flex items-start gap-3">
        <span className="grid place-items-center w-10 h-10 shrink-0 rounded-lg border border-git-green/30 bg-git-green/10 text-git-green">
          <ShieldCheck size={20} />
        </span>
        <div className="space-y-1 min-w-0">
          <h1 className="text-git-green text-sm">$ mfa enroll</h1>
          <p className="text-text-faint text-xs">
            # add a TOTP second factor (Google Authenticator / Authy / 1Password)
          </p>
        </div>
      </header>

      {/* ── Stepper ────────────────────────────────────────────── */}
      <ol className="flex items-center gap-2">
        {STEPS.map((s, i) => {
          const done = step > s.n;
          const active = step === s.n;
          return (
            <li key={s.n} className="flex items-center gap-2 flex-1 last:flex-none">
              <div
                className={`flex items-center gap-2 text-[11px] transition-colors ${
                  active ? "text-git-green" : done ? "text-text-secondary" : "text-text-faint"
                }`}
              >
                <span
                  className={`grid place-items-center w-6 h-6 shrink-0 rounded-md border text-[11px] transition-colors ${
                    active
                      ? "border-git-green/60 bg-git-green/10 text-git-green"
                      : done
                        ? "border-git-green/40 bg-git-green/10 text-git-green"
                        : "border-terminal-border text-text-faint"
                  }`}
                >
                  {done ? <Check size={12} /> : s.n}
                </span>
                <span className="hidden sm:inline whitespace-nowrap">{s.label}</span>
              </div>
              {i < STEPS.length - 1 && (
                <span
                  className={`h-px flex-1 transition-colors ${
                    done ? "bg-git-green/40" : "bg-terminal-border"
                  }`}
                />
              )}
            </li>
          );
        })}
      </ol>

      {error && (
        <p className="flex items-center gap-2 text-git-red text-xs rounded-lg border border-git-red/30 bg-git-red/5 px-3 py-2">
          <AlertTriangle size={13} className="shrink-0" />
          {error}
        </p>
      )}

      {/* ── Step 1 — start ─────────────────────────────────────── */}
      {step === 1 && (
        <div className="rounded-xl border border-terminal-border bg-terminal-surface p-6 space-y-5 animate-fade-up">
          <p className="text-text-muted text-xs leading-relaxed">
            # Two-factor adds a one-time code on top of your password. You&apos;ll scan a QR code
            with an authenticator app, confirm one code, then save a set of recovery codes.
          </p>
          <ul className="space-y-2 text-xs text-text-secondary">
            {[
              { icon: QrCode, text: "Generate a secret & QR code" },
              { icon: Smartphone, text: "Add it to your authenticator app" },
              { icon: KeyRound, text: "Confirm a live 6-digit code" },
            ].map(({ icon: Icon, text }, idx) => (
              <li key={idx} className="flex items-center gap-2.5">
                <Icon size={14} className="text-git-blue shrink-0" />
                {text}
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={startSetup}
            disabled={loading}
            className="inline-flex items-center gap-2 py-2.5 px-4 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 hover:border-git-green/70 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <>
                <Loader2 size={13} className="animate-spin" />
                Generating…
              </>
            ) : (
              <>
                $ generate secret
                <ArrowRight size={13} />
              </>
            )}
          </button>
        </div>
      )}

      {/* ── Step 2 — scan + confirm ────────────────────────────── */}
      {step === 2 && setup && (
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden animate-fade-up">
          <div className="grid md:grid-cols-2">
            {/* Left — QR + manual key */}
            <div className="p-6 space-y-4 border-b md:border-b-0 md:border-r border-terminal-border">
              <p className="flex items-center gap-2 text-text-muted text-xs">
                <span className="grid place-items-center w-5 h-5 rounded-md border border-terminal-border text-[10px] text-git-green">
                  1
                </span>
                scan in your authenticator app
              </p>
              <div className="flex justify-center">
                <img
                  src={setup.qrDataUri}
                  alt="MFA QR code"
                  className="w-44 h-44 rounded-lg bg-white p-2 ring-1 ring-terminal-border"
                />
              </div>
              <div className="space-y-1.5">
                <p className="text-text-faint text-[11px]"># or enter the key manually</p>
                <button
                  type="button"
                  onClick={copySecret}
                  title="Copy secret"
                  className="group w-full flex items-center gap-2 rounded-lg border border-terminal-border bg-terminal-bg px-3 py-2 text-left transition-colors hover:border-git-blue/50"
                >
                  <span className="flex-1 min-w-0 overflow-x-auto whitespace-nowrap text-[10px] text-text-primary tracking-tight">
                    {setup.secret}
                  </span>
                  {copied === "secret" ? (
                    <Check size={13} className="shrink-0 text-git-green" />
                  ) : (
                    <Copy size={13} className="shrink-0 text-text-faint group-hover:text-git-blue" />
                  )}
                </button>
              </div>
            </div>

            {/* Right — confirm code */}
            <div className="p-6 space-y-4">
              <p className="flex items-center gap-2 text-text-muted text-xs">
                <span className="grid place-items-center w-5 h-5 rounded-md border border-terminal-border text-[10px] text-git-green">
                  2
                </span>
                enter the current 6-digit code
              </p>
              <input
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                autoFocus
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && code.trim().length === 6 && !loading) enable();
                }}
                placeholder="••••••"
                className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-3 py-3 text-center text-2xl text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 transition-colors tracking-[0.5em] indent-[0.5em]"
              />
              <button
                type="button"
                onClick={enable}
                disabled={loading || code.trim().length < 6}
                className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 hover:border-git-green/70 transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <>
                    <Loader2 size={13} className="animate-spin" />
                    Verifying…
                  </>
                ) : (
                  <>
                    <ShieldCheck size={13} />
                    enable two-factor
                  </>
                )}
              </button>
              <p className="text-text-faint text-[10px] leading-relaxed">
                # the code rotates every 30s — enter the one showing now
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ── Step 3 — recovery codes (shown once) ───────────────── */}
      {step === 3 && recoveryCodes && (
        <div className="space-y-5 animate-fade-up">
          <div className="flex items-center gap-2 rounded-lg border border-git-green/30 bg-git-green/10 px-3 py-2.5 text-git-green text-xs">
            <Check size={14} className="shrink-0" />
            Two-factor is enabled on your account.
          </div>

          <div className="rounded-xl border border-git-yellow/40 bg-git-yellow/5 p-5 space-y-4">
            <div className="flex items-start gap-2">
              <AlertTriangle size={15} className="shrink-0 mt-0.5 text-git-yellow" />
              <p className="text-git-yellow text-xs leading-relaxed">
                Save these recovery codes now — they&apos;re shown{" "}
                <span className="font-semibold">only once</span>. Each works a single time if you
                lose your authenticator device.
              </p>
            </div>

            <ul className="grid grid-cols-2 gap-2">
              {recoveryCodes.map((c, idx) => (
                <li
                  key={c}
                  className="flex items-center gap-2 rounded-lg border border-terminal-border bg-terminal-bg px-3 py-2 text-xs text-text-primary tracking-wider"
                >
                  <span className="text-text-faint text-[10px] tabular-nums">
                    {String(idx + 1).padStart(2, "0")}
                  </span>
                  {c}
                </li>
              ))}
            </ul>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={copyRecoveryCodes}
                className="inline-flex items-center gap-2 py-2 px-3 rounded-lg border border-terminal-border text-text-muted text-xs hover:border-git-blue/50 hover:text-text-primary transition-colors"
              >
                {copied === "codes" ? <Check size={13} className="text-git-green" /> : <Copy size={13} />}
                {copied === "codes" ? "copied" : "copy"}
              </button>
              <button
                type="button"
                onClick={downloadRecoveryCodes}
                className="inline-flex items-center gap-2 py-2 px-3 rounded-lg border border-terminal-border text-text-muted text-xs hover:border-git-blue/50 hover:text-text-primary transition-colors"
              >
                <Download size={13} />
                download .txt
              </button>
            </div>
          </div>

          <Link
            to="/admin"
            className="inline-flex items-center gap-2 py-2.5 px-4 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 hover:border-git-green/70 transition-all duration-200"
          >
            <Check size={13} />
            I&apos;ve saved my codes — done
          </Link>
        </div>
      )}
    </div>
  );
}
