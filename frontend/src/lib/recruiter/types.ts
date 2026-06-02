import { z } from "zod";

export const matchedProjectSchema = z.object({
  slug: z.string(),
  reason: z.string(),
  relevantTags: z.array(z.string()).default([]),
});

export const matchedSkillSchema = z.object({
  name: z.string(),
  reason: z.string(),
});

export const gapSkillSchema = z.object({
  name: z.string(),
  importance: z.enum(["must-have", "nice-to-have"]),
});

export const matchResultSchema = z.object({
  fitScore: z.number().min(0).max(100),
  matchedProjects: z.array(matchedProjectSchema).max(5),
  matchedSkills: z.array(matchedSkillSchema).max(12),
  gapSkills: z.array(gapSkillSchema).max(8),
});

export type MatchedProject = z.infer<typeof matchedProjectSchema>;
export type MatchedSkill = z.infer<typeof matchedSkillSchema>;
export type GapSkill = z.infer<typeof gapSkillSchema>;
export type MatchResult = z.infer<typeof matchResultSchema>;

export const JD_MIN = 80;
export const JD_MAX = 8000;
