
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import {
  Menu,
  X,
  Folder,
  ChevronDown,
  ChevronRight,
  User,
  Code2,
  GitCommit,
  GitBranch,
  Terminal,
  LogOut,
  Upload,
  ShieldCheck,
  HardDrive,
  type LucideIcon,
} from "lucide-react";
import { useToast } from "./ToastProvider";

type FileEntry = {
  id: string;
  label: string;
  path: string;
  icon: LucideIcon;
  colorClass: string;
};

const FILES: FileEntry[] = [
  { id: "status",     label: "git-status",      path: "/admin",            icon: Terminal,  colorClass: "text-git-green" },
  { id: "profile",    label: "profile.json",    path: "/admin/profile",    icon: User,      colorClass: "text-git-green" },
  { id: "projects",   label: "projects.json",   path: "/admin/projects",   icon: Code2,     colorClass: "text-git-purple" },
  { id: "experience", label: "experience.json", path: "/admin/experience", icon: GitCommit, colorClass: "text-git-orange" },
  { id: "skills",     label: "skills.json",     path: "/admin/skills",     icon: GitBranch, colorClass: "text-git-yellow" },
  { id: "drive",      label: "drive.json",      path: "/admin/drive",      icon: HardDrive, colorClass: "text-git-blue" },
];

function isActive(pathname: string, path: string) {
  return path === "/admin" ? pathname === "/admin" : pathname.startsWith(path);
}

export function AdminSidebar() {
  const pathname = useLocation().pathname;
  const navigate = useNavigate();
  const { toast } = useToast();
  const clear = useAuthStore((s) => s.clear);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [folderOpen, setFolderOpen] = useState(true);

  async function handleLogout() {
    clear();
    await fetch("/api/auth/logout", { method: "POST" });
    toast("Logged out", "success");
    navigate("/admin/login");
  }

  const navContent = (
    <>
      {/* Explorer header */}
      <div className="px-3 py-3 border-b border-terminal-border flex items-center justify-between font-mono text-[10px] uppercase tracking-widest text-text-faint">
        <span>Explorer</span>
        <button
          onClick={() => setMobileOpen(false)}
          className="md:hidden text-text-muted hover:text-text-primary transition-colors"
          aria-label="Close menu"
        >
          <X size={14} />
        </button>
      </div>

      {/* Workspace folder */}
      <button
        onClick={() => setFolderOpen((v) => !v)}
        className="w-full flex items-center gap-1.5 px-3 py-2 font-mono text-xs text-text-secondary hover:brightness-125 transition-all"
      >
        {folderOpen ? <ChevronDown size={11} /> : <ChevronRight size={11} />}
        <Folder size={12} className="text-git-blue" />
        <span className="font-semibold">portfolio-data</span>
        <span className="ml-auto text-[10px] text-text-faint">{FILES.length}</span>
      </button>

      {/* File list */}
      {folderOpen && (
        <nav className="pl-2 flex-1">
          {FILES.map((f) => {
            const active = isActive(pathname, f.path);
            const Icon = f.icon;
            return (
              <Link
                key={f.id}
                to={f.path}
                onClick={() => setMobileOpen(false)}
                className="w-full flex items-center gap-2 pl-5 pr-3 py-1.5 font-mono text-[12px] transition-all"
                style={{
                  background: active ? "rgb(var(--color-git-green) / 0.08)" : "transparent",
                  borderLeft: active
                    ? "2px solid rgb(var(--color-git-green))"
                    : "2px solid transparent",
                  color: active
                    ? "rgb(var(--color-git-green))"
                    : "rgb(var(--color-text-secondary))",
                }}
              >
                <Icon size={11} className={active ? "text-git-green" : f.colorClass} />
                <span className="truncate">{f.label}</span>
              </Link>
            );
          })}
        </nav>
      )}

      {/* Quick actions footer */}
      <div className="mt-auto border-t border-terminal-border px-3 py-3">
        <div className="font-mono text-[10px] uppercase tracking-widest mb-2 text-text-faint">
          Quick actions
        </div>
        <Link
          to="/"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full flex items-center gap-2 px-2 py-1.5 rounded font-mono text-[11px] text-text-muted hover:text-text-primary hover:brightness-125 transition-all"
        >
          <Upload size={11} className="text-git-green" />
          View live site
        </Link>
        <Link
          to="/admin/mfa/setup"
          onClick={() => setMobileOpen(false)}
          className="w-full flex items-center gap-2 px-2 py-1.5 rounded font-mono text-[11px] text-text-muted hover:text-text-primary hover:brightness-125 transition-all"
          style={{
            color: isActive(pathname, "/admin/mfa/setup")
              ? "rgb(var(--color-git-green))"
              : undefined,
          }}
        >
          <ShieldCheck size={11} className="text-git-yellow" />
          Enable 2FA
        </Link>
        <button
          onClick={handleLogout}
          className="w-full flex items-center gap-2 px-2 py-1.5 rounded font-mono text-[11px] text-git-red/80 hover:text-git-red hover:bg-git-red/5 transition-all"
        >
          <LogOut size={11} />
          git logout
        </button>
        <div className="mt-2 px-1 font-mono text-[10px] text-text-faint">
          HEAD → admin/panel
        </div>
      </div>
    </>
  );

  return (
    <>
      {/* Mobile hamburger */}
      <button
        onClick={() => setMobileOpen(true)}
        className="fixed top-4 left-4 z-50 md:hidden p-2 rounded-lg border border-terminal-border bg-terminal-surface text-text-muted hover:text-text-primary transition-colors"
        aria-label="Open menu"
      >
        <Menu size={18} />
      </button>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/60 md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Mobile drawer */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 bg-terminal-window border-r border-terminal-border flex flex-col transform transition-transform duration-200 md:hidden ${
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        {navContent}
      </aside>

      {/* Desktop sidebar */}
      <aside className="hidden md:flex w-60 shrink-0 border-r border-terminal-border bg-terminal-window flex-col overflow-y-auto">
        {navContent}
      </aside>
    </>
  );
}
