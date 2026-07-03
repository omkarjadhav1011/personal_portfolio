import { useState, type FormEvent } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { useSubmitLead } from "@/api/leads";
import { ApiError } from "@/lib/api";
import type { MatchResult } from "@/lib/recruiter/types";

/** Session flag: once a lead is sent, don't ask again for the rest of the browser session. */
const SENT_KEY = "recruiter-lead-sent";

/** Mirrors the server-side `jd_excerpt varchar(500)` cap. */
const JD_EXCERPT_MAX = 500;

const NETWORK_ERROR = "Couldn't send. Check your connection.";

interface LeadCardProps {
  match: MatchResult;
  jobDescription: string;
  ownerName: string;
}

/**
 * Post-match lead capture: rendered only below a MatchResult — the ask comes
 * after the value, never before it. Email is the only required field; the
 * match context travels with the lead so follow-up starts from the score.
 */
export function LeadCard({ match, jobDescription, ownerName }: LeadCardProps) {
  const [sentThisSession] = useState(
    () => sessionStorage.getItem(SENT_KEY) === "1",
  );
  const shouldReduce = useReducedMotion();
  const lead = useSubmitLead();

  if (sentThisSession) return null;

  const firstName = ownerName.split(" ")[0] || ownerName;

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const company = String(form.get("company") ?? "").trim();
    lead.mutate(
      {
        email: String(form.get("email") ?? "").trim(),
        company: company || undefined,
        fitScore: Math.round(match.fitScore),
        matchedSkills: match.matchedSkills.map((s) => s.name),
        jdExcerpt: jobDescription.slice(0, JD_EXCERPT_MAX),
        honeypot: String(form.get("honeypot") ?? ""),
      },
      { onSuccess: () => sessionStorage.setItem(SENT_KEY, "1") },
    );
  }

  const busy = lead.isPending;
  const errorMessage = lead.isError
    ? lead.error instanceof ApiError
      ? lead.error.message
      : NETWORK_ERROR
    : null;

  return (
    <motion.section
      initial={shouldReduce ? false : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className="rounded-xl border border-terminal-border bg-terminal-surface p-4 sm:p-5 space-y-3"
    >
      <div className="font-mono text-sm space-y-1">
        <p className="text-text-primary">
          <span className="text-git-green mr-2">$</span>
          want {firstName} to see this match?
        </p>
        <p className="text-text-faint text-xs">
          # leave an email — the fit score and matched skills go with it
        </p>
      </div>

      {lead.isSuccess ? (
        <p className="font-mono text-sm text-git-green" role="status">
          ✓ sent — he'll reach out
        </p>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-3">
          {/* Honeypot — hidden from real users, filled by bots */}
          <input
            type="text"
            name="honeypot"
            tabIndex={-1}
            aria-hidden="true"
            className="absolute opacity-0 pointer-events-none h-0"
            autoComplete="off"
          />
          <div className="flex flex-col sm:flex-row gap-3">
            <input
              type="email"
              name="email"
              required
              maxLength={255}
              disabled={busy}
              placeholder="your@email.com"
              aria-label="Your email"
              className="flex-1 min-w-0 bg-terminal-bg border border-terminal-border rounded-lg px-4 py-2.5 font-mono text-sm text-text-primary placeholder-text-faint outline-none focus:border-git-green/60 focus-visible:ring-1 focus-visible:ring-git-green/30 transition-colors disabled:opacity-50"
            />
            <input
              type="text"
              name="company"
              maxLength={150}
              disabled={busy}
              placeholder="company (optional)"
              aria-label="Company (optional)"
              className="flex-1 min-w-0 bg-terminal-bg border border-terminal-border rounded-lg px-4 py-2.5 font-mono text-sm text-text-primary placeholder-text-faint outline-none focus:border-git-green/60 focus-visible:ring-1 focus-visible:ring-git-green/30 transition-colors disabled:opacity-50"
            />
            <button
              type="submit"
              disabled={busy}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green font-mono text-sm whitespace-nowrap hover:bg-git-green/20 hover:border-git-green/70 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 cursor-pointer"
            >
              {busy ? (
                <>
                  <span className="animate-pulse">●</span>
                  <span>sending...</span>
                </>
              ) : (
                <span>git request-review</span>
              )}
            </button>
          </div>
          {errorMessage && (
            <p className="font-mono text-xs text-git-red" role="alert">
              ✗ {errorMessage}
            </p>
          )}
        </form>
      )}
    </motion.section>
  );
}
