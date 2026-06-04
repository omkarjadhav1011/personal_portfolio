
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import type { SkillDiff } from "@/types";

interface SkillsDiffSectionProps {
  skillsDiff: SkillDiff[];
}

export function SkillsDiffSection({ skillsDiff }: SkillsDiffSectionProps) {
  return (
    <section className="py-16 px-4">
      <div className="max-w-4xl mx-auto">
        <ScrollReveal>
          <div className="flex items-center gap-3 mb-4">
            <span className="text-git-green font-mono text-sm">$</span>
            <span className="font-mono text-text-muted text-sm">
              git diff skills/archive skills/current
            </span>
          </div>
          <h2 className="text-xl font-bold font-mono text-text-primary mb-2">
            Stack Evolution
          </h2>
          <p className="text-text-muted text-sm font-mono mb-8">
            # what&apos;s changed in my toolkit
          </p>
        </ScrollReveal>

        <ScrollReveal delay={0.1}>
          <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden font-mono text-sm">
            {/* Diff file header */}
            <div className="px-5 py-3 bg-terminal-bg border-b border-terminal-border text-text-faint text-xs">
              <div>diff --git a/skills/archive.ts b/skills/current.ts</div>
              <div className="text-text-muted mt-0.5">@@ -skills/archive +skills/current @@ skills/current.ts</div>
            </div>

            {/* Diff lines */}
            <div className="p-4 space-y-1">
              {skillsDiff.map((entry, i) => {
                if (entry.type === "added") {
                  return (
                    <div key={i} className="flex items-baseline gap-3 px-2 py-0.5 rounded bg-git-green/5 hover:bg-git-green/10 transition-colors">
                      <span className="text-git-green font-bold w-3 shrink-0">+</span>
                      <span className="text-git-green">{entry.name}</span>
                      {entry.note && (
                        <span className="text-git-green/50 text-xs ml-auto"># {entry.note}</span>
                      )}
                    </div>
                  );
                }
                if (entry.type === "deprecated") {
                  return (
                    <div key={i} className="flex items-baseline gap-3 px-2 py-0.5 rounded bg-git-red/5 hover:bg-git-red/10 transition-colors">
                      <span className="text-git-red font-bold w-3 shrink-0">-</span>
                      <span className="text-git-red line-through opacity-60">{entry.name}</span>
                      {entry.note && (
                        <span className="text-git-red/50 text-xs ml-auto"># {entry.note}</span>
                      )}
                    </div>
                  );
                }
                return (
                  <div key={i} className="flex items-baseline gap-3 px-2 py-0.5 rounded bg-git-yellow/5 hover:bg-git-yellow/10 transition-colors">
                    <span className="text-git-yellow font-bold w-3 shrink-0">~</span>
                    <span className="text-git-yellow">{entry.name}</span>
                    {entry.note && (
                      <span className="text-git-yellow/50 text-xs ml-auto"># {entry.note}</span>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Diff footer */}
            <div className="px-5 py-2 bg-terminal-bg border-t border-terminal-border text-text-faint text-xs flex gap-4">
              <span className="text-git-green">
                +{skillsDiff.filter((e) => e.type === "added").length} added
              </span>
              <span className="text-git-red">
                -{skillsDiff.filter((e) => e.type === "deprecated").length} deprecated
              </span>
              <span className="text-git-yellow">
                ~{skillsDiff.filter((e) => e.type === "modified").length} modified
              </span>
            </div>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}
