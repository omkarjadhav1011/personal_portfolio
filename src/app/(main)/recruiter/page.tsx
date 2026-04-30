import type { Metadata } from "next";
import { prisma } from "@/lib/prisma";
import { profile as staticProfile } from "@/data/profile";
import type { Project, SocialLink } from "@/types";
import { RecruiterClient } from "./RecruiterClient";

export const revalidate = 60;

export const metadata: Metadata = {
  title: "Recruiter Mode",
  description:
    "Paste a job description and get a tailored fit report — matched projects, relevant skills, and a personal pitch.",
};

function deriveHandle(socials: SocialLink[], fallback: string): string {
  const githubUrl =
    socials.find((s) => s.icon === "github")?.url ?? "https://github.com";
  const m = /github\.com\/([^/]+)/i.exec(githubUrl);
  return m?.[1] ?? fallback;
}

async function getRecruiterPageData() {
  try {
    const [rawProjects, rawProfile] = await Promise.all([
      prisma.project.findMany({
        orderBy: [{ pinned: "desc" }, { order: "asc" }],
      }),
      prisma.profile.findUnique({ where: { id: "main" } }),
    ]);

    const projects: Project[] = rawProjects.map((p) => ({
      ...p,
      tags: JSON.parse(p.tags) as string[],
      liveUrl: p.liveUrl ?? undefined,
      repoUrl: p.repoUrl ?? undefined,
      longDescription: p.longDescription ?? undefined,
      status: p.status as "active" | "archived" | "wip",
    }));

    if (rawProfile) {
      const socials = JSON.parse(rawProfile.socials) as SocialLink[];
      return {
        projects,
        profileName: rawProfile.name,
        handle: deriveHandle(socials, rawProfile.handle),
      };
    }

    return {
      projects,
      profileName: staticProfile.name,
      handle: deriveHandle(staticProfile.socials, staticProfile.handle),
    };
  } catch {
    const { projects: staticProjects } = await import("@/data/projects");
    return {
      projects: staticProjects,
      profileName: staticProfile.name,
      handle: deriveHandle(staticProfile.socials, staticProfile.handle),
    };
  }
}

export default async function RecruiterPage() {
  const { projects, profileName, handle } = await getRecruiterPageData();

  return (
    <section className="pt-20 pb-16 sm:pt-24 px-4 scroll-mt-14">
      <div className="max-w-4xl mx-auto">
        <div className="mb-6 sm:mb-8">
          <div className="flex items-center gap-3 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">$</span>
            <span>git rev-parse --recruiter</span>
          </div>
          <h1 className="font-mono font-bold text-3xl sm:text-4xl md:text-5xl mb-2 text-text-primary">
            Recruiter Mode
          </h1>
          <p className="text-text-muted text-sm sm:text-base font-sans max-w-2xl">
            Paste the job description below. {profileName} will be matched
            against it — relevant projects, overlapping skills, honest gaps, and
            a short pitch tailored to the role.
          </p>
        </div>

        <RecruiterClient projects={projects} handle={handle} />
      </div>
    </section>
  );
}
