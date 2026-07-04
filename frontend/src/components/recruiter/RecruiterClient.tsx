
import { useEffect, useMemo, useRef, useState } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { ArrowLeft, Check, Copy, FileSearch, GitBranch, Send } from "lucide-react";
import { JobInputForm } from "@/components/recruiter/JobInputForm";
import { AnalysisProgress } from "@/components/recruiter/AnalysisProgress";
import { FitScoreHero } from "@/components/recruiter/FitScoreHero";
import { MatchedProjects } from "@/components/recruiter/MatchedProjects";
import { SkillsMatchDiff } from "@/components/recruiter/SkillsMatchDiff";
import { LeadCard } from "@/components/recruiter/LeadCard";
import { BASE_URL } from "@/lib/api";
import { copyToClipboard } from "@/lib/clipboard";
import { buildReportMarkdown } from "@/lib/recruiter/report";
import type { Project } from "@/types";
import type { MatchResult } from "@/lib/recruiter/types";

interface RecruiterClientProps {
  projects: Project[];
  handle: string;
  ownerName: string;
  /** F1 booking link (from the profile's socials) — absent → no slot line rendered. */
  bookingUrl?: string;
}

const RATE_LIMITED = "Too many submissions. Try again in a minute.";
const NETWORK_ERROR = "Couldn't reach the analyzer. Check your connection.";
const GENERIC_ERROR = "Something went wrong analyzing this job description.";

export function RecruiterClient({ projects, handle, ownerName, bookingUrl }: RecruiterClientProps) {
  const [jd, setJd] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [match, setMatch] = useState<MatchResult | null>(null);
  const [submittedJd, setSubmittedJd] = useState<string>("");
  const [copied, setCopied] = useState(false);
  const resultsRef = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();

  const projectsBySlug = useMemo(
    () => new Map(projects.map((p) => [p.slug, p])),
    [projects]
  );

  // Bring the fit score into view (and park focus there) when results land.
  useEffect(() => {
    if (!match) return;
    const node = resultsRef.current;
    if (!node) return;
    node.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
    node.focus({ preventScroll: true });
  }, [match, reduce]);

  async function analyze() {
    setLoading(true);
    setError(null);
    setMatch(null);
    try {
      // BASE_URL prefix: relative paths hit the Vercel SPA rewrite in prod, not the backend.
      const res = await fetch(`${BASE_URL}/api/recruiter/match`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ jobDescription: jd }),
      });
      if (!res.ok) {
        let message = GENERIC_ERROR;
        if (res.status === 429) message = RATE_LIMITED;
        else if (res.status === 400) {
          try {
            const body = (await res.json()) as { error?: { message?: string } };
            if (body.error?.message) message = body.error.message;
          } catch {
            /* keep generic */
          }
        }
        setError(message);
        return;
      }
      const data = (await res.json()) as MatchResult;
      setMatch(data);
      setSubmittedJd(jd);
    } catch (err: unknown) {
      if (err instanceof Error && err.name === "AbortError") return;
      setError(NETWORK_ERROR);
    } finally {
      setLoading(false);
    }
  }

  function reset() {
    setMatch(null);
    setError(null);
    setCopied(false);
  }

  async function copyReport() {
    if (!match) return;
    const ok = await copyToClipboard(
      buildReportMarkdown(match, projectsBySlug, ownerName)
    );
    if (ok) {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  if (loading) {
    return (
      <AnalysisProgress ownerName={ownerName} projectCount={projects.length} />
    );
  }

  if (match) {
    const score = Math.round(match.fitScore);
    const firstName = ownerName.split(" ")[0] || ownerName;
    return (
      <div ref={resultsRef} tabIndex={-1} className="space-y-8 outline-none scroll-mt-24">
        <p className="sr-only" role="status">
          Analysis complete — fit score {score} of 100
        </p>

        <div className="flex items-center justify-between gap-3 flex-wrap">
          <button
            type="button"
            onClick={reset}
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg font-mono text-xs bg-terminal-surface border border-terminal-border text-text-muted hover:text-text-primary hover:border-git-green/40 transition-colors cursor-pointer"
          >
            <ArrowLeft size={12} />
            Try a different role
          </button>
          <button
            type="button"
            onClick={copyReport}
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg font-mono text-xs bg-terminal-surface border border-terminal-border text-text-muted hover:text-text-primary hover:border-git-green/40 transition-colors cursor-pointer"
          >
            {copied ? (
              <>
                <Check size={12} className="text-git-green" />
                copied ✓
              </>
            ) : (
              <>
                <Copy size={12} />
                Copy report
              </>
            )}
          </button>
        </div>

        <FitScoreHero score={match.fitScore} />

        <ReportSection
          icon={<GitBranch size={14} className="text-git-green" />}
          title="Matched projects"
          subtitle={`${match.matchedProjects.length} pull request${
            match.matchedProjects.length === 1 ? "" : "s"
          } worth reviewing`}
          delay={0.05}
        >
          <MatchedProjects
            matches={match.matchedProjects}
            projectsBySlug={projectsBySlug}
            handle={handle}
          />
        </ReportSection>

        <ReportSection
          icon={<FileSearch size={14} className="text-git-blue" />}
          title="Skills diff"
          subtitle="what overlaps, what's missing"
          delay={0.15}
        >
          <SkillsMatchDiff
            matched={match.matchedSkills}
            gaps={match.gapSkills}
          />
        </ReportSection>

        <ReportSection
          icon={<Send size={14} className="text-git-green" />}
          title={`Connect with ${firstName}`}
          subtitle="this match lands on his phone in seconds"
          delay={0.25}
        >
          <LeadCard
            match={match}
            jobDescription={submittedJd}
            ownerName={ownerName}
          />
        </ReportSection>

        {/* F1 friction remover: outlives the lead card (which hides after a send). */}
        {bookingUrl && (
          <p className="font-mono text-xs text-text-muted">
            # or just grab a slot —{" "}
            <a
              href={bookingUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-git-blue hover:underline"
            >
              book a call
            </a>
          </p>
        )}
      </div>
    );
  }

  return (
    <JobInputForm
      value={jd}
      onChange={setJd}
      onSubmit={analyze}
      loading={loading}
      error={error}
    />
  );
}

function ReportSection({
  icon,
  title,
  subtitle,
  delay = 0,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  delay?: number;
  children: React.ReactNode;
}) {
  const reduce = useReducedMotion();
  return (
    <motion.section
      initial={reduce ? false : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay: reduce ? 0 : delay }}
      className="space-y-3"
    >
      <div className="flex items-baseline gap-2 font-mono">
        {icon}
        <h2 className="text-base sm:text-lg font-semibold text-text-primary">
          {title}
        </h2>
        <span className="text-text-faint text-xs">— {subtitle}</span>
      </div>
      {children}
    </motion.section>
  );
}
