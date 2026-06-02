import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { Profile, SocialLink, CurrentRole, TechPick } from "@/types";

interface CurrentRoleDto {
  enabled: boolean;
  title: string;
  company: string;
  monogram: string | null;
  logoUrl: string | null;
  url: string | null;
  location: string | null;
  startedAt: string;
  tenure: string | null;
  accent: string | null;
}

interface ProfileDto {
  id: string;
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
  stash: string[];
  currentRole: CurrentRoleDto | null;
  avatarUrl: string | null;
  techPicks: TechPick[] | null;
  updatedAt: string;
}

export const profileKeys = { detail: ["profile"] as const };

/** Maps the API DTO to the domain Profile the section components consume. */
function toDomainProfile(p: ProfileDto): Profile {
  const currentRole: CurrentRole | undefined =
    p.currentRole && p.currentRole.enabled
      ? {
          enabled: true,
          title: p.currentRole.title,
          company: p.currentRole.company,
          monogram: p.currentRole.monogram ?? undefined,
          logoUrl: p.currentRole.logoUrl ?? undefined,
          url: p.currentRole.url ?? undefined,
          location: p.currentRole.location ?? undefined,
          startedAt: p.currentRole.startedAt,
          tenure: p.currentRole.tenure ?? undefined,
          accent: p.currentRole.accent ?? undefined,
        }
      : undefined;

  return {
    name: p.name,
    handle: p.handle,
    headline: p.headline,
    bio: p.bio,
    currentBranch: p.currentBranch,
    currentStatus: p.currentStatus,
    availableForWork: p.availableForWork,
    email: p.email,
    location: p.location,
    socials: p.socials,
    funFacts: p.funFacts,
    stash: p.stash,
    currentRole,
    avatarUrl: p.avatarUrl ?? undefined,
    techPicks: p.techPicks ?? undefined,
  };
}

export function useProfile() {
  return useQuery({
    queryKey: profileKeys.detail,
    queryFn: () => apiFetch<ProfileDto>("/api/profile"),
    select: toDomainProfile,
  });
}
