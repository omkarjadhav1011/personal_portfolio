
import { useEffect, useRef, useState } from "react";
import { Clock } from "lucide-react";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import type { CommitEntry, EntryType } from "@/types";

const KIND_MAP: Record<EntryType, { label: string; glyph: string }> = {
  job: { label: "Work", glyph: "⌬" },
  education: { label: "Education", glyph: "✦" },
  achievement: { label: "Milestone", glyph: "◆" },
  project: { label: "Project", glyph: "✦" },
};

const ROW_MIN_HEIGHT = 240;

const MONTHS: Record<string, number> = {
  Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5,
  Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11,
};

function parseDateToMs(dateStr: string): number {
  const [mon, yr] = dateStr.split(" ");
  return new Date(Number(yr), MONTHS[mon] ?? 0).getTime();
}

interface ExperienceSectionProps {
  timeline: CommitEntry[];
}

export function ExperienceSection({ timeline }: ExperienceSectionProps) {
  const sorted = [...timeline].sort(
    (a, b) => parseDateToMs(b.date) - parseDateToMs(a.date)
  );
  const [active, setActive] = useState(0);
  const refs = useRef<Array<HTMLDivElement | null>>([]);

  useEffect(() => {
    const obs = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            const idx = parseInt(
              (e.target as HTMLElement).dataset.idx ?? "0",
              10
            );
            setActive(idx);
          }
        });
      },
      { threshold: 0.5, rootMargin: "-40% 0px -40% 0px" }
    );
    refs.current.forEach((r) => r && obs.observe(r));
    return () => obs.disconnect();
  }, [sorted.length]);

  return (
    <section id="experience" className="py-16 sm:py-24 px-4 scroll-mt-14 bg-terminal-surface/30">
      <div className="max-w-5xl mx-auto">
        <ScrollReveal>
          <div className="flex items-center gap-3 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">$</span>
            <span>git log --graph --oneline --all</span>
          </div>
          <h2 className="font-mono font-bold text-3xl sm:text-4xl md:text-5xl mb-2 text-text-primary">
            Experience
          </h2>
          <p className="text-text-muted text-sm font-sans mb-8">
            walk the commit history
          </p>
        </ScrollReveal>

        <div className="relative">
          <div className="grid grid-cols-[56px_1fr] gap-4 md:gap-8">
            {/* Timeline rail */}
            <div className="relative">
              <div
                className="absolute left-1/2 top-4 bottom-4 w-px -translate-x-1/2"
                style={{ background: "rgb(var(--color-terminal-border))" }}
              />
              <div
                className="absolute left-1/2 top-4 w-px -translate-x-1/2 transition-all duration-500"
                style={{
                  height: `${((active + 1) / sorted.length) * 100}%`,
                  background: "rgb(var(--color-git-green))",
                  boxShadow: "0 0 8px rgb(var(--color-git-green) / 0.5)",
                }}
              />
              {sorted.map((c, i) => (
                <div
                  key={c.hash}
                  className="relative flex items-center justify-center"
                  style={{
                    height: `calc(100% / ${sorted.length})`,
                    minHeight: ROW_MIN_HEIGHT,
                  }}
                >
                  <div
                    className="absolute top-1/2 -translate-y-1/2 w-5 h-5 rounded-full transition-all duration-300 flex items-center justify-center"
                    style={{
                      background:
                        i <= active ? c.branchColor : "rgb(var(--color-terminal-surface))",
                      border: `2px solid ${c.branchColor}`,
                      boxShadow:
                        i === active
                          ? `0 0 0 5px ${c.branchColor}40, 0 0 20px ${c.branchColor}80`
                          : "none",
                      transform:
                        i === active
                          ? "translateY(-50%) scale(1.25)"
                          : "translateY(-50%) scale(1)",
                    }}
                  >
                    {i === active && (
                      <span
                        className="w-1.5 h-1.5 rounded-full"
                        style={{ background: "rgb(var(--color-terminal-bg))" }}
                      />
                    )}
                  </div>
                </div>
              ))}
            </div>

            {/* Cards */}
            <div>
              {sorted.map((c, i) => {
                const kind = KIND_MAP[c.type] ?? KIND_MAP.project;
                const isActive = i === active;
                return (
                  <div
                    key={c.hash}
                    ref={(el) => {
                      refs.current[i] = el;
                    }}
                    data-idx={i}
                    style={{ minHeight: ROW_MIN_HEIGHT }}
                    className="flex items-center"
                  >
                    <ScrollReveal className="w-full">
                      <div
                        className="rounded-xl p-5 transition-all duration-500 bg-terminal-surface"
                        style={{
                          border: `1px solid ${
                            isActive ? `${c.branchColor}8c` : "rgb(var(--color-terminal-border))"
                          }`,
                          boxShadow: isActive
                            ? `0 0 40px ${c.branchColor}1f`
                            : "none",
                        }}
                      >
                        <div className="flex items-start gap-3 mb-3">
                          <div
                            className="shrink-0 w-10 h-10 rounded-lg flex items-center justify-center font-mono text-base"
                            style={{
                              background: `${c.branchColor}1f`,
                              border: `1px solid ${c.branchColor}4d`,
                              color: c.branchColor,
                            }}
                          >
                            {kind.glyph}
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2 flex-wrap mb-0.5">
                              <span
                                className="px-1.5 py-0.5 rounded font-mono text-[10px] font-semibold uppercase tracking-wider"
                                style={{
                                  background: `${c.branchColor}26`,
                                  color: c.branchColor,
                                }}
                              >
                                {kind.label}
                              </span>
                              <span className="font-mono text-[10px] text-text-faint">
                                {c.hash}
                              </span>
                            </div>
                            <h3 className="font-semibold text-base leading-snug text-text-primary font-sans">
                              {c.title}
                            </h3>
                            <div className="text-xs mt-0.5 text-text-muted font-sans">
                              <span className="text-git-blue">@ {c.org}</span>
                            </div>
                          </div>
                          <div
                            className="shrink-0 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full font-mono text-[10px] bg-terminal-bg/60 border border-terminal-border text-text-muted"
                          >
                            <Clock
                              size={10}
                              style={{ color: c.branchColor, opacity: 0.85 }}
                            />
                            {c.date}
                            {c.dateEnd && ` — ${c.dateEnd}`}
                          </div>
                        </div>

                        <ul className="space-y-1.5" style={{ paddingLeft: 52 }}>
                          {c.description.map((line, j) => (
                            <li
                              key={j}
                              className="flex gap-2 text-[13px] leading-relaxed text-text-secondary font-sans"
                            >
                              <span
                                className="shrink-0 mt-1.5 w-1 h-1 rounded-full"
                                style={{ background: c.branchColor }}
                              />
                              <span>{line}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    </ScrollReveal>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
