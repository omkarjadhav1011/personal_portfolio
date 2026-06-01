import { useProjects } from "@/api/projects";

/**
 * Phase 7.2 scratch page — proves the API client + TanStack Query path by
 * rendering live data from GET /api/projects. Replaced by real pages later.
 */
export default function ScratchProjects() {
  const { data: projects, isPending, isError, error } = useProjects();

  return (
    <main className="min-h-screen bg-terminal-bg bg-grid-pattern bg-grid font-sans text-text-primary p-6">
      <div className="mx-auto w-full max-w-3xl rounded-xl bg-terminal-window shadow-terminal overflow-hidden">
        <div className="flex items-center gap-2 border-b border-terminal-border bg-terminal-surface px-4 py-3">
          <span className="h-3 w-3 rounded-full bg-git-red" />
          <span className="h-3 w-3 rounded-full bg-git-yellow" />
          <span className="h-3 w-3 rounded-full bg-git-green" />
          <span className="ml-3 font-mono text-2xs uppercase tracking-widest text-text-muted">
            GET /api/projects
          </span>
        </div>

        <div className="p-6 font-mono text-sm">
          {isPending && <p className="text-text-muted">Loading projects…</p>}

          {isError && (
            <p className="text-git-red">Error: {(error as Error).message}</p>
          )}

          {projects && (
            <>
              <p className="mb-4 text-git-green">
                ✓ {projects.length} project{projects.length === 1 ? "" : "s"} loaded
              </p>
              <ul className="space-y-3">
                {projects.map((p) => (
                  <li
                    key={p.id}
                    className="rounded-lg border border-terminal-border bg-terminal-surface p-4"
                  >
                    <div className="flex items-center gap-2">
                      {p.pinned && (
                        <span className="text-2xs uppercase tracking-wide text-git-yellow">
                          pinned
                        </span>
                      )}
                      <span className="text-git-blue">{p.repoName}</span>
                      <span
                        className="ml-auto inline-flex items-center gap-1 text-2xs text-text-muted"
                      >
                        <span
                          className="h-2 w-2 rounded-full"
                          style={{ backgroundColor: p.languageColor }}
                        />
                        {p.language}
                      </span>
                    </div>
                    <p className="mt-1 text-text-secondary">{p.description}</p>
                    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-2xs text-text-muted">
                      <span>★ {p.stars}</span>
                      <span>⑂ {p.forks}</span>
                      <span>{p.commits} commits</span>
                      {p.tags.length > 0 && (
                        <span className="text-text-faint">{p.tags.join(" · ")}</span>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </main>
  );
}
