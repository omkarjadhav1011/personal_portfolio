import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { Project as ProjectDto } from "@/types/project";
import type { Project } from "@/types";

export const projectKeys = {
  all: ["projects"] as const,
};

/** GET /api/projects — all projects, pinned first then by sort order (raw DTO). */
export function fetchProjects(): Promise<ProjectDto[]> {
  return apiFetch<ProjectDto[]>("/api/projects");
}

/** Maps the API DTO to the domain Project the section components consume. */
function toDomainProject(p: ProjectDto): Project {
  return {
    id: p.id,
    slug: p.slug,
    repoName: p.repoName,
    description: p.description,
    language: p.language,
    languageColor: p.languageColor,
    stars: p.stars,
    forks: p.forks,
    commits: p.commits,
    lastCommit: p.lastCommit,
    lastCommitMsg: p.lastCommitMsg,
    tags: p.tags,
    liveUrl: p.liveUrl ?? undefined,
    repoUrl: p.repoUrl ?? undefined,
    status: p.status as Project["status"],
    pinned: p.pinned,
    longDescription: p.longDescription ?? undefined,
  };
}

/** Raw DTO list (used by the scratch page). */
export function useProjects() {
  return useQuery({
    queryKey: projectKeys.all,
    queryFn: fetchProjects,
  });
}

/** Domain projects for the home page sections. */
export function useDomainProjects() {
  return useQuery({
    queryKey: projectKeys.all,
    queryFn: fetchProjects,
    select: (data) => data.map(toDomainProject),
  });
}

/**
 * Single project by slug. Reuses the cached projects list and selects the match
 * (instant when arriving from the home page; fetches the list on a deep link).
 * Resolves to `undefined` when no project has that slug.
 */
export function useProject(slug: string | undefined) {
  return useQuery({
    queryKey: projectKeys.all,
    queryFn: fetchProjects,
    enabled: !!slug,
    select: (data) => data.map(toDomainProject).find((p) => p.slug === slug),
  });
}
