import { profile } from "@/data/profile";

export function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="border-t border-terminal-border bg-terminal-surface/50 py-8 px-4 font-mono">
      <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-text-faint">
        <div className="flex items-center gap-2">
          <span className="text-git-green">⑂</span>
          <span>{profile.handle}</span>
          <span className="text-terminal-border">/</span>
          <span className="text-git-blue">main</span>
        </div>

        <p className="text-center">
          <span className="text-text-muted">Made with</span>{" "}
          <code className="text-git-green">git push</code>{" "}
          <span className="text-text-muted">·</span>{" "}
          <span className="text-text-muted">Next.js + Tailwind</span>
        </p>

        <div className="flex items-center gap-3">
          <span className="text-terminal-border">© {year}</span>
          <span className="px-1.5 py-0.5 rounded border border-terminal-border text-[10px]">
            v1.0.0
          </span>
        </div>
      </div>
    </footer>
  );
}
