/** Fit-score tone mapping shared by the hero score and report export. */
export interface ScoreTone {
  key: "green" | "blue" | "yellow" | "red";
  label: string;
  /** Text color class for the big number. */
  text: string;
  /** Solid fill class for the score bar. */
  bg: string;
  /** Full pill classes (same look as the original corner badge). */
  chip: string;
}

// Class strings stay complete literals — Tailwind's JIT can't see interpolated names.
export function scoreTone(score: number): ScoreTone {
  if (score >= 75) {
    return {
      key: "green",
      label: "strong fit",
      text: "text-git-green",
      bg: "bg-git-green",
      chip: "bg-git-green/10 border-git-green/40 text-git-green",
    };
  }
  if (score >= 50) {
    return {
      key: "blue",
      label: "partial fit",
      text: "text-git-blue",
      bg: "bg-git-blue",
      chip: "bg-git-blue/10 border-git-blue/40 text-git-blue",
    };
  }
  if (score >= 25) {
    return {
      key: "yellow",
      label: "stretch fit",
      text: "text-git-yellow",
      bg: "bg-git-yellow",
      chip: "bg-git-yellow/10 border-git-yellow/40 text-git-yellow",
    };
  }
  return {
    key: "red",
    label: "low fit",
    text: "text-git-red",
    bg: "bg-git-red",
    chip: "bg-git-red/10 border-git-red/40 text-git-red",
  };
}

/** Hero font size grows with the score: 3rem at 0 → 6.5rem at 100. */
export function scoreFontSizeRem(score: number): number {
  const clamped = Math.min(Math.max(score, 0), 100);
  return 3 + (clamped / 100) * 3.5;
}
