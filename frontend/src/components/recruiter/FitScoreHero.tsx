import { useEffect, useState } from "react";
import { animate, motion, useReducedMotion } from "framer-motion";
import { scoreFontSizeRem, scoreTone } from "@/lib/recruiter/score";

/**
 * Hero fit score: the number's size scales with the score itself
 * (a 92 reads grander than a 40), colored by the fit-tier thresholds.
 */
export function FitScoreHero({ score }: { score: number }) {
  const reduce = useReducedMotion();
  const target = Math.round(Math.min(Math.max(score, 0), 100));
  const tone = scoreTone(target);
  const [display, setDisplay] = useState(reduce ? target : 0);

  useEffect(() => {
    if (reduce) {
      setDisplay(target);
      return;
    }
    const controls = animate(0, target, {
      duration: 0.9,
      ease: "easeOut",
      onUpdate: (v) => setDisplay(Math.round(v)),
    });
    return () => controls.stop();
  }, [target, reduce]);

  return (
    <section
      aria-label={`Fit score ${target} out of 100 — ${tone.label}`}
      className="text-center space-y-4"
    >
      <div aria-hidden="true" className="space-y-4">
        <p className="font-mono text-xs text-text-faint">
          <span className="text-git-green">$</span> git merge --score
        </p>

        <p className="font-mono font-bold tabular-nums leading-none">
          {/* Size/color come from the final score — no mid-count-up jumps. */}
          <span
            className={tone.text}
            style={{
              fontSize: `clamp(2.75rem, ${scoreFontSizeRem(target)}rem, 20vw)`,
            }}
          >
            {display}
          </span>
          <span className="text-text-muted text-base sm:text-lg font-normal ml-1">
            /100
          </span>
        </p>

        <div className="max-w-md mx-auto h-1.5 rounded-full bg-terminal-border/60 overflow-hidden">
          {reduce ? (
            <div
              className={`h-full rounded-full ${tone.bg}`}
              style={{ width: `${target}%` }}
            />
          ) : (
            <motion.div
              className={`h-full rounded-full ${tone.bg}`}
              initial={{ width: "0%" }}
              animate={{ width: `${target}%` }}
              transition={{ duration: 0.9, ease: "easeOut" }}
            />
          )}
        </div>

        <span
          className={`inline-flex items-center px-3 py-1.5 rounded-lg border font-mono text-[10px] uppercase tracking-wider ${tone.chip}`}
        >
          {tone.label}
        </span>
      </div>
    </section>
  );
}
