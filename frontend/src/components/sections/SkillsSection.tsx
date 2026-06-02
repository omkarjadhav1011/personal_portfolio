
import { GitBranch } from "lucide-react";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import type { Skill, SkillBranch } from "@/types";

function ProfDots({ level, color }: { level: number; color: string }) {
  return (
    <span className="inline-flex gap-[3px]">
      {[1, 2, 3, 4, 5].map((l) => {
        const on = l <= level;
        return (
          <span
            key={l}
            className="w-1.5 h-1.5 rounded-full transition-all"
            style={{
              background: on ? color : "rgb(var(--color-terminal-border))",
              boxShadow: on ? `0 0 4px ${color}99` : "none",
            }}
          />
        );
      })}
    </span>
  );
}

function SkillRow({ skill, color }: { skill: Skill; color: string }) {
  return (
    <div
      className="group flex items-center gap-2 px-3 py-2 rounded-md cursor-default transition-colors bg-terminal-bg/60 border border-terminal-border hover:bg-[var(--row-bg)] hover:border-[var(--row-border)]"
      style={
        {
          "--row-bg": `${color}0d`,
          "--row-border": `${color}99`,
        } as React.CSSProperties
      }
    >
      <span className="font-mono text-xs flex-1 truncate text-text-primary">{skill.name}</span>
      {skill.tag && (
        <span
          className="font-mono text-[9px] px-1 py-[1px] rounded"
          style={{ background: `${color}26`, color }}
        >
          {skill.tag}
        </span>
      )}
      <ProfDots level={skill.level} color={color} />
    </div>
  );
}

function BranchBlock({
  branch,
  isLast,
  commitsCount,
}: {
  branch: SkillBranch;
  isLast: boolean;
  commitsCount: number;
}) {
  const color = branch.color;
  return (
    <div className="relative pl-12 pb-6">
      {/* Vertical rail */}
      <div
        className="absolute left-[14px] top-0 w-px"
        style={{
          height: isLast ? 22 : "100%",
          background: `${color}66`,
        }}
      />

      {/* Branch node dot */}
      <div
        className="absolute rounded-full"
        style={{
          left: 8,
          top: 14,
          width: 13,
          height: 13,
          border: `2px solid ${color}`,
          background: "rgb(var(--color-terminal-bg))",
          boxShadow: `0 0 0 3px ${color}1f, 0 0 10px ${color}66`,
        }}
      />

      {/* Curve from rail to branch label */}
      <svg
        className="absolute"
        style={{
          left: 14,
          top: 0,
          width: 26,
          height: 24,
          overflow: "visible",
          pointerEvents: "none",
        }}
        viewBox="0 0 26 24"
      >
        <path
          d="M 0 0 L 0 12 Q 0 20 8 20 L 22 20"
          fill="none"
          stroke={`${color}8c`}
          strokeWidth="1.2"
        />
      </svg>

      {/* Header */}
      <div className="flex items-center justify-between mb-2.5 font-mono text-xs">
        <div className="flex items-center gap-2">
          <GitBranch size={12} style={{ color }} />
          <span style={{ color, fontWeight: 600 }}>{branch.branchName}</span>
          <span
            className="ml-1 px-1.5 py-[1px] rounded text-[10px]"
            style={{ background: `${color}26`, color }}
          >
            {commitsCount} commits
          </span>
        </div>
        <span className="text-text-faint">last updated 2d ago</span>
      </div>

      {/* Skill grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        {branch.skills.map((s) => (
          <SkillRow key={s.name} skill={s} color={color} />
        ))}
      </div>
    </div>
  );
}

interface SkillsSectionProps {
  skillBranches: SkillBranch[];
}

export function SkillsSection({ skillBranches }: SkillsSectionProps) {
  const total = skillBranches.reduce((a, b) => a + b.skills.length, 0);
  const branchCount = skillBranches.length;

  return (
    <section id="skills" className="py-16 sm:py-24 px-4 scroll-mt-14">
      <div className="max-w-5xl mx-auto">
        <ScrollReveal>
          <div className="flex items-center gap-3 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">##</span>
            <span>skills</span>
          </div>
          <h2 className="font-mono font-bold text-3xl sm:text-4xl md:text-5xl mb-2 text-text-primary">
            Branches in the workspace
          </h2>
          <div className="font-mono text-xs mb-6 text-text-muted">
            <span className="text-git-green">$</span> git branch -a --verbose
          </div>
        </ScrollReveal>

        <ScrollReveal delay={0.05}>
          <div className="rounded-xl overflow-hidden bg-terminal-surface border border-terminal-border shadow-terminal">
            {/* Title bar */}
            <div className="flex items-center gap-2 px-4 py-2.5 border-b border-terminal-border bg-terminal-bg/60 font-mono text-xs">
              <span className="w-3 h-3 rounded-full bg-dot-red" />
              <span className="w-3 h-3 rounded-full bg-dot-yellow" />
              <span className="w-3 h-3 rounded-full bg-dot-green" />
              <span className="flex-1 text-center text-text-faint">
                ~/skills · git branch -a
              </span>
              <span className="w-12" />
            </div>

            {/* Body */}
            <div className="p-5 sm:p-6">
              {skillBranches.map((branch, i) => (
                <BranchBlock
                  key={branch.branchName}
                  branch={branch}
                  isLast={i === skillBranches.length - 1}
                  commitsCount={branch.skills.length}
                />
              ))}
            </div>
          </div>
        </ScrollReveal>

        <ScrollReveal delay={0.15}>
          <div className="mt-4 font-mono text-xs text-text-faint">
            <span className="text-git-green">$</span> git diff main...HEAD --stat
            <span className="ml-3">
              {`// ${total} skills · ${branchCount} active branches`}
            </span>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}
