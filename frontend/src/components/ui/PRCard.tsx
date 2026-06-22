
import { useState } from "react";
import {
  ChevronRight,
  ExternalLink,
  Folder,
  GitFork,
  Sparkles,
  Star,
} from "lucide-react";
import { Link } from "react-router-dom";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import type { Project } from "@/types";

const STATUS_STYLES: Record<
  Project["status"],
  { label: string; tintVar: string; glyph: string }
> = {
  active: { label: "Open", tintVar: "--color-git-green", glyph: "●" },
  wip: { label: "WIP", tintVar: "--color-git-orange", glyph: "◐" },
  archived: { label: "Archived", tintVar: "--color-text-muted", glyph: "◌" },
};

interface PRCardProps {
  project: Project;
  index: number;
  handle: string;
  matchReason?: string;
  matchTags?: string[];
}

export function PRCard({
  project,
  index,
  handle,
  matchReason,
  matchTags,
}: PRCardProps) {
  const [expanded, setExpanded] = useState(false);
  const status = STATUS_STYLES[project.status];
  const matchTagSet = new Set((matchTags ?? []).map((t) => t.toLowerCase()));

  const previewLines: { type: "context" | "add"; text: string }[] = [
    { type: "context", text: `// ${project.repoName}` },
    { type: "add", text: `+ ${project.lastCommitMsg}` },
    ...project.tags.slice(0, 2).map(
      (t) => ({ type: "context" as const, text: `  using: ${t}` }),
    ),
  ];

  return (
    <ScrollReveal delay={index * 0.06}>
      <div className="group rounded-xl overflow-hidden transition-transform hover:-translate-y-0.5 bg-terminal-surface border border-terminal-border">
        {/* Status strip */}
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-terminal-border bg-terminal-bg/50 font-mono text-[11px]">
          <div className="flex items-center gap-2">
            <span
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full font-semibold"
              style={{
                background: `rgb(var(${status.tintVar}) / 0.12)`,
                color: `rgb(var(${status.tintVar}))`,
                border: `1px solid rgb(var(${status.tintVar}) / 0.4)`,
              }}
            >
              <span style={{ fontSize: 9 }}>{status.glyph}</span> {status.label}
            </span>
            <span className="text-text-faint">#{index + 1}</span>
          </div>
          <div className="flex items-center gap-3 text-text-muted">
            <span className="inline-flex items-center gap-1">
              <Star size={11} /> {project.stars}
            </span>
            <span className="inline-flex items-center gap-1">
              <GitFork size={11} /> {project.forks}
            </span>
          </div>
        </div>

        {/* Match reason — only when prop is supplied (recruiter mode) */}
        {matchReason && (
          <div className="px-4 py-2.5 bg-git-green/5 border-b border-git-green/20 font-mono text-[11px] flex items-start gap-2">
            <Sparkles size={12} className="text-git-green shrink-0 mt-0.5" />
            <div className="flex-1">
              <span className="text-git-green font-semibold">+ matches:</span>{" "}
              <span className="text-text-secondary">{matchReason}</span>
            </div>
          </div>
        )}

        {/* Body */}
        <div className="p-4 sm:p-5">
          <div className="flex items-center gap-2 mb-1 font-mono text-xs">
            <Folder size={12} className="text-git-blue" />
            <span className="text-text-muted">{handle}/</span>
            <span className="font-semibold text-git-blue">{project.repoName}</span>
          </div>

          <h3 className="text-lg font-semibold leading-snug mb-2 text-text-primary font-mono">
            {project.lastCommitMsg}
          </h3>

          <p className="text-sm mb-4 leading-relaxed text-text-secondary font-mono">
            {project.description}
          </p>

          {/* Meta row */}
          <div className="flex items-center gap-4 mb-3 text-[11px] font-mono text-text-muted">
            <span className="inline-flex items-center gap-1.5">
              <span
                className="w-2.5 h-2.5 rounded-full"
                style={{ background: project.languageColor }}
              />
              {project.language}
            </span>
            <span className="inline-flex items-center gap-1">
              <span className="text-git-green">+{project.commits}</span>
              <span className="text-git-red">−0</span>
            </span>
            <span className="text-text-faint">{project.tags.length} tags</span>
          </div>

          {/* Tags — highlight ones that matched the JD */}
          <div className="flex flex-wrap gap-1.5 mb-4">
            {project.tags.map((t) => {
              const matched = matchTagSet.has(t.toLowerCase());
              return (
                <span
                  key={t}
                  className={
                    matched
                      ? "px-2 py-0.5 rounded-full text-[10px] font-mono bg-git-green/15 border border-git-green/40 text-git-green font-semibold"
                      : "px-2 py-0.5 rounded-full text-[10px] font-mono bg-terminal-bg/70 border border-terminal-border text-text-muted"
                  }
                >
                  {t}
                </span>
              );
            })}
          </div>

          {/* Diff toggle */}
          <button
            type="button"
            onClick={() => setExpanded(!expanded)}
            className="w-full text-left font-mono text-[11px] flex items-center gap-1.5 hover:brightness-125 mb-3 text-text-muted"
          >
            <ChevronRight
              size={12}
              className="text-git-green transition-transform"
              style={{ transform: expanded ? "rotate(90deg)" : "rotate(0)" }}
            />
            {expanded ? "Hide" : "Show"} diff preview
          </button>

          {expanded && (
            <div className="font-mono text-[11px] overflow-hidden rounded-md mb-3 bg-terminal-bg/70 border border-terminal-border">
              {previewLines.map((line, i) => {
                const styles = {
                  add: {
                    bg: "rgb(var(--color-git-green) / 0.08)",
                    c: "rgb(var(--color-git-green))",
                    g: "+",
                  },
                  context: {
                    bg: "transparent",
                    c: "rgb(var(--color-text-muted))",
                    g: " ",
                  },
                }[line.type];
                return (
                  <div
                    key={i}
                    className="flex gap-3 px-3 py-0.5"
                    style={{ background: styles.bg }}
                  >
                    <span
                      className="w-5 text-right"
                      style={{ color: "rgb(var(--color-text-faint))" }}
                    >
                      {i + 1}
                    </span>
                    <span className="w-2.5" style={{ color: styles.c }}>
                      {styles.g}
                    </span>
                    <span style={{ color: styles.c }}>
                      {line.text.replace(/^[+−\-]\s?/, "")}
                    </span>
                  </div>
                );
              })}
            </div>
          )}

          {/* Actions */}
          <div className="pt-3 border-t border-terminal-border flex gap-2">
            <Link
              to={`/projects/${project.slug}`}
              className="flex-1 inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg font-mono text-xs font-semibold transition-colors bg-git-green/10 border border-git-green/40 text-git-green hover:bg-git-green/20"
            >
              <ChevronRight size={11} /> Details
            </Link>
            <a
              href={project.repoUrl || `https://github.com/${handle}/${project.slug}`}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-1 inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg font-mono text-xs font-semibold transition-colors bg-terminal-bg/70 border border-terminal-border text-text-primary hover:border-git-blue/50"
            >
              <ExternalLink size={11} /> View Source
            </a>
          </div>
        </div>
      </div>
    </ScrollReveal>
  );
}
