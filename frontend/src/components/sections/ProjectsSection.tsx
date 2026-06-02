
import { useState } from "react";
import { ExternalLink, SearchX } from "lucide-react";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import { EmptyState } from "@/components/ui/EmptyState";
import { PRCard } from "@/components/ui/PRCard";
import type { Project } from "@/types";

type Filter = "all" | "active" | "wip" | "archived";

interface ProjectsSectionProps {
  projects: Project[];
  githubUrl: string;
}

export function ProjectsSection({ projects, githubUrl }: ProjectsSectionProps) {
  const [filter, setFilter] = useState<Filter>("all");

  const handle = (() => {
    const m = /github\.com\/([^/]+)/i.exec(githubUrl);
    return m?.[1] ?? "you";
  })();

  const counts = projects.reduce<Record<string, number>>((acc, p) => {
    acc[p.status] = (acc[p.status] ?? 0) + 1;
    return acc;
  }, {});

  const filters: { k: Filter; label: string; count: number }[] = [
    { k: "all", label: "All", count: projects.length },
    { k: "active", label: "Open", count: counts.active ?? 0 },
    { k: "wip", label: "WIP", count: counts.wip ?? 0 },
    { k: "archived", label: "Archived", count: counts.archived ?? 0 },
  ];

  const filtered = filter === "all" ? projects : projects.filter((p) => p.status === filter);

  return (
    <section id="projects" className="py-16 sm:py-24 px-4 scroll-mt-14">
      <div className="max-w-5xl mx-auto">
        <ScrollReveal>
          <div className="flex items-center gap-3 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">$</span>
            <span>gh pr list --state=open</span>
          </div>
          <h2 className="font-mono font-bold text-3xl sm:text-4xl md:text-5xl mb-2 text-text-primary">
            Projects
          </h2>
          <p className="text-text-muted text-sm font-sans mb-6">
            each project as a pull request — review the changes and peek the diff
          </p>
        </ScrollReveal>

        <ScrollReveal delay={0.05}>
          <div className="flex flex-wrap items-center gap-2 mb-6 p-1.5 rounded-xl bg-terminal-surface border border-terminal-border">
            {filters.map((f) => {
              const active = filter === f.k;
              return (
                <button
                  key={f.k}
                  type="button"
                  onClick={() => setFilter(f.k)}
                  className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg font-mono text-xs font-medium transition-colors"
                  style={{
                    background: active ? "rgb(var(--color-git-green) / 0.15)" : "transparent",
                    border: `1px solid ${
                      active ? "rgb(var(--color-git-green) / 0.45)" : "transparent"
                    }`,
                    color: active
                      ? "rgb(var(--color-git-green))"
                      : "rgb(var(--color-text-muted))",
                  }}
                >
                  {f.label}
                  <span
                    className="px-1.5 rounded-full text-[10px]"
                    style={{
                      background: active
                        ? "rgb(var(--color-git-green) / 0.2)"
                        : "rgb(var(--color-terminal-bg) / 0.7)",
                      color: active
                        ? "rgb(var(--color-git-green))"
                        : "rgb(var(--color-text-faint))",
                    }}
                  >
                    {f.count}
                  </span>
                </button>
              );
            })}
            <span className="ml-auto px-2 font-mono text-[11px] text-text-faint">
              showing {filtered.length} of {projects.length}
            </span>
          </div>
        </ScrollReveal>

        <div className="grid gap-4 md:grid-cols-2">
          {filtered.map((p, i) => (
            <PRCard key={p.id} project={p} index={i} handle={handle} />
          ))}
        </div>

        {filtered.length === 0 && (
          <EmptyState
            icon={<SearchX size={32} />}
            title="warning: no objects found matching filter"
            description="try a different filter or view all repositories"
          />
        )}

        <ScrollReveal delay={0.2}>
          <div className="mt-10 text-center">
            <a
              href={githubUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 font-mono text-sm text-text-muted hover:text-git-blue transition-colors"
            >
              <span className="text-git-green">→</span>
              View all repositories on GitHub
              <ExternalLink size={12} className="opacity-40" />
            </a>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}
