// ─── Project / Repository ────────────────────────────────────────────────────

export interface Project {
  id: string;
  slug: string;
  repoName: string;
  description: string;
  language: string;
  languageColor: string;
  stars: number;
  forks: number;
  commits: number;
  lastCommit: string;
  lastCommitMsg: string;
  tags: string[];
  liveUrl?: string;
  repoUrl?: string;
  status: "active" | "archived" | "wip";
  pinned: boolean;
  longDescription?: string;
}

// ─── Skills / Branches ───────────────────────────────────────────────────────

export interface Skill {
  name: string;
  level: 1 | 2 | 3 | 4 | 5; // 5 = expert
  tag?: string;
  icon?: string;
}

export interface SkillBranch {
  branchName: string;
  color: string;
  offset: number;
  skills: Skill[];
}

// ─── Experience / Commit Log ─────────────────────────────────────────────────

export type EntryType = "job" | "education" | "achievement" | "project";

export interface CommitEntry {
  hash: string;
  type: EntryType;
  title: string;
  org: string;
  date: string;
  dateEnd?: string;
  description: string[];
  branch: string;
  branchColor: string;
  colorKey?: "green" | "blue" | "yellow" | "orange";
  tags?: string[];
  url?: string;
}

// ─── Profile ─────────────────────────────────────────────────────────────────

export interface SocialLink {
  label: string;
  url: string;
  icon: string;
}

export interface CurrentRole {
  enabled: boolean;
  title: string;
  company: string;
  monogram?: string;
  logoUrl?: string;
  url?: string;
  location?: string;
  startedAt: string;
  tenure?: string;
  accent?: string; // hex color
}

export interface Profile {
  name: string;
  handle: string;
  headline: string;
  bio: string;
  currentBranch: string;
  currentStatus: string;
  availableForWork: boolean;
  email: string;
  location: string;
  socials: SocialLink[];
  funFacts: string[];
  stash?: string[];
  currentRole?: CurrentRole;
}

// ─── Skills Diff ─────────────────────────────────────────────────────────────

export interface SkillDiff {
  name: string;
  type: "added" | "deprecated" | "modified";
  note?: string;
}

// ─── Terminal ────────────────────────────────────────────────────────────────

export interface TerminalLine {
  type: "command" | "output" | "error" | "success" | "comment";
  text: string;
  delay?: number;
}

export interface CommandResult {
  output: string[];
  type: "success" | "error";
}

// ─── Contact Form ────────────────────────────────────────────────────────────

export interface ContactFormData {
  name: string;
  email: string;
  message: string;
  honeypot?: string;
}

export type ContactFormState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; message: string }
  | { status: "error"; message: string };
