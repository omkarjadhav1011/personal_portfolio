import { useDomainProjects } from "@/api/projects";
import { useProfile } from "@/api/profile";
import { profile as staticProfile } from "@/data/profile";
import { RecruiterClient } from "@/components/recruiter/RecruiterClient";
import type { SocialLink } from "@/types";

/** Derives the GitHub handle from the profile's social links. */
function deriveHandle(socials: SocialLink[], fallback: string): string {
  const githubUrl = socials.find((s) => s.icon === "github")?.url ?? "https://github.com";
  const m = /github\.com\/([^/]+)/i.exec(githubUrl);
  return m?.[1] ?? fallback;
}

/** Recruiter Mode page (replaces (main)/recruiter/page.tsx). */
export default function RecruiterPage() {
  const projects = useDomainProjects().data ?? [];
  const profile = useProfile().data ?? staticProfile;
  const handle = deriveHandle(profile.socials, profile.handle);

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
            Paste the job description below. {profile.name} will be matched against it —
            relevant projects, overlapping skills, honest gaps, and a short pitch tailored
            to the role.
          </p>
        </div>

        <RecruiterClient projects={projects} handle={handle} />
      </div>
    </section>
  );
}
