import { describe, it, expect } from "vitest";
import { buildFactPool, buildSteps, shuffle } from "./loading";
import type { Profile, SkillBranch } from "@/types";

const profile: Profile = {
  name: "Omkar Jadhav",
  handle: "omkar",
  headline: "Full-stack engineer who ships",
  bio: "",
  currentBranch: "main",
  currentStatus: "",
  availableForWork: true,
  email: "x@example.com",
  location: "Pune",
  socials: [],
  funFacts: ["debugs with print statements", "keyboard collector"],
  currentRole: {
    enabled: true,
    title: "Software Engineer",
    company: "AcmeCorp",
    startedAt: "2024-01-01",
  },
  techPicks: [
    { name: "React", glyph: "⚛", tint: "#61dafb" },
    { name: "Spring Boot", glyph: "🌱", tint: "#6db33f" },
  ],
};

const branches: SkillBranch[] = [
  {
    branchName: "frontend",
    color: "green",
    offset: 0,
    skills: [
      { name: "React", level: 5 },
      { name: "TypeScript", level: 4 },
      { name: "jQuery", level: 2 },
    ],
  },
  {
    branchName: "backend",
    color: "blue",
    offset: 1,
    skills: [{ name: "Java", level: 4 }],
  },
];

describe("buildSteps", () => {
  it("uses real counts when available", () => {
    const steps = buildSteps({ skillCount: 4, branchCount: 2, projectCount: 12 });
    expect(steps).toContain("scanning 4 skills across 2 branches");
    expect(steps).toContain("diffing against 12 projects…");
  });

  it("degrades gracefully without counts", () => {
    const steps = buildSteps({});
    expect(steps).toContain("scanning skills…");
    expect(steps).toContain("diffing against projects…");
  });

  it("ends with the holding step", () => {
    const steps = buildSteps({});
    expect(steps[steps.length - 1]).toBe("computing fit score…");
  });
});

describe("buildFactPool", () => {
  it("collects role, headline, top skills, tech picks, and fun facts", () => {
    const facts = buildFactPool(profile, branches);
    expect(facts).toContain("currently Software Engineer @ AcmeCorp");
    expect(facts).toContain("Full-stack engineer who ships");
    expect(facts).toContain("ships React · TypeScript · Java");
    expect(facts).toContain("current toolbox: React · Spring Boot");
    expect(facts).toContain("# fun fact: debugs with print statements");
    // level-2 skill is excluded
    expect(facts.join("\n")).not.toContain("jQuery");
  });

  it("returns an empty pool for missing data", () => {
    expect(buildFactPool(undefined, undefined)).toEqual([]);
  });
});

describe("shuffle", () => {
  it("keeps all items and doesn't mutate the input", () => {
    const input = [1, 2, 3, 4, 5];
    const out = shuffle(input, () => 0.42);
    expect(out).toHaveLength(5);
    expect([...out].sort()).toEqual([1, 2, 3, 4, 5]);
    expect(input).toEqual([1, 2, 3, 4, 5]);
  });
});
