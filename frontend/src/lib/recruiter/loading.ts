import type { Profile, SkillBranch } from "@/types";

/** Inputs for the git-themed analysis step ticker. Counts are optional —
 *  when the data isn't cached yet the labels degrade gracefully. */
export interface StepCounts {
  skillCount?: number;
  branchCount?: number;
  projectCount?: number;
}

/** Ordered ticker lines. The last step is meant to hold (blinking cursor)
 *  until the request resolves — it never claims completion. */
export function buildSteps({
  skillCount,
  branchCount,
  projectCount,
}: StepCounts): string[] {
  const scanning =
    skillCount && branchCount
      ? `scanning ${skillCount} skills across ${branchCount} branches`
      : "scanning skills…";
  const diffing = projectCount
    ? `diffing against ${projectCount} project${projectCount === 1 ? "" : "s"}…`
    : "diffing against projects…";
  return [
    "git fetch origin/job-description",
    "parsing requirements…",
    scanning,
    diffing,
    "computing fit score…",
  ];
}

/** "Meanwhile, about {owner}" facts shown while the analysis runs. */
export function buildFactPool(
  profile: Profile | undefined,
  branches: SkillBranch[] | undefined
): string[] {
  const facts: string[] = [];

  if (profile?.currentRole) {
    facts.push(
      `currently ${profile.currentRole.title} @ ${profile.currentRole.company}`
    );
  }
  if (profile?.headline) facts.push(profile.headline);

  const topSkills = (branches ?? [])
    .flatMap((b) => b.skills)
    .filter((s) => s.level >= 4)
    .map((s) => s.name);
  for (let i = 0; i < topSkills.length; i += 4) {
    facts.push(`ships ${topSkills.slice(i, i + 4).join(" · ")}`);
  }

  const picks = (profile?.techPicks ?? []).map((t) => t.name);
  if (picks.length > 0) facts.push(`current toolbox: ${picks.join(" · ")}`);

  for (const fact of profile?.funFacts ?? []) {
    facts.push(`# fun fact: ${fact}`);
  }

  return facts;
}

/** Fisher–Yates copy-shuffle (rng injectable for deterministic tests). */
export function shuffle<T>(items: T[], rng: () => number = Math.random): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}
