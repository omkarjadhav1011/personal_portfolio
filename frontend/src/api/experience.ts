import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { CommitEntry } from "@/types";

interface ExperienceDto {
  id: string;
  hash: string;
  type: string;
  title: string;
  org: string;
  date: string;
  dateEnd: string | null;
  description: string[];
  branch: string;
  branchColor: string;
  colorKey: string | null;
  tags?: string[];
  url: string | null;
  order: number;
  createdAt: string;
  updatedAt: string;
}

export const experienceKeys = { all: ["experience"] as const };

function toDomainEntry(e: ExperienceDto): CommitEntry {
  return {
    hash: e.hash,
    type: e.type as CommitEntry["type"],
    title: e.title,
    org: e.org,
    date: e.date,
    dateEnd: e.dateEnd ?? undefined,
    description: e.description,
    branch: e.branch,
    branchColor: e.branchColor,
    colorKey: (e.colorKey ?? undefined) as CommitEntry["colorKey"],
    tags: e.tags ?? undefined,
    url: e.url ?? undefined,
  };
}

export function useExperience() {
  return useQuery({
    queryKey: experienceKeys.all,
    queryFn: () => apiFetch<ExperienceDto[]>("/api/experience"),
    select: (data) => data.map(toDomainEntry),
  });
}

/** Raw DTO list (with id/order) for the admin editor. Shares the cache key. */
export function useExperienceList() {
  return useQuery({
    queryKey: experienceKeys.all,
    queryFn: () => apiFetch<ExperienceDto[]>("/api/experience"),
  });
}
