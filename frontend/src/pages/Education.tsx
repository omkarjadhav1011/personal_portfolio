import { Link } from "react-router-dom";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PageShell } from "@/components/layout/PageShell";
import { useExperience } from "@/api/experience";

/**
 * Education — and the cheapest real disambiguation on the site.
 *
 * Two of the other Omkar Jadhavs in software are also in Kolhapur, so
 * "Omkar Jadhav Kolhapur" is contested. Neither attended KIT, so
 * "Omkar Jadhav KIT College Kolhapur" is not. Target the institution, not the
 * city.
 *
 * Institutions are written under their full official names because the schema
 * `alumniOf` nodes have to match a real-world organisation to be worth
 * anything — "KIT College" and "ICRE Gargoti" will not resolve.
 *
 * Percentages are visible here deliberately: the JSON-LD marks them up, and
 * structured data may only claim what a reader can see.
 */
export default function Education() {
  useDocumentTitle("Education");
  const { data: timeline } = useExperience();

  const education = (timeline ?? []).filter((entry) => entry.type === "education");

  return (
    <PageShell
      command="git log --branches='edu/*'"
      title={
        <>
          <span className="text-git-green">Omkar Jadhav</span> — Education
        </>
      }
      intro={
        <>
          Omkar Jadhav graduated from KIT&apos;s College of Engineering (Autonomous), Kolhapur in
          2026 with a B.Tech in Computer Science &amp; Engineering (Data Science). He reached it via
          a Diploma in Computer Engineering from the Institute of Civil and Rural Engineering,
          Gargoti, rather than the standard route.
        </>
      }
    >
      <section aria-labelledby="qualifications" className="space-y-6">
        <h2 id="qualifications" className="font-mono text-lg font-bold text-text-primary">
          Qualifications
        </h2>

        {education.map((entry) => (
          <article
            key={entry.hash}
            className="rounded-lg border border-terminal-border bg-terminal-surface/60 p-5"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="font-mono text-base font-bold text-text-primary">{entry.title}</h3>
              <span className="font-mono text-xs text-text-faint">
                {entry.date}
                {entry.dateEnd ? ` — ${entry.dateEnd}` : ""}
              </span>
            </div>
            <p className="mt-1 font-mono text-sm text-git-blue">{entry.org}</p>

            {entry.description.length > 0 && (
              <ul className="mt-3 space-y-1.5 text-sm text-text-muted leading-relaxed">
                {entry.description.map((line) => (
                  <li key={line} className="flex gap-2">
                    <span className="text-text-faint shrink-0" aria-hidden="true">
                      —
                    </span>
                    <span>{line}</span>
                  </li>
                ))}
              </ul>
            )}
          </article>
        ))}
      </section>

      <section aria-labelledby="why-this-path" className="mt-12">
        <h2 id="why-this-path" className="font-mono text-lg font-bold text-text-primary mb-3">
          The diploma route
        </h2>
        <p className="text-sm sm:text-base text-text-muted leading-relaxed">
          Omkar Jadhav completed a three-year Diploma in Computer Engineering before starting his
          B.Tech, which meant covering data structures, DBMS and object-oriented programming years
          earlier than the standard path does. He entered the degree already writing code, and
          finished it in 2026 while working as a Software Developer Intern at Nonstop IO
          Technologies from February of that year.
        </p>
      </section>

      <p className="mt-12 text-sm text-text-muted">
        His professional work is described on{" "}
        <Link to="/experience" className="text-git-green hover:underline">
          the experience page
        </Link>
        , and there is more background on{" "}
        <Link to="/about" className="text-git-green hover:underline">
          the about page
        </Link>
        .
      </p>
    </PageShell>
  );
}
