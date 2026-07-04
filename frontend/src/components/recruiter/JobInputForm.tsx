
import { FormEvent } from "react";
import { Sparkles } from "lucide-react";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { cn } from "@/lib/utils";
import { JD_MAX, JD_MIN } from "@/lib/recruiter/types";

const SAMPLE_JD = `Senior Full-Stack Engineer at AcmeCorp

We're looking for an engineer to own features end-to-end across a React + TypeScript
frontend and a Java/Spring Boot backend. You'll design REST APIs, model data in
PostgreSQL, and ship to cloud infrastructure with CI/CD.

Requirements:
- 3+ years building production web applications
- Strong React, TypeScript, and modern CSS
- Java + Spring Boot (or similar backend framework)
- PostgreSQL schema design and query tuning
- Docker and cloud deployment experience

Nice to have: LLM/RAG integrations, security hardening, OAuth2/JWT auth flows.`;

interface JobInputFormProps {
  value: string;
  onChange: (next: string) => void;
  onSubmit: () => void;
  loading: boolean;
  error: string | null;
}

export function JobInputForm({
  value,
  onChange,
  onSubmit,
  loading,
  error,
}: JobInputFormProps) {
  const len = value.length;
  const tooShort = len > 0 && len < JD_MIN;
  const tooLong = len > JD_MAX;
  const canSubmit = !loading && len >= JD_MIN && len <= JD_MAX;

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (canSubmit) onSubmit();
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2 bg-terminal-bg border-b border-terminal-border font-mono text-[11px] text-text-faint">
          <span>job-description.md</span>
          <span
            className={cn(
              "tabular-nums",
              tooLong
                ? "text-git-red"
                : tooShort
                  ? "text-git-yellow"
                  : "text-text-faint"
            )}
          >
            {len.toLocaleString()} / {JD_MAX.toLocaleString()}
          </span>
        </div>
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={`Paste the full job description here.\n\nExample:\n  Senior Full-Stack Engineer at AcmeCorp\n  We're looking for someone with strong React, Node.js, and Postgres experience...`}
          disabled={loading}
          rows={12}
          maxLength={JD_MAX + 200}
          className="w-full resize-y bg-transparent p-4 text-base sm:text-sm font-mono text-text-primary placeholder-text-faint outline-none focus-visible:ring-1 focus-visible:ring-git-green/40 disabled:opacity-60 leading-relaxed min-h-[220px]"
          spellCheck={false}
        />
      </div>

      {error && (
        <p className="font-mono text-xs text-git-red px-1" role="alert">
          {error}
        </p>
      )}

      <div className="flex items-center justify-between gap-3">
        <p className="text-text-faint text-[11px] font-mono">
          {tooShort ? (
            `at least ${JD_MIN} chars please`
          ) : tooLong ? (
            `over the ${JD_MAX} char limit`
          ) : (
            <>
              we don&apos;t store anything — submissions are ephemeral
              {len === 0 && (
                <>
                  {" · "}
                  <button
                    type="button"
                    onClick={() => onChange(SAMPLE_JD)}
                    className="text-git-blue hover:underline cursor-pointer focus-visible:ring-1 focus-visible:ring-git-green/40 rounded outline-none"
                  >
                    or try a sample JD
                  </button>
                </>
              )}
            </>
          )}
        </p>
        <LoadingButton
          type="submit"
          loading={loading}
          loadingText="Analyzing…"
          disabled={!canSubmit}
          className="shrink-0 whitespace-nowrap"
        >
          <Sparkles size={12} />
          Analyze fit
        </LoadingButton>
      </div>
    </form>
  );
}
