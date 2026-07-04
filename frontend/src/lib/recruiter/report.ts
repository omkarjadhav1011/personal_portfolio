import type { Project } from "@/types";
import type { MatchResult } from "@/lib/recruiter/types";
import { scoreTone } from "@/lib/recruiter/score";

/** Markdown version of the full analysis, for one-click copy into notes/ATS. */
export function buildReportMarkdown(
  match: MatchResult,
  projectsBySlug: Map<string, Project>,
  ownerName: string
): string {
  const score = Math.round(match.fitScore);
  const lines: string[] = [
    `# Fit report — ${ownerName}`,
    "",
    `**Fit score: ${score}/100** (${scoreTone(score).label})`,
  ];

  if (match.matchedProjects.length > 0) {
    lines.push("", "## Matched projects");
    for (const p of match.matchedProjects) {
      const project = projectsBySlug.get(p.slug);
      const name = project?.repoName ?? p.slug;
      const tags = p.relevantTags.length > 0 ? ` _(${p.relevantTags.join(", ")})_` : "";
      lines.push(`- **${name}** — ${p.reason}${tags}`);
    }
  }

  if (match.matchedSkills.length > 0) {
    lines.push("", "## Matched skills");
    for (const s of match.matchedSkills) {
      lines.push(`- **${s.name}** — ${s.reason}`);
    }
  }

  if (match.gapSkills.length > 0) {
    lines.push("", "## Gaps");
    for (const g of match.gapSkills) {
      lines.push(`- ${g.name} _(${g.importance})_`);
    }
  }

  return lines.join("\n");
}
