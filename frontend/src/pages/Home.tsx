import { useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { ArrowRight } from "lucide-react";
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
 * When another route navigates here with { state: { scrollTo: id } } (e.g. the
 * Navbar from a project detail page), smooth-scroll to that section. Sections
 * mount only after their data loads, so we retry briefly until the node exists.
 */
function useScrollToSection() {
  const location = useLocation();
  useEffect(() => {
    const target = (location.state as { scrollTo?: string } | null)?.scrollTo;
    if (!target) return;
    let cancelled = false;
    let attempts = 0;
    const tick = () => {
      if (cancelled) return;
      const el = document.getElementById(target);
      if (el) {
        el.scrollIntoView({ behavior: "smooth" });
      } else if (attempts++ < 40) {
        // ~2s budget covers the data-loading skeleton → real sections swap.
        setTimeout(tick, 50);
      }
    };
    tick();
    return () => {
      cancelled = true;
    };
  }, [location.key, location.state]);
}

/**
 * Home page (replaces (main)/page.tsx). Each data slice is fetched from the
 * backend via useQuery, mapped to the domain types, and passed to the sections.
 */
export default function Home() {
  useDocumentTitle();
  useScrollToSection();
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

  // The homepage is a hub, not the whole site. Topics that now own a dedicated
  // page are summarised here and linked out; showing them in full would mean the
  // homepage and the dedicated page compete for the same query, and Google would
  // pick one and waste the other.
  //
  // Skills and contact are NOT summarised: they have no separate page, so this
  // is where they live in full.
  const featuredProjects = projects.filter((p) => p.pinned).slice(0, 4);
  const currentRoles = timeline.filter((entry) => entry.type === "job").slice(0, 1);

  return (
    <>
      <HeroSection profile={profile} />
      <AboutSection profile={profile} topSkills={topSkills} />
      <MoreLink to="/about" label="Read the full background" />

      <SkillsSection skillBranches={skillBranches} />
      <SkillsDiffSection skillsDiff={skillsDiff} />

      <ProjectsSection
        projects={featuredProjects.length > 0 ? featuredProjects : projects.slice(0, 4)}
        githubUrl={githubUrl}
      />
      <MoreLink to="/projects" label={`View all ${projects.length} projects`} />

      <ExperienceSection timeline={currentRoles.length > 0 ? currentRoles : timeline.slice(0, 1)} />
      <MoreLink to="/experience" label="Full experience and certifications" />

      <ContactSection />
    </>
  );
}

/**
 * The link from a homepage summary to the page that owns that topic in full.
 * A real <a>, with descriptive anchor text rather than "read more" — the anchor
 * text is one of the few places a crawler learns what the destination is about.
 */
function MoreLink({ to, label }: { to: string; label: string }) {
  return (
    <div className="px-4 pb-8 -mt-6 sm:-mt-10">
      <div className="max-w-5xl mx-auto text-center sm:text-left">
        <Link
          to={to}
          className="inline-flex items-center gap-2 font-mono text-sm text-text-muted transition-colors hover:text-git-green"
        >
          <span className="text-git-green">$</span>
          {label}
          <ArrowRight size={13} aria-hidden="true" />
        </Link>
      </div>
    </div>
  );
}
