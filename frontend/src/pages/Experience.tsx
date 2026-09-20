import { Link } from "react-router-dom";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PageShell } from "@/components/layout/PageShell";
import { useExperience } from "@/api/experience";

/**
 * Employment history.
 *
 * Targets "Omkar Jadhav Nonstop IO" — a query with effectively no competition,
 * unlike his name alone, which is contested by fifteen other software
 * engineers. Nonstop IO is itself an indexed entity with a LinkedIn company
 * page and a Glassdoor presence, so naming it consistently is one of the
 * cheapest pieces of disambiguation available.
 *
 * The employer's name is written exactly one way here. The site previously
 * spelled it three ways at once ("NonStop io Technologies", "NonstopIO",
 * "Nonstop IO Technologies"), which defeats the point of naming it at all.
 */
export default function Experience() {
  useDocumentTitle("Experience");
  const { data: timeline } = useExperience();

  const jobs = (timeline ?? []).filter((entry) => entry.type === "job");
  const certifications = (timeline ?? []).filter((entry) => entry.type === "achievement");

  return (
    <PageShell
      command="git log --author='Omkar Jadhav'"
      title={
        <>
          <span className="text-git-green">Omkar Jadhav</span> — Experience
        </>
      }
      intro={
        <>
          Omkar Jadhav works as a Software Development Engineer I at Nonstop IO Technologies in
          Kharadi, Pune. He joined in February 2026 as a Software Developer Intern and moved into
          the SDE-I role in August 2026, working on the backend of an enterprise reporting product
          in C#, NestJS and SQL.
        </>
      }
    >
      <section aria-labelledby="roles" className="space-y-6">
        <h2 id="roles" className="font-mono text-lg font-bold text-text-primary">
          Roles
        </h2>

        {jobs.map((job) => (
          <article
            key={job.hash}
            className="rounded-lg border border-terminal-border bg-terminal-surface/60 p-5"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="font-mono text-base font-bold text-text-primary">{job.title}</h3>
              <span className="font-mono text-xs text-text-faint">
                {job.date}
                {job.dateEnd ? ` — ${job.dateEnd}` : " — Present"}
              </span>
            </div>
            <p className="mt-1 font-mono text-sm text-git-green">{job.org}</p>

            {job.description.length > 0 && (
              <ul className="mt-3 space-y-2 text-sm text-text-muted leading-relaxed">
                {job.description.map((line) => (
                  <li key={line} className="flex gap-2">
                    <span className="text-text-faint shrink-0" aria-hidden="true">
                      —
                    </span>
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            )}

            {job.tags && job.tags.length > 0 && (
              <ul className="mt-4 flex flex-wrap gap-1.5">
                {job.tags.map((tag) => (
                  <li
                    key={tag}
                    className="rounded border border-terminal-border px-2 py-0.5 font-mono text-2xs text-text-muted"
                  >
                    {tag}
                  </li>
                ))}
              </ul>
            )}
          </article>
        ))}
      </section>

      {certifications.length > 0 && (
        <section aria-labelledby="certifications" className="mt-12">
          <h2 id="certifications" className="font-mono text-lg font-bold text-text-primary mb-4">
            Certifications
          </h2>
          <ul className="space-y-2 text-sm text-text-muted">
            {certifications.map((cert) => (
              <li key={cert.hash} className="flex flex-wrap items-baseline gap-2">
                <span className="text-text-primary">{cert.title}</span>
                <span className="text-text-faint">·</span>
                <span>{cert.org}</span>
                <span className="font-mono text-xs text-text-faint">{cert.date}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="mt-12 text-sm text-text-muted">
        Omkar Jadhav&apos;s degrees are listed on{" "}
        <Link to="/education" className="text-git-green hover:underline">
          the education page
        </Link>
        , and the software he has built is on{" "}
        <Link to="/projects" className="text-git-green hover:underline">
          the projects page
        </Link>
        .
      </p>
    </PageShell>
  );
}
