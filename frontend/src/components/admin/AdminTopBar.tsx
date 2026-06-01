
import { Link, useNavigate } from "react-router-dom";
import { Folder, GitBranch, ExternalLink, LogOut } from "lucide-react";
import { useToast } from "./ToastProvider";

export function AdminTopBar({ profileName }: { profileName: string }) {
  const navigate = useNavigate();
  const { toast } = useToast();

  async function handleLogout() {
    await fetch("/api/auth/logout", { method: "POST" });
    toast("Logged out", "success");
    navigate("/admin/login");
  }

  return (
    <div className="hidden md:flex items-center justify-between h-11 shrink-0 px-4 border-b border-terminal-border bg-terminal-surface">
      <div className="flex items-center gap-3 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-full" style={{ background: "#ff5f57" }} />
          <span className="w-3 h-3 rounded-full" style={{ background: "#febc2e" }} />
          <span className="w-3 h-3 rounded-full" style={{ background: "#28c840" }} />
        </div>
        <div className="font-mono text-xs flex items-center gap-2 text-text-muted min-w-0">
          <Folder size={12} className="text-git-blue shrink-0" />
          <span className="truncate">~/portfolio/admin</span>
          <span className="text-text-faint">—</span>
          <span className="text-git-green truncate">{profileName}</span>
        </div>
      </div>

      <div className="flex items-center gap-2 shrink-0">
        <div className="font-mono text-[11px] flex items-center gap-1.5 mr-1 text-text-muted">
          <GitBranch size={11} className="text-git-green" />
          main
        </div>
        <Link
          to="/"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-mono text-xs font-medium text-text-primary bg-terminal-bg border border-terminal-border hover:brightness-125 transition-all"
        >
          <ExternalLink size={11} />
          View site
        </Link>
        <button
          onClick={handleLogout}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-mono text-xs font-medium text-text-muted hover:text-git-red hover:bg-git-red/10 transition-all"
        >
          <LogOut size={11} />
          Logout
        </button>
      </div>
    </div>
  );
}
