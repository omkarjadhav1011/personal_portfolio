
import type { GapSkill, MatchedSkill } from "@/lib/recruiter/types";

interface SkillsMatchDiffProps {
  matched: MatchedSkill[];
  gaps: GapSkill[];
}

export function SkillsMatchDiff({ matched, gaps }: SkillsMatchDiffProps) {
  const total = matched.length + gaps.length;
  if (total === 0) {
    return (
      <div className="rounded-xl border border-dashed border-terminal-border bg-terminal-surface/40 p-6 text-center font-mono text-xs text-text-faint">
        No skill overlaps or gaps detected.
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden font-mono text-sm">
      {/* Diff file header */}
      <div className="px-5 py-3 bg-terminal-bg border-b border-terminal-border text-text-faint text-xs">
        <div>diff --git a/skills/required b/skills/have</div>
        <div className="text-text-muted mt-0.5">
          @@ -required +have @@ skills.match
        </div>
      </div>

      {/* Diff lines */}
      <div className="p-4 space-y-1">
        {matched.map((s, i) => (
          <div
            key={`+${i}`}
            className="flex items-baseline gap-3 px-2 py-0.5 rounded bg-git-green/5 hover:bg-git-green/10 transition-colors"
          >
            <span className="text-git-green font-bold w-3 shrink-0">+</span>
            <span className="text-git-green">{s.name}</span>
            <span className="text-git-green/60 text-xs ml-auto truncate max-w-[60%]">
              # {s.reason}
            </span>
          </div>
        ))}
        {gaps.map((g, i) => {
          const isMust = g.importance === "must-have";
          const rowClass = isMust
            ? "flex items-baseline gap-3 px-2 py-0.5 rounded bg-git-red/5 hover:bg-git-red/10 transition-colors"
            : "flex items-baseline gap-3 px-2 py-0.5 rounded bg-git-yellow/5 hover:bg-git-yellow/10 transition-colors";
          const textClass = isMust ? "text-git-red" : "text-git-yellow";
          const noteClass = isMust ? "text-git-red/60" : "text-git-yellow/60";
          return (
            <div key={`-${i}`} className={rowClass}>
              <span className={`${textClass} font-bold w-3 shrink-0`}>−</span>
              <span className={textClass}>{g.name}</span>
              <span className={`${noteClass} text-xs ml-auto`}>
                # {g.importance}
              </span>
            </div>
          );
        })}
      </div>

      {/* Diff footer */}
      <div className="px-5 py-2 bg-terminal-bg border-t border-terminal-border text-text-faint text-xs flex gap-4">
        <span className="text-git-green">+{matched.length} matched</span>
        <span className="text-git-red">
          −{gaps.filter((g) => g.importance === "must-have").length} must-have
          gaps
        </span>
        <span className="text-git-yellow">
          ~{gaps.filter((g) => g.importance === "nice-to-have").length}{" "}
          nice-to-have gaps
        </span>
      </div>
    </div>
  );
}
