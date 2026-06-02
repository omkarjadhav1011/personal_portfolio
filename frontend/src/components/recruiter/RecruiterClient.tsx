
import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import { ArrowLeft, FileSearch, GitBranch, Sparkles } from "lucide-react";
import { JobInputForm } from "@/components/recruiter/JobInputForm";
import { MatchedProjects } from "@/components/recruiter/MatchedProjects";
import { SkillsMatchDiff } from "@/components/recruiter/SkillsMatchDiff";
import { CoverLetterStream } from "@/components/recruiter/CoverLetterStream";
import type { Project } from "@/types";
import type { MatchResult } from "@/lib/recruiter/types";

interface RecruiterClientProps {
  projects: Project[];
  handle: string;
}

const RATE_LIMITED = "Too many submissions. Try again in a minute.";
const NETWORK_ERROR = "Couldn't reach the analyzer. Check your connection.";
const GENERIC_ERROR = "Something went wrong analyzing this job description.";

export function RecruiterClient({ projects, handle }: RecruiterClientProps) {
  const [jd, setJd] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [match, setMatch] = useState<MatchResult | null>(null);
  const [submittedJd, setSubmittedJd] = useState<string>("");

  const projectsBySlug = useMemo(
    () => new Map(projects.map((p) => [p.slug, p])),
    [projects]
  );

  async function analyze() {
    setLoading(true);
    setError(null);
    setMatch(null);
    try {
      const res = await fetch("/api/recruiter/match", {
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
  }

  if (match) {
    return (
      <div className="space-y-8">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <button
            type="button"
            onClick={reset}
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg font-mono text-xs bg-terminal-surface border border-terminal-border text-text-muted hover:text-text-primary hover:border-git-green/40 transition-colors cursor-pointer"
          >
            <ArrowLeft size={12} />
            Try a different role
          </button>
          <FitScoreBadge score={match.fitScore} />
        </div>

        <ReportSection
          icon={<GitBranch size={14} className="text-git-green" />}
          title="Matched projects"
          subtitle={`${match.matchedProjects.length} pull request${
            match.matchedProjects.length === 1 ? "" : "s"
          } worth reviewing`}
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
        >
          <SkillsMatchDiff
            matched={match.matchedSkills}
            gaps={match.gapSkills}
          />
        </ReportSection>

        <ReportSection
          icon={<Sparkles size={14} className="text-git-green" />}
          title="A short note for the role"
          subtitle="written in first person, tailored to this JD"
        >
          <CoverLetterStream
            jobDescription={submittedJd}
            matchResult={match}
          />
        </ReportSection>
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
  children,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <motion.section
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
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

function FitScoreBadge({ score }: { score: number }) {
  const tone =
    score >= 75
      ? { color: "git-green", label: "strong fit" }
      : score >= 50
        ? { color: "git-blue", label: "partial fit" }
        : score >= 25
          ? { color: "git-yellow", label: "stretch fit" }
          : { color: "git-red", label: "low fit" };

  const cls =
    tone.color === "git-green"
      ? "bg-git-green/10 border-git-green/40 text-git-green"
      : tone.color === "git-blue"
        ? "bg-git-blue/10 border-git-blue/40 text-git-blue"
        : tone.color === "git-yellow"
          ? "bg-git-yellow/10 border-git-yellow/40 text-git-yellow"
          : "bg-git-red/10 border-git-red/40 text-git-red";

  return (
    <span
      className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border font-mono text-xs ${cls}`}
      title={`Fit score: ${score}/100`}
    >
      <span className="font-bold tabular-nums">{Math.round(score)}</span>
      <span className="text-[10px] uppercase tracking-wider opacity-80">
        {tone.label}
      </span>
    </span>
  );
}
