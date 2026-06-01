import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { SkillBranch, Skill, SkillDiff } from "@/types";

interface SkillDto {
  id: string;
  name: string;
  level: number;
  tag: string | null;
  icon: string | null;
  branchId: string;
  createdAt: string;
  updatedAt: string;
}

interface SkillBranchDto {
  id: string;
  branchName: string;
  color: string;
  offset: number;
  skills: SkillDto[];
  createdAt: string;
  updatedAt: string;
}

interface SkillDiffDto {
  id: string;
  name: string;
  type: string;
  note: string | null;
  order: number;
  createdAt: string;
  updatedAt: string;
}

export const skillKeys = {
  branches: ["skill-branches"] as const,
  diff: ["skill-diff"] as const,
};

function toDomainSkill(s: SkillDto): Skill {
  return {
    name: s.name,
    level: s.level as Skill["level"],
    tag: s.tag ?? undefined,
    icon: s.icon ?? undefined,
  };
}

function toDomainBranch(b: SkillBranchDto): SkillBranch {
  return {
    branchName: b.branchName,
    color: b.color,
    offset: b.offset,
    skills: b.skills.map(toDomainSkill),
  };
}

function toDomainDiff(d: SkillDiffDto): SkillDiff {
  return {
    name: d.name,
    type: d.type as SkillDiff["type"],
    note: d.note ?? undefined,
  };
}

export function useSkillBranches() {
  return useQuery({
    queryKey: skillKeys.branches,
    queryFn: () => apiFetch<SkillBranchDto[]>("/api/skills/branches"),
    select: (data) => data.map(toDomainBranch),
  });
}

export function useSkillDiff() {
  return useQuery({
    queryKey: skillKeys.diff,
    queryFn: () => apiFetch<SkillDiffDto[]>("/api/skills/diff"),
    select: (data) => data.map(toDomainDiff),
  });
}
