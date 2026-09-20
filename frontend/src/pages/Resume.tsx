import { Link } from "react-router-dom";
import { Download } from "lucide-react";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PageShell } from "@/components/layout/PageShell";
import { useProfile } from "@/api/profile";
import { useExperience } from "@/api/experience";
import { useSkillBranches } from "@/api/skills";
import { useDomainProjects } from "@/api/projects";
import { assetUrl } from "@/lib/api";

/**
 * The resume as a readable web page.
 *
 * A PDF is a poor ranking asset and a worse citation asset: it is awkward to
 * crawl, impossible to link into, and carries no schema. An HTML resume is all
 * three. The PDF stays available as a download for anyone who wants to keep a
 * copy, but the page is the source of truth.
 *
 * Everything here is read from the same API that renders the rest of the site,
 * so the resume cannot drift from the portfolio the way the previous PDF did —
 * that file still described a final-year student seeking a role, using
 * technologies he has withdrawn.
 */
export default function Resume() {
  useDocumentTitle("Resume");
  const { data: profile } = useProfile();
  const { data: timeline } = useExperience();
  const { data: branches } = useSkillBranches();
  const { data: projects } = useDomainProjects();

  const jobs = (timeline ?? []).filter((e) => e.type === "job");
  const education = (timeline ?? []).filter((e) => e.type === "education");
  const certifications = (timeline ?? []).filter((e) => e.type === "achievement");
  const pdfHref = profile?.resumeUrl ? assetUrl(profile.resumeUrl) : undefined;

  return (
    <PageShell
      command="git export --resume"
      title={
        <>
          <span className="text-git-green">Omkar Jadhav</span> — Resume
        </>
      }
      intro={
        <>
          The full resume of Omkar Jayvant Jadhav, Software Development Engineer I at Nonstop IO
          Technologies in Pune. Readable here in full; a PDF copy is available to download.
        </>
      }
    >
      {pdfHref && (
        <a
          href={pdfHref}
          download={profile?.resumeFilename ?? "Omkar_Jadhav_Resume.pdf"}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 rounded-lg border border-git-purple/40 bg-git-purple/10 px-4 py-2 font-mono text-sm text-git-purple transition-colors hover:bg-git-purple/20 hover:border-git-purple/70"
        >
          <Download size={14} aria-hidden="true" />
          Download PDF
        </a>
      )}

      <section aria-labelledby="summary" className="mt-10">
        <h2 id="summary" className="font-mono text-lg font-bold text-text-primary mb-3">
          Summary
        </h2>
        <p className="text-sm sm:text-base text-text-muted leading-relaxed">
          Omkar Jayvant Jadhav is a Software Development Engineer I at Nonstop IO Technologies in
          Kharadi, Pune. He works on the backend of an enterprise reporting product in C#, NestJS
          and SQL, contributing to live production modules, and holds a B.Tech in Computer Science
          &amp; Engineering (Data Science) from KIT&apos;s College of Engineering (Autonomous),
          Kolhapur.
        </p>
      </section>

      <section aria-labelledby="resume-contact" className="mt-10">
        <h2 id="resume-contact" className="font-mono text-lg font-bold text-text-primary mb-3">
          Contact
        </h2>
        <ul className="space-y-1.5 font-mono text-sm text-text-muted">
          {profile?.email && (
            <li>
              Email:{" "}
              <a
                href={`mailto:${profile.email}`}
                className="text-git-green hover:underline break-all"
              >
                {profile.email}
              </a>
            </li>
          )}
          {profile?.location && <li>Location: {profile.location}</li>}
          {profile?.socials.map((social) => (
            <li key={social.url}>
              {social.label}:{" "}
              <a
                href={social.url}
                target="_blank"
                rel="noopener noreferrer"
                className="text-git-green hover:underline break-all"
              >
                {social.url}
              </a>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="resume-experience" className="mt-10">
        <h2 id="resume-experience" className="font-mono text-lg font-bold text-text-primary mb-4">
          Experience
        </h2>
        <div className="space-y-6">
          {jobs.map((job) => (
            <article key={job.hash}>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h3 className="font-mono text-sm font-bold text-text-primary">
                  {job.title} — {job.org}
                </h3>
                <span className="font-mono text-xs text-text-faint">
                  {job.date}
                  {job.dateEnd ? ` — ${job.dateEnd}` : " — Present"}
                </span>
              </div>
              <ul className="mt-2 space-y-1.5 text-sm text-text-muted leading-relaxed">
                {job.description.map((line) => (
                  <li key={line} className="flex gap-2">
                    <span className="text-text-faint shrink-0" aria-hidden="true">
                      —
                    </span>
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </section>

      <section aria-labelledby="resume-skills" className="mt-10">
        <h2 id="resume-skills" className="font-mono text-lg font-bold text-text-primary mb-4">
          Technical skills
        </h2>
        <dl className="space-y-2 text-sm">
          {(branches ?? []).map((branch) => (
            <div key={branch.branchName} className="flex flex-wrap gap-2">
              <dt className="font-mono text-text-faint">
                {branch.branchName.replace("feature/", "")}:
              </dt>
              <dd className="text-text-muted">{branch.skills.map((s) => s.name).join(", ")}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section aria-labelledby="resume-projects" className="mt-10">
        <h2 id="resume-projects" className="font-mono text-lg font-bold text-text-primary mb-4">
          Projects
        </h2>
        <div className="space-y-4">
          {(projects ?? []).map((project) => (
            <article key={project.id}>
              <h3 className="font-mono text-sm font-bold text-text-primary">
                <Link to={`/projects/${project.slug}`} className="hover:text-git-green">
                  {project.repoName}
                </Link>
                {project.tags.length > 0 && (
                  <span className="ml-2 font-normal text-text-faint">
                    ({project.tags.join(", ")})
                  </span>
                )}
              </h3>
              <p className="mt-1 text-sm text-text-muted leading-relaxed">{project.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section aria-labelledby="resume-education" className="mt-10">
        <h2 id="resume-education" className="font-mono text-lg font-bold text-text-primary mb-4">
          Education
        </h2>
        <div className="space-y-4">
          {education.map((entry) => (
            <article key={entry.hash}>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h3 className="font-mono text-sm font-bold text-text-primary">{entry.title}</h3>
                <span className="font-mono text-xs text-text-faint">
                  {entry.date}
                  {entry.dateEnd ? ` — ${entry.dateEnd}` : ""}
                </span>
              </div>
              <p className="mt-0.5 text-sm text-text-muted">{entry.org}</p>
              {entry.description.length > 0 && (
                <p className="mt-1 text-sm text-text-muted">{entry.description.join(" · ")}</p>
              )}
            </article>
          ))}
        </div>
      </section>

      {certifications.length > 0 && (
        <section aria-labelledby="resume-certs" className="mt-10">
          <h2 id="resume-certs" className="font-mono text-lg font-bold text-text-primary mb-3">
            Certifications
          </h2>
          <ul className="space-y-1.5 text-sm text-text-muted">
            {certifications.map((cert) => (
              <li key={cert.hash}>
                {cert.title} — {cert.org} ({cert.date})
              </li>
            ))}
          </ul>
        </section>
      )}
    </PageShell>
  );
}
