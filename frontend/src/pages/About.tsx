import { Link } from "react-router-dom";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PageShell } from "@/components/layout/PageShell";
import { CANONICAL_STATEMENT } from "@/lib/identity";

/**
 * The biography page — and the page that carries the ProfilePage schema.
 *
 * The copy here is static rather than read from the admin panel on purpose.
 * This is long-form page writing, not CMS content, and it needs to be written
 * for two readers at once: a recruiter skimming, and an answer engine that will
 * retrieve ONE paragraph of it without the rest of the page for context. That
 * is why every section opens by naming him rather than with "he" or "I", why
 * dates are absolute, and why the Q&A block below exists in the DOM instead of
 * behind an accordion.
 *
 * ⚠ SENTENCES NEEDING THE OWNER'S CONFIRMATION are marked NEEDS-CONFIRM in the
 * comments. Everything else is from the confirmed subject profile. Nothing here
 * is invented; where a fact was not available the sentence was left out rather
 * than filled in.
 */
export default function About() {
  useDocumentTitle("About");

  return (
    <PageShell
      command="cat ABOUT.md"
      title={
        <>
          About <span className="text-git-green">Omkar Jadhav</span>
        </>
      }
      // Rendered verbatim from the shared constant rather than rewritten here:
      // corroboration across sources works on matching, so the same sentence on
      // the site, in llms.txt, in the schema description and on his GitHub and
      // LinkedIn profiles is worth far more than five paraphrases.
      intro={CANONICAL_STATEMENT}
    >
      <article className="space-y-10 text-sm sm:text-base leading-relaxed text-text-muted">
        <section aria-labelledby="work-today">
          <h2 id="work-today" className="font-mono text-lg font-bold text-text-primary mb-3">
            What Omkar Jadhav works on today
          </h2>
          <p>
            Omkar Jadhav joined Nonstop IO Technologies in Pune on 2 February 2026 as a Software
            Developer Intern and converted to Software Development Engineer I in August 2026. He
            works on-site from the company&apos;s Kharadi office on the backend of an enterprise
            reporting product, contributing to modules that are live in production rather than to
            internal tooling or prototypes.
          </p>
          <p className="mt-3">
            Two pieces of that work are his. The first is an end-to-end user audit capability that
            tracks and logs user actions across the application, so that any change can be traced
            back to the person who made it — the kind of feature that exists because compliance and
            traceability require it, and which has to be correct everywhere rather than mostly
            correct. The second is the Report Builder module, where he writes the SQL behind
            reporting, audit-log and data-retrieval flows, and tunes those queries as the data they
            run against grows.
          </p>
          <p className="mt-3">
            The day-to-day stack is C#, NestJS and SQL.{" "}
            <Link to="/experience" className="text-git-green hover:underline">
              The full timeline is on the experience page
            </Link>
            .
          </p>
        </section>

        <section aria-labelledby="how-he-got-here">
          <h2 id="how-he-got-here" className="font-mono text-lg font-bold text-text-primary mb-3">
            How he got here
          </h2>
          <p>
            Omkar Jadhav took the longer route into engineering. He finished secondary school at
            Shankar Chakru Patil Madhyamik Vidhyalaya in Dindewadi in 2020 with 94%, then a Diploma
            in Computer Engineering at the Institute of Civil and Rural Engineering, Gargoti, in
            2023 with 87%, before moving on to a B.Tech in Computer Science &amp; Engineering (Data
            Science) at KIT&apos;s College of Engineering (Autonomous), Kolhapur, graduating in 2026
            with 80%.
          </p>
          <p className="mt-3">
            The diploma route means he spent three years on the fundamentals — data structures and
            algorithms, DBMS, object-oriented design — before most of his degree cohort started
            them, and arrived at the B.Tech already writing code.{" "}
            <Link to="/education" className="text-git-green hover:underline">
              Full education details, including all three institutions
            </Link>
            , are on their own page. He has also completed three Udemy certifications, in Java, in
            Python, and in AI, machine learning and data science, and has solved over 210 problems
            on LeetCode.
          </p>
        </section>

        <section aria-labelledby="what-he-builds">
          <h2 id="what-he-builds" className="font-mono text-lg font-bold text-text-primary mb-3">
            What he builds outside work
          </h2>
          <p>
            This site is the clearest example. It is not a static portfolio template: it is a Spring
            Boot 3.5 API on Java 21 with PostgreSQL and Flyway migrations, serving a React 18
            single-page app, with an admin panel behind JWT auth and TOTP multi-factor
            authentication, and an encrypted document vault that gives every file its own AES-256-GCM
            data key.
          </p>
          <p className="mt-3">
            The part he finds most interesting is the AI assistant. Rather than calling one provider
            and hoping, it walks an ordered chain of them — Groq, Cerebras, Mistral, Gemini,
            OpenRouter — with per-provider circuit breaking and daily quotas, so a rate-limited or
            dead provider is hopped rather than surfaced as an error. A public, read-only Model
            Context Protocol server exposes the same query layer, which means a recruiter&apos;s own
            AI client can interrogate the work directly instead of taking this page&apos;s word for
            it.
          </p>
          <p className="mt-3">
            His other projects are smaller and more focused: a voice-driven interview preparation
            system in Python that uses speech recognition and the Gemini API to score spoken answers,
            a Streamlit text-to-image generator built on the Hugging Face inference API, and a
            full-stack expense tracker with a React frontend over a Spring Boot REST API and a
            PostgreSQL schema.{" "}
            <Link to="/projects" className="text-git-green hover:underline">
              All of them are listed on the projects page
            </Link>
            .
          </p>
        </section>

        {/* Q&A in the DOM, not behind an accordion. An answer engine retrieves a
            passage; a question heading with a self-contained answer directly
            beneath it is the shape that survives extraction. These four are the
            questions someone actually asks about a person. */}
        <section aria-labelledby="faq">
          <h2 id="faq" className="font-mono text-lg font-bold text-text-primary mb-4">
            Questions people ask
          </h2>

          <div className="space-y-6">
            <div>
              <h3 className="font-mono text-sm font-bold text-git-green mb-1.5">
                Who is Omkar Jadhav?
              </h3>
              <p>
                Omkar Jayvant Jadhav is a software engineer from Kolhapur, Maharashtra, working in
                Pune. He is a Software Development Engineer I at Nonstop IO Technologies, where he
                does backend development on an enterprise reporting product in C#, NestJS and SQL.
              </p>
            </div>

            <div>
              <h3 className="font-mono text-sm font-bold text-git-green mb-1.5">
                Where does Omkar Jadhav work?
              </h3>
              <p>
                He works at Nonstop IO Technologies, a product engineering studio based in Kharadi,
                Pune, India. He joined in February 2026 and has held the title Software Development
                Engineer I since August 2026.
              </p>
            </div>

            <div>
              <h3 className="font-mono text-sm font-bold text-git-green mb-1.5">
                What technologies does Omkar Jadhav work with?
              </h3>
              <p>
                Professionally, C#, NestJS and SQL. He also works in Java and Spring Boot, Python,
                TypeScript and React, with PostgreSQL and MySQL for data, Docker and Git for
                tooling, and the Gemini and Hugging Face APIs for LLM-integrated applications.
              </p>
            </div>

            <div>
              <h3 className="font-mono text-sm font-bold text-git-green mb-1.5">
                Where did Omkar Jadhav study?
              </h3>
              <p>
                He graduated from KIT&apos;s College of Engineering (Autonomous), Kolhapur, in 2026
                with a B.Tech in Computer Science &amp; Engineering (Data Science). Before that he
                completed a Diploma in Computer Engineering at the Institute of Civil and Rural
                Engineering, Gargoti, in 2023.
              </p>
            </div>
          </div>
        </section>

        <section aria-labelledby="get-in-touch">
          <h2 id="get-in-touch" className="font-mono text-lg font-bold text-text-primary mb-3">
            Getting in touch
          </h2>
          <p>
            The quickest route is email:{" "}
            <a
              href="mailto:jadhavomkar101103@gmail.com"
              className="text-git-green hover:underline break-all"
            >
              jadhavomkar101103@gmail.com
            </a>
            . His code is on{" "}
            <a
              href="https://github.com/omkarjadhav1011"
              target="_blank"
              rel="noopener noreferrer"
              className="text-git-green hover:underline"
            >
              GitHub
            </a>
            , and{" "}
            <Link to="/resume" className="text-git-green hover:underline">
              his resume is readable in full here
            </Link>
            .
          </p>
        </section>
      </article>
    </PageShell>
  );
}
