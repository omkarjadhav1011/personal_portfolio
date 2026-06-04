/**
 * Mirrors the backend {@code ProjectDto}. {@code id} is the UUID as a string;
 * {@code order} is the entity's sortOrder; timestamps are ISO-8601 strings.
 */
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
  liveUrl: string | null;
  repoUrl: string | null;
  status: string;
  pinned: boolean;
  longDescription: string | null;
  order: number;
  createdAt: string;
  updatedAt: string;
}
