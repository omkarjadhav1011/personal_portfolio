import { useParams, Link } from "react-router-dom";
import { ExternalLink, GitFork, Star, GitCommitHorizontal } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { LanguageDot } from "@/components/ui/LanguageDot";
import { useProject } from "@/api/projects";

const STATUS_COLORS: Record<string, string> = {
  active: "text-git-green border-git-green/40 bg-git-green/10",
  wip: "text-git-yellow border-git-yellow/40 bg-git-yellow/10",
  archived: "text-text-muted border-terminal-border bg-terminal-surface",
};

/** Project detail page (replaces (main)/projects/[slug]/page.tsx). Fetches by slug via useQuery. */
export default function ProjectDetail() {
  const { slug } = useParams<{ slug: string }>();
  const { data: project, isPending, isError } = useProject(slug);

  if (isPending) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-terminal-bg font-mono text-sm text-text-muted">
        <p>
          <span className="text-git-green">$</span> git show {slug}
          <span className="ml-1 inline-block h-4 w-2 translate-y-0.5 animate-cursor-blink bg-git-green" />
        </p>
      </main>
    );
  }

  if (isError || !project) {
    return (
      <main className="min-h-screen flex items-center justify-center px-4 bg-terminal-bg font-mono">
        <div className="max-w-md w-full space-y-4 text-center">
          <p className="text-git-red font-bold">
            fatal: pathspec &apos;{slug}&apos; did not match any project
          </p>
          <Link
            to="/#projects"
            className="inline-flex items-center gap-2 text-sm text-text-muted hover:text-git-green transition-colors"
          >
            <span>←</span>
            <span className="text-git-green">$</span> git checkout main
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen py-24 px-4">
      <div className="max-w-4xl mx-auto space-y-8">
        {/* Back navigation */}
        <Link
          to="/#projects"
          className="inline-flex items-center gap-2 font-mono text-sm text-text-muted hover:text-git-green transition-colors"
        >
          <span>←</span>
          <span className="text-git-green">$</span>
          git checkout main
        </Link>

        {/* Project header */}
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden shadow-terminal">
          {/* Terminal title bar */}
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <span className="w-3 h-3 rounded-full bg-[#ff5f57]" />
            <span className="w-3 h-3 rounded-full bg-[#febc2e]" />
            <span className="w-3 h-3 rounded-full bg-[#28c840]" />
            <span className="ml-2 text-xs text-text-muted font-mono">
              {project.repoName} — detail
            </span>
          </div>

          <div className="p-6 md:p-8 space-y-6 font-mono">
            {/* Repo name + status */}
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-git-blue text-xl">📁</span>
                  <h1 className="text-2xl md:text-3xl font-bold text-git-blue">
                    {project.repoName}
                  </h1>
                </div>
                <p className="text-text-muted text-sm">{project.description}</p>
              </div>
              <span
                className={`text-xs px-2.5 py-1 rounded-full border font-mono ${STATUS_COLORS[project.status] ?? ""}`}
              >
                {project.status}
              </span>
            </div>

            {/* Stats row */}
            <div className="flex flex-wrap items-center gap-4 text-sm text-text-muted">
              <LanguageDot language={project.language} color={project.languageColor} />
              <span className="flex items-center gap-1.5">
                <Star size={14} />
                {project.stars}
              </span>
              <span className="flex items-center gap-1.5">
                <GitFork size={14} />
                {project.forks}
              </span>
              <span className="text-text-faint">{project.commits} commits</span>
            </div>

            {/* Last commit */}
            <div className="flex items-center gap-2 text-xs text-text-faint bg-terminal-bg/60 rounded px-3 py-2 border border-terminal-border/50">
              <GitCommitHorizontal size={13} className="text-git-green shrink-0" />
              <span className="text-git-green/70 font-mono">
                {project.id.padStart(7, "0").slice(-7)}
              </span>
              <span className="font-mono truncate">{project.lastCommitMsg}</span>
              <span className="ml-auto shrink-0">{project.lastCommit}</span>
            </div>

            <hr className="border-terminal-border" />

            {/* Long description */}
            <div>
              <h2 className="text-git-blue font-bold mb-3">## About</h2>
              <p className="text-text-secondary text-sm leading-7">
                {project.longDescription ?? project.description}
              </p>
            </div>

            <hr className="border-terminal-border" />

            {/* Tech stack */}
            <div>
              <h2 className="text-git-blue font-bold mb-3">## Tech Stack</h2>
              <div className="flex flex-wrap gap-2">
                {project.tags.map((tag) => (
                  <Badge key={tag} variant="tag">
                    {tag}
                  </Badge>
                ))}
              </div>
            </div>

            <hr className="border-terminal-border" />

            {/* Links */}
            <div>
              <h2 className="text-git-blue font-bold mb-3">## Links</h2>
              <div className="flex flex-wrap gap-3">
                {project.repoUrl && (
                  <a
                    href={project.repoUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 px-4 py-2 rounded-lg border border-terminal-border bg-terminal-bg text-text-secondary text-sm font-mono hover:border-git-blue/50 hover:text-git-blue transition-all duration-200"
                  >
                    <span className="text-text-muted">$</span>
                    git remote -v
                  </a>
                )}
                {project.liveUrl && (
                  <a
                    href={project.liveUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 px-4 py-2 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-sm font-mono hover:bg-git-green/20 hover:border-git-green/70 transition-all duration-200"
                  >
                    <ExternalLink size={13} />
                    Live Demo
                  </a>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
