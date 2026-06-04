
import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { FileSearch, Sparkles } from "lucide-react";
import { useScrollProgress } from "@/hooks/useScrollProgress";
import { useCommandPaletteStore } from "@/store/commandPalette";
import { profile as staticProfile } from "@/data/profile";
import { useProfile } from "@/api/profile";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

const NAV_SECTIONS = [
  { id: "about", label: "about" },
  { id: "skills", label: "skills" },
  { id: "projects", label: "projects" },
  { id: "experience", label: "log" },
  { id: "contact", label: "contact" },
];

function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth" });
}

export function Navbar() {
  const { data: profileData } = useProfile();
  const profile = profileData ?? staticProfile;
  const { progress, activeSection } = useScrollProgress();
  const [mobileOpen, setMobileOpen] = useState(false);
  const { openInMode } = useCommandPaletteStore();
  const navigate = useNavigate();
  const location = useLocation();

  // Section anchors only exist on the home page. When we're on another route
  // (e.g. a project detail page), route home first and let Home scroll to the
  // section once it has rendered (see useScrollToSection in Home).
  function goTo(id: string) {
    if (location.pathname === "/") {
      scrollTo(id);
    } else {
      navigate("/", { state: { scrollTo: id } });
    }
  }

  return (
    <>
      <nav className="fixed top-0 left-0 right-0 z-50 bg-terminal-bg/80 backdrop-blur-md border-b border-terminal-border">
        {/* Scroll progress bar */}
        <div className="absolute bottom-0 left-0 h-px bg-terminal-border w-full">
          <motion.div
            className="h-full bg-git-green"
            style={{ width: `${progress}%` }}
            transition={{ type: "tween", duration: 0.1 }}
          />
        </div>

        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          {/* Logo / branch indicator */}
          <button
            onClick={() => goTo("hero")}
            className="flex items-center gap-2 font-mono text-sm hover:opacity-80 transition-opacity cursor-pointer"
          >
            <span className="text-git-green font-bold">⑂</span>
            <span className="text-text-primary font-semibold">{profile.handle}</span>
            <span className="text-text-faint">/</span>
            <span className="text-git-blue">{profile.currentBranch}</span>
          </button>

          {/* Desktop nav */}
          <div className="hidden md:flex items-center gap-1 font-mono text-sm">
            {NAV_SECTIONS.map((s) => (
              <button
                key={s.id}
                onClick={() => goTo(s.id)}
                className={cn(
                  "px-3 py-1.5 rounded-lg transition-all duration-200",
                  activeSection === s.id
                    ? "text-git-green bg-git-green/10 border border-git-green/30"
                    : "text-text-muted hover:text-text-primary hover:bg-terminal-surface"
                )}
              >
                {activeSection === s.id && (
                  <span className="mr-1 text-git-green">*</span>
                )}
                {s.label}
              </button>
            ))}
            <Link
              to="/recruiter"
              className="ml-1 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all duration-200 text-text-muted hover:text-git-green border border-transparent hover:border-git-green/30 hover:bg-git-green/5"
            >
              <FileSearch size={12} className="text-git-green/70" />
              recruiter
              <span className="text-[9px] px-1 py-px rounded bg-git-green/15 border border-git-green/30 text-git-green uppercase tracking-wider">
                new
              </span>
            </Link>
          </div>

          {/* AI trigger + theme + mobile toggle */}
          <div className="flex items-center gap-3">
            {/* Theme toggle (desktop) */}
            <div className="hidden md:block">
              <ThemeToggle />
            </div>

            {/* Prominent clickable AI trigger — opens palette in AI mode */}
            <button
              onClick={() => openInMode("ai")}
              className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-lg border border-terminal-border bg-terminal-surface hover:border-git-green/50 hover:bg-git-green/5 text-text-faint hover:text-text-muted text-xs font-mono transition-all duration-200 group cursor-pointer"
              aria-label="Open AI assistant (Ctrl+K)"
            >
              <Sparkles
                size={12}
                className="text-git-green/50 group-hover:text-git-green transition-colors shrink-0"
              />
              <span className="hidden lg:block text-text-faint group-hover:text-text-muted transition-colors">
                Ask me anything...
              </span>
              <div className="hidden lg:flex items-center gap-0.5 ml-0.5 opacity-50 group-hover:opacity-70 transition-opacity">
                <kbd className="px-1 py-0.5 rounded text-2xs bg-terminal-bg border border-terminal-border leading-none">
                  Ctrl
                </kbd>
                <span className="text-2xs">+</span>
                <kbd className="px-1 py-0.5 rounded text-2xs bg-terminal-bg border border-terminal-border leading-none">
                  K
                </kbd>
              </div>
            </button>

            {/* Mobile hamburger */}
            <button
              className="md:hidden text-text-muted hover:text-text-primary transition-colors p-2 cursor-pointer"
              onClick={() => setMobileOpen(!mobileOpen)}
              aria-label="Toggle mobile menu"
            >
              <div className="space-y-1 w-5">
                <span className={cn("block h-px bg-current transition-all", mobileOpen && "rotate-45 translate-y-1.5")} />
                <span className={cn("block h-px bg-current transition-all", mobileOpen && "opacity-0")} />
                <span className={cn("block h-px bg-current transition-all", mobileOpen && "-rotate-45 -translate-y-1.5")} />
              </div>
            </button>
          </div>
        </div>
      </nav>

      {/* Mobile menu */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } }}
            exit={{ opacity: 0, y: -16, transition: { duration: 0.2, ease: "easeIn" } }}
            className="fixed top-14 left-0 right-0 z-40 bg-terminal-bg/95 backdrop-blur-md border-b border-terminal-border p-4 font-mono space-y-1 md:hidden"
          >
            {/* AI trigger */}
            <button
              onClick={() => { openInMode("ai"); setMobileOpen(false); }}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm text-git-green bg-git-green/5 border border-git-green/20 hover:bg-git-green/10 transition-colors cursor-pointer"
            >
              <Sparkles size={14} className="text-git-green/70" />
              <span>Ask AI about this developer</span>
            </button>

            <Link
              to="/recruiter"
              onClick={() => setMobileOpen(false)}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm text-text-muted hover:text-git-green hover:bg-terminal-surface border border-terminal-border"
            >
              <FileSearch size={14} className="text-git-green/70" />
              <span>Recruiter mode</span>
              <span className="ml-auto text-[10px] px-1.5 py-0.5 rounded bg-git-green/15 border border-git-green/30 text-git-green uppercase tracking-wider">
                new
              </span>
            </Link>

            <div className="border-t border-terminal-border my-1" />

            {NAV_SECTIONS.map((s) => (
              <button
                key={s.id}
                onClick={() => { goTo(s.id); setMobileOpen(false); }}
                className={cn(
                  "w-full text-left px-4 py-3 rounded-lg text-sm transition-colors",
                  activeSection === s.id
                    ? "text-git-green bg-git-green/10"
                    : "text-text-muted hover:text-text-primary hover:bg-terminal-surface"
                )}
              >
                <span className="text-text-faint mr-3">$</span>
                git checkout {s.id}
              </button>
            ))}

            <div className="border-t border-terminal-border my-1" />
            <div className="flex items-center justify-between px-4 py-2">
              <span className="text-text-faint text-xs font-mono">theme</span>
              <ThemeToggle />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
