import { Link } from "react-router-dom";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PageShell } from "@/components/layout/PageShell";
import { useDomainProjects } from "@/api/projects";
import { useProfile } from "@/api/profile";
import { PRCard } from "@/components/ui/PRCard";

/**
 * The project index.
 *
 * These pages matter more than their traffic suggests: they are the only pages
 * on the site that can rank for queries which do not contain his name. Someone
 * searching "expense tracker react spring boot postgresql" has never heard of
 * him, and this is the door they come through.
 */
export default function Projects() {
  useDocumentTitle("Projects");
  const { data: projects } = useDomainProjects();
  const { data: profile } = useProfile();

  const handle =
    profile?.socials.find((s) => s.icon === "github")?.url.split("/").pop() ?? "omkarjadhav1011";

  return (
    <PageShell
      command="ls -la ~/projects"
      title={
        <>
          Projects by <span className="text-git-green">Omkar Jadhav</span>
        </>
      }
      intro={
        <>
          Software Omkar Jadhav has designed and built — a Spring Boot and React portfolio with an
          AI assistant, an LLM-scored interview practice system, a text-to-image generator, and a
          full-stack expense tracker. Each has its own page with the stack and what the work
          involved.
        </>
      }
    >
      {projects && projects.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {projects.map((project, index) => (
            <PRCard key={project.id} project={project} index={index} handle={handle} />
          ))}
        </div>
      ) : (
        <p className="font-mono text-sm text-text-muted">No projects published yet.</p>
      )}

      <p className="mt-10 text-sm text-text-muted">
        More of Omkar Jadhav&apos;s code is on{" "}
        <a
          href={`https://github.com/${handle}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-git-green hover:underline"
        >
          GitHub
        </a>
        , and the work he does professionally is described on{" "}
        <Link to="/experience" className="text-git-green hover:underline">
          the experience page
        </Link>
        .
      </p>
    </PageShell>
  );
}
