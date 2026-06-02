
import { PRCard } from "@/components/ui/PRCard";
import type { Project } from "@/types";
import type { MatchedProject } from "@/lib/recruiter/types";

interface MatchedProjectsProps {
  matches: MatchedProject[];
  projectsBySlug: Map<string, Project>;
  handle: string;
}

export function MatchedProjects({
  matches,
  projectsBySlug,
  handle,
}: MatchedProjectsProps) {
  const resolved = matches
    .map((m) => ({ match: m, project: projectsBySlug.get(m.slug) }))
    .filter((r): r is { match: MatchedProject; project: Project } =>
      Boolean(r.project)
    );

  if (resolved.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-terminal-border bg-terminal-surface/40 p-6 text-center font-mono text-xs text-text-faint">
        No projects in the portfolio strongly match this role.
      </div>
    );
  }

  return (
    <div className="grid gap-4 md:grid-cols-2">
      {resolved.map(({ match, project }, i) => (
        <PRCard
          key={project.id}
          project={project}
          index={i}
          handle={handle}
          matchReason={match.reason}
          matchTags={match.relevantTags}
        />
      ))}
    </div>
  );
}
