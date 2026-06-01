import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { Project } from "@/types/project";

export const projectKeys = {
  all: ["projects"] as const,
};

/** GET /api/projects — all projects, pinned first then by sort order. */
export function fetchProjects(): Promise<Project[]> {
  return apiFetch<Project[]>("/api/projects");
}

export function useProjects() {
  return useQuery({
    queryKey: projectKeys.all,
    queryFn: fetchProjects,
  });
}
