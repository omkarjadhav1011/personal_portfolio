import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { Link } from "react-router-dom";

/** Catch-all 404 — ported from Next's not-found.tsx (now using react-router Link). */
export function NotFound() {
  useDocumentTitle("Not Found");
  const branches = [
    { to: "/", label: "git checkout main", desc: "→ home" },
    { to: "/#projects", label: "git checkout projects", desc: "→ my work" },
    { to: "/#contact", label: "git checkout contact", desc: "→ get in touch" },
  ];

  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-terminal-bg">
      <div className="max-w-lg w-full font-mono">
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden shadow-terminal">
          <div className="flex items-center gap-1.5 px-4 py-3 bg-terminal-bg border-b border-terminal-border">
            <span className="w-3 h-3 rounded-full bg-dot-red" />
            <span className="w-3 h-3 rounded-full bg-dot-yellow" />
            <span className="w-3 h-3 rounded-full bg-dot-green" />
            <span className="ml-2 text-xs text-text-faint">portfolio terminal</span>
          </div>

          <div className="p-8 space-y-3">
            <p className="text-text-muted text-sm">
              <span className="text-git-green">$</span>{" "}
              <span className="text-text-primary">git checkout this-page</span>
            </p>
            <p className="text-git-red font-bold">
              fatal: branch &apos;this-page&apos; not found
            </p>
            <p className="text-text-muted text-sm">
              error: pathspec did not match any file(s) known to git
            </p>

            <div className="pt-4 border-t border-terminal-border">
              <p className="text-text-faint text-xs mb-4"># Available branches:</p>
              <div className="space-y-2">
                {branches.map((link) => (
                  <Link
                    key={link.to}
                    to={link.to}
                    className="flex items-center gap-3 text-sm text-text-muted hover:text-git-green transition-colors group"
                  >
                    <span className="text-git-green">⑂</span>
                    <span className="group-hover:underline">{link.label}</span>
                    <span className="text-text-faint text-xs">{link.desc}</span>
                  </Link>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
