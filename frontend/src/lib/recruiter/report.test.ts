import { describe, it, expect } from "vitest";
import { buildReportMarkdown } from "./report";
import type { MatchResult } from "./types";
import type { Project } from "@/types";

const match: MatchResult = {
  fitScore: 78,
  matchedProjects: [
    { slug: "portfolio", reason: "Full-stack build", relevantTags: ["react", "spring"] },
  ],
  matchedSkills: [{ name: "React", reason: "3 years in production" }],
  gapSkills: [
    { name: "Kubernetes", importance: "must-have" },
    { name: "GraphQL", importance: "nice-to-have" },
  ],
};

const projectsBySlug = new Map<string, Project>([
  [
    "portfolio",
    {
      id: "1",
      slug: "portfolio",
      repoName: "personal-portfolio",
      description: "",
      language: "TypeScript",
      languageColor: "#3178c6",
      stars: 0,
      forks: 0,
      commits: 0,
      lastCommit: "",
      lastCommitMsg: "",
      tags: [],
      status: "active",
      pinned: true,
    },
  ],
]);

describe("buildReportMarkdown", () => {
  it("includes score, tone label, projects, skills, and gaps", () => {
    const md = buildReportMarkdown(match, projectsBySlug, "Omkar Jadhav");
    expect(md).toContain("# Fit report — Omkar Jadhav");
    expect(md).toContain("**Fit score: 78/100** (strong fit)");
    expect(md).toContain("**personal-portfolio** — Full-stack build _(react, spring)_");
    expect(md).toContain("**React** — 3 years in production");
    expect(md).toContain("- Kubernetes _(must-have)_");
    expect(md).toContain("- GraphQL _(nice-to-have)_");
  });

  it("falls back to the slug for unknown projects and omits empty sections", () => {
    const md = buildReportMarkdown(
      { ...match, matchedProjects: [{ slug: "ghost", reason: "r", relevantTags: [] }], matchedSkills: [], gapSkills: [] },
      new Map(),
      "Omkar"
    );
    expect(md).toContain("**ghost** — r");
    expect(md).not.toContain("## Matched skills");
    expect(md).not.toContain("## Gaps");
  });
});
