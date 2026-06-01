import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { HeroSection } from "@/components/sections/HeroSection";
import { AboutSection } from "@/components/sections/AboutSection";
import { SkillsSection } from "@/components/sections/SkillsSection";
import { SkillsDiffSection } from "@/components/sections/SkillsDiffSection";
import { ProjectsSection } from "@/components/sections/ProjectsSection";
import { ExperienceSection } from "@/components/sections/ExperienceSection";
import { ContactSection } from "@/components/sections/ContactSection";
import {
  HeroSkeleton,
  HeatmapSkeleton,
  AboutSkeleton,
  SkillsSkeleton,
  ProjectsSkeleton,
  ExperienceSkeleton,
  ContactSkeleton,
} from "@/components/ui/Skeleton";
import { useProfile } from "@/api/profile";
import { useDomainProjects } from "@/api/projects";
import { useSkillBranches, useSkillDiff } from "@/api/skills";
import { useExperience } from "@/api/experience";

/**
 * Home page (replaces (main)/page.tsx). Each data slice is fetched from the
 * backend via useQuery, mapped to the domain types, and passed to the sections.
 */
export default function Home() {
  useDocumentTitle();
  const profileQ = useProfile();
  const projectsQ = useDomainProjects();
  const branchesQ = useSkillBranches();
  const diffQ = useSkillDiff();
  const timelineQ = useExperience();

  const queries = [profileQ, projectsQ, branchesQ, diffQ, timelineQ];

  if (queries.some((q) => q.isPending)) {
    return (
      <>
        <HeroSkeleton />
        <HeatmapSkeleton />
        <AboutSkeleton />
        <SkillsSkeleton />
        <ProjectsSkeleton />
        <ExperienceSkeleton />
        <ContactSkeleton />
      </>
    );
  }

  if (
    !profileQ.data ||
    !projectsQ.data ||
    !branchesQ.data ||
    !diffQ.data ||
    !timelineQ.data
  ) {
    return (
      <div className="min-h-screen flex items-center justify-center px-4 bg-terminal-bg font-mono">
        <p className="text-git-red">
          fatal: failed to load portfolio data from the backend.
        </p>
      </div>
    );
  }

  const profile = profileQ.data;
  const projects = projectsQ.data;
  const skillBranches = branchesQ.data;
  const skillsDiff = diffQ.data;
  const timeline = timelineQ.data;

  const topSkills = skillBranches
    .flatMap((b) => b.skills)
    .filter((s) => s.level >= 4)
    .slice(0, 8);
  const githubUrl =
    profile.socials.find((s) => s.icon === "github")?.url ?? "https://github.com";

  return (
    <>
      <HeroSection profile={profile} />
      <AboutSection profile={profile} topSkills={topSkills} />
      <SkillsSection skillBranches={skillBranches} />
      <SkillsDiffSection skillsDiff={skillsDiff} />
      <ProjectsSection projects={projects} githubUrl={githubUrl} />
      <ExperienceSection timeline={timeline} />
      <ContactSection />
    </>
  );
}
