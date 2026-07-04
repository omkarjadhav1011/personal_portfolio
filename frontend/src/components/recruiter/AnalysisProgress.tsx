import { useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { FileSearch } from "lucide-react";
import { useProfile } from "@/api/profile";
import { useSkillBranches } from "@/api/skills";
import { profile as staticProfile } from "@/data/profile";
import { buildFactPool, buildSteps, shuffle } from "@/lib/recruiter/loading";

interface AnalysisProgressProps {
  ownerName: string;
  projectCount: number;
}

const STEP_INTERVAL_MS = 900;
const FACT_INTERVAL_MS = 2800;
/** Don't flash the panel for sub-300ms responses. */
const MOUNT_DELAY_MS = 300;

/**
 * Shown in place of the JD form while the match request is in flight.
 * Honest about the unknown duration: the bar decelerates toward 90% and the
 * last step holds with a blinking cursor until the parent unmounts us.
 */
export function AnalysisProgress({
  ownerName,
  projectCount,
}: AnalysisProgressProps) {
  const reduce = useReducedMotion();
  const containerRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [factIndex, setFactIndex] = useState(0);

  // Both queries are cached from the page/home visits; fall back to the
  // static profile so the panel never renders empty.
  const { data: profile } = useProfile();
  const { data: branches } = useSkillBranches();

  const steps = useMemo(
    () =>
      buildSteps({
        skillCount: branches?.flatMap((b) => b.skills).length,
        branchCount: branches?.length,
        projectCount,
      }),
    [branches, projectCount]
  );

  const facts = useMemo(
    () => shuffle(buildFactPool(profile ?? staticProfile, branches)),
    [profile, branches]
  );

  useEffect(() => {
    const id = setTimeout(() => setVisible(true), MOUNT_DELAY_MS);
    return () => clearTimeout(id);
  }, []);

  // The submit button unmounted with the form — park focus here so keyboard
  // and screen-reader position isn't lost.
  useEffect(() => {
    if (visible) containerRef.current?.focus({ preventScroll: true });
  }, [visible]);

  useEffect(() => {
    if (!visible) return;
    const id = setInterval(
      () => setStepIndex((i) => Math.min(i + 1, steps.length - 1)),
      STEP_INTERVAL_MS
    );
    return () => clearInterval(id);
  }, [visible, steps.length]);

  useEffect(() => {
    if (!visible || facts.length <= 1) return;
    const id = setInterval(
      () => setFactIndex((i) => (i + 1) % facts.length),
      FACT_INTERVAL_MS
    );
    return () => clearInterval(id);
  }, [visible, facts.length]);

  if (!visible) return null;

  const firstName = ownerName.split(" ")[0];

  return (
    <motion.div
      ref={containerRef}
      tabIndex={-1}
      role="status"
      aria-busy="true"
      aria-live="polite"
      initial={reduce ? false : { opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.2 }}
      className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden outline-none"
    >
      <span className="sr-only">
        Analyzing job description against {ownerName}&apos;s profile
      </span>

      <div className="flex items-center justify-between px-4 py-2.5 bg-terminal-bg border-b border-terminal-border font-mono text-[11px] text-text-faint">
        <span className="flex items-center gap-2">
          <FileSearch size={11} className="text-git-green/70" />
          analyzing-fit.log
        </span>
        <span>running…</span>
      </div>

      <div aria-hidden="true" className="p-5 sm:p-6 space-y-5">
        {/* Step ticker */}
        <div className="font-mono text-xs sm:text-sm space-y-1.5">
          {steps.slice(0, stepIndex + 1).map((step, i) => {
            const active = i === stepIndex;
            return (
              <div key={step} className="flex items-baseline gap-2">
                <span className="text-text-faint shrink-0">$</span>
                <span className={active ? "text-text-primary" : "text-text-muted"}>
                  {step}
                  {active ? (
                    <span
                      className={`inline-block w-1.5 h-3.5 bg-git-green/70 align-middle ml-1 ${
                        reduce ? "" : "animate-cursor-blink"
                      }`}
                    />
                  ) : (
                    <span className="text-git-green ml-2">✓</span>
                  )}
                </span>
              </div>
            );
          })}
        </div>

        {/* Asymptotic progress bar — never fakes 100% */}
        <div className="h-1 rounded-full bg-terminal-border overflow-hidden">
          {reduce ? (
            <div className="h-full w-1/2 bg-git-green rounded-full" />
          ) : (
            <motion.div
              className="h-full bg-git-green rounded-full"
              initial={{ width: "0%" }}
              animate={{ width: "90%" }}
              transition={{ duration: 8, ease: [0.1, 0.6, 0.2, 1] }}
            />
          )}
        </div>

        {/* Rotating fact */}
        {facts.length > 0 && (
          <div className="font-mono text-xs text-text-muted min-h-[2.5rem]">
            <span className="text-text-faint"># meanwhile, about {firstName}: </span>
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={factIndex}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: reduce ? 0 : 0.25 }}
                className="text-text-secondary"
              >
                {facts[factIndex]}
              </motion.span>
            </AnimatePresence>
          </div>
        )}
      </div>
    </motion.div>
  );
}
