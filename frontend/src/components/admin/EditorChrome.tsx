
import { useLocation } from "react-router-dom";
import {
  User,
  Code2,
  GitCommit,
  GitBranch,
  Terminal,
  Eye,
  type LucideIcon,
} from "lucide-react";

type FileMeta = {
  match: (p: string) => boolean;
  label: string;
  icon: LucideIcon;
  colorClass: string;
};

const FILES: FileMeta[] = [
  {
    match: (p) => p === "/admin",
    label: "git-status",
    icon: Terminal,
    colorClass: "text-git-green",
  },
  {
    match: (p) => p.startsWith("/admin/profile"),
    label: "profile.json",
    icon: User,
    colorClass: "text-git-green",
  },
  {
    match: (p) => p.startsWith("/admin/projects"),
    label: "projects.json",
    icon: Code2,
    colorClass: "text-git-purple",
  },
  {
    match: (p) => p.startsWith("/admin/experience"),
    label: "experience.json",
    icon: GitCommit,
    colorClass: "text-git-orange",
  },
  {
    match: (p) => p.startsWith("/admin/skills"),
    label: "skills.json",
    icon: GitBranch,
    colorClass: "text-git-yellow",
  },
];

export function EditorChrome({ children }: { children: React.ReactNode }) {
  const pathname = useLocation().pathname;
  const file = FILES.find((f) => f.match(pathname)) ?? FILES[0];
  const Icon = file.icon;

  return (
    <div className="flex-1 flex flex-col overflow-hidden min-w-0">
      <div className="hidden md:flex items-center h-9 shrink-0 border-b border-terminal-border bg-terminal-bg">
        <div
          className="flex items-center gap-2 px-3 h-full font-mono text-xs border-r border-terminal-border bg-terminal-surface text-text-primary"
          style={{ borderTop: "2px solid rgb(var(--color-git-green))" }}
        >
          <Icon size={11} className={file.colorClass} />
          <span>{file.label}</span>
          <span className="ml-1 w-1.5 h-1.5 rounded-full bg-text-faint" />
        </div>
        <div className="flex-1" />
        <div className="flex items-center gap-2 px-3 font-mono text-[11px] text-text-faint">
          <Eye size={11} />
          <span>Edit · Preview</span>
        </div>
      </div>
      <div className="flex-1 overflow-y-auto">{children}</div>
    </div>
  );
}
