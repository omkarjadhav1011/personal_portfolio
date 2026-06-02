import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Generate a fake-looking 7-char git hash */
export function gitHash(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    const char = seed.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  return Math.abs(hash).toString(16).substring(0, 7).padStart(7, "0");
}

/** Map skill level (1-5) to a human-readable label */
export function skillLevelLabel(level: number): string {
  const labels: Record<number, string> = {
    1: "Learning",
    2: "Familiar",
    3: "Proficient",
    4: "Advanced",
    5: "Expert",
  };
  return labels[level] ?? "Unknown";
}

/** Map skill level to a fill percentage for visual indicators */
export function skillLevelPercent(level: number): number {
  return (level / 5) * 100;
}

/** Clamp a number between min and max */
export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** Format a date range like "Jun 2024 — Dec 2024" or "Jun 2024 — present" */
export function formatDateRange(start: string, end?: string): string {
  return end ? `${start} — ${end}` : `${start} — present`;
}
