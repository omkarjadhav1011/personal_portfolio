
import { useLocation } from "react-router-dom";
import { GitBranch } from "lucide-react";

const FILE_TYPE_BY_PATH: { match: (p: string) => boolean; type: string }[] = [
  { match: (p) => p.startsWith("/admin/profile"), type: "JSON" },
  { match: (p) => p.startsWith("/admin/projects"), type: "JSON" },
  { match: (p) => p.startsWith("/admin/experience"), type: "JSON" },
  { match: (p) => p.startsWith("/admin/skills"), type: "JSON" },
];

function fileTypeFor(pathname: string) {
  return FILE_TYPE_BY_PATH.find((e) => e.match(pathname))?.type ?? "TEXT";
}

export function AdminStatusBar() {
  const pathname = useLocation().pathname;
  const type = fileTypeFor(pathname);

  return (
    <div className="hidden md:flex items-center h-6 shrink-0 px-3 gap-4 border-t border-terminal-border bg-git-green/90 text-terminal-bg font-mono text-[10px]">
      <span className="flex items-center gap-1.5">
        <GitBranch size={9} /> main
      </span>
      <span>↑ 0 ↓ 0</span>
      <span>no changes</span>
      <div className="flex-1" />
      <span>UTF-8</span>
      <span>LF</span>
      <span>{type}</span>
      <span>Ln 1, Col 1</span>
      <span className="flex items-center gap-1">● connected</span>
    </div>
  );
}
