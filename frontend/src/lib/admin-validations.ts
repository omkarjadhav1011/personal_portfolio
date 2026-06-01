import { z } from "zod";

export const projectSchema = z.object({
  slug: z.string().min(1).regex(/^[a-z0-9-]+$/, "Use lowercase letters, numbers, and hyphens"),
  repoName: z.string().min(1),
  description: z.string().min(1),
  language: z.string().min(1),
  languageColor: z.string().regex(/^#[0-9a-fA-F]{6}$/, "Must be a hex color like #3178c6"),
  stars: z.coerce.number().int().min(0).default(0),
  forks: z.coerce.number().int().min(0).default(0),
  commits: z.coerce.number().int().min(0).default(0),
  lastCommit: z.string().min(1),
  lastCommitMsg: z.string().min(1),
  tags: z.array(z.string()).default([]),
  liveUrl: z.string().url().optional().or(z.literal("")),
  repoUrl: z.string().url().optional().or(z.literal("")),
  status: z.enum(["active", "archived", "wip"]).default("active"),
  pinned: z.boolean().default(false),
  longDescription: z.string().optional(),
});

export const commitEntrySchema = z.object({
  hash: z.string().min(1),
  type: z.enum(["job", "education", "achievement", "project"]),
  title: z.string().min(1),
  org: z.string().min(1),
  date: z.string().min(1),
  dateEnd: z.string().optional(),
  description: z.array(z.string()).min(1),
  branch: z.string().min(1),
  branchColor: z.string().regex(/^#[0-9a-fA-F]{6}$/),
  colorKey: z.enum(["green", "blue", "yellow", "orange"]).optional(),
  tags: z.array(z.string()).optional(),
  url: z.string().url().optional().or(z.literal("")),
});

export const skillBranchSchema = z.object({
  branchName: z.string().min(1),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/),
  offset: z.coerce.number().int().min(0).default(0),
});

export const skillSchema = z.object({
  name: z.string().min(1),
  level: z.coerce.number().int().min(1).max(5),
  tag: z.string().optional(),
  icon: z.string().optional(),
});

export const skillDiffSchema = z.object({
  name: z.string().min(1),
  type: z.enum(["added", "deprecated", "modified"]),
  note: z.string().optional(),
});

export const currentRoleSchema = z.object({
  enabled: z.boolean().default(false),
  title: z.string().default(""),
  company: z.string().default(""),
  monogram: z.string().max(3).optional().or(z.literal("")),
  logoUrl: z.string().url().optional().or(z.literal("")),
  url: z.string().url().optional().or(z.literal("")),
  location: z.string().optional().or(z.literal("")),
  startedAt: z.string().default(""),
  tenure: z.string().optional().or(z.literal("")),
  accent: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/, "Must be a hex color like #00ff88")
    .optional()
    .or(z.literal("")),
});

export const profileSchema = z.object({
  name: z.string().min(1),
  handle: z.string().min(1),
  headline: z.string().min(1),
  bio: z.string().min(1),
  currentBranch: z.string().min(1),
  currentStatus: z.string().min(1),
  availableForWork: z.boolean().default(true),
  email: z.string().email(),
  location: z.string().min(1),
  socials: z.array(z.object({ label: z.string(), url: z.string().url(), icon: z.string() })),
  funFacts: z.array(z.string()),
  stash: z.array(z.string()).optional(),
  currentRole: currentRoleSchema.optional(),
});
