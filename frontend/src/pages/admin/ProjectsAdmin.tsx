import { useProjects } from "@/api/projects";
import { ProjectsClient } from "@/components/admin/ProjectsClient";

/**
 * Admin projects page (replaces admin/projects/page.tsx). Loads the list via
 * useQuery and hands it to ProjectsClient, whose CRUD mutations invalidate the
 * ['projects'] query so the list (and the public site) update live.
 */
export default function ProjectsAdmin() {
  const { data: projects, isPending, isError, error } = useProjects();

  if (isPending) {
    return <p className="font-mono text-sm text-text-muted">Loading projects…</p>;
  }
  if (isError || !projects) {
    return (
      <p className="font-mono text-sm text-git-red">
        Failed to load projects{error instanceof Error ? `: ${error.message}` : ""}.
      </p>
    );
  }

  return <ProjectsClient initialProjects={projects} />;
}
