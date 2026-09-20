
import { useEffect, useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { FileSearch, Sparkles, Terminal } from "lucide-react";
import { useScrollProgress } from "@/hooks/useScrollProgress";
import { useCommandPaletteStore } from "@/store/commandPalette";
import { profile as staticProfile } from "@/data/profile";
import { useProfile } from "@/api/profile";
import { cn } from "@/lib/utils";

/**
 * Primary navigation.
 *
 * Items with `to` are real routes and render as <Link>, i.e. real <a href>
 * elements. This matters more than it looks: the whole nav used to be
 * <button onClick={scrollTo}>, so with JavaScript disabled the site had no
 * links at all and a crawler could not reach anything from the homepage.
 *
 * Items with `id` are still homepage sections — skills and contact were not
 * split into their own pages — and keep the smooth-scroll behaviour.
 */
const NAV_ITEMS: { label: string; to?: string; id?: string }[] = [
  { label: "about", to: "/about" },
  { label: "skills", id: "skills" },
  { label: "projects", to: "/projects" },
  { label: "log", to: "/experience" },
  { label: "contact", id: "contact" },
];

// The "new" badge on the recruiter link auto-retires after this date so it
// doesn't live in the navbar forever (suggestion #3 — time-box the badge).
const RECRUITER_BADGE_UNTIL = new Date("2026-08-31T23:59:59");

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
  const showRecruiterBadge = new Date() < RECRUITER_BADGE_UNTIL;

  // While the mobile menu is open, the page behind it shouldn't scroll.
  useEffect(() => {
    if (!mobileOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [mobileOpen]);

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
            {NAV_ITEMS.map((item) => {
              const isActive = item.to
                ? location.pathname === item.to
                : activeSection === item.id;
              const className = cn(
                "px-3 py-1.5 rounded-lg transition-colors duration-200",
                // #1 — one signal for active: green text + the `*` marker.
                // The bordered/tinted pill was removed to lighten the bar.
                isActive
                  ? "text-git-green"
                  : "text-text-muted hover:text-text-primary hover:bg-terminal-surface",
              );
              const body = (
                <>
                  {isActive && <span className="mr-1 text-git-green">*</span>}
                  {item.label}
                </>
              );
              return item.to ? (
                <Link key={item.label} to={item.to} className={className}>
                  {body}
                </Link>
              ) : (
                <button key={item.label} onClick={() => goTo(item.id!)} className={className}>
                  {body}
                </button>
              );
            })}

            {/* #2 — divider separates content nav from the recruiter CTA */}
            <span className="mx-2 h-4 w-px bg-terminal-border" aria-hidden="true" />

            {/* #4 — recruiter reads as a deliberate CTA: same shape/weight as the
                nav links, set apart by the divider rather than extra chrome. */}
            <Link
              to="/recruiter"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-colors duration-200 text-text-muted hover:text-git-green hover:bg-terminal-surface"
            >
              <FileSearch size={12} className="text-git-green/70" />
              recruiter
              {/* #3 — softer badge (no harsh border), auto-retires after the date */}
              {showRecruiterBadge && (
                <span className="text-[9px] px-1 py-px rounded bg-git-green/15 text-git-green uppercase tracking-wider">
                  new
                </span>
              )}
            </Link>
          </div>

          {/* Terminal trigger + mobile toggle */}
          <div className="flex items-center gap-3">
            {/* Terminal trigger (desktop) — opens the command palette */}
            <button
              onClick={() => openInMode("terminal")}
              className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-lg border border-terminal-border bg-terminal-surface hover:border-git-green/50 hover:bg-git-green/5 text-text-faint hover:text-text-muted text-xs font-mono transition-all duration-200 group cursor-pointer"
              aria-label="Open terminal (Ctrl+K)"
            >
              <Terminal
                size={12}
                className="text-git-green/50 group-hover:text-git-green transition-colors shrink-0"
              />
              <span className="hidden md:block text-text-faint group-hover:text-text-muted transition-colors">
                terminal
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

            {/* AI trigger removed from navbar — the floating "Ask AI" button
                (FloatingAIButton) already opens the palette in AI mode, and the
                terminal button above exposes AI mode via its tabs. */}

            {/* Mobile hamburger */}
            <button
              className="md:hidden text-text-muted hover:text-text-primary transition-colors p-3 -mr-1 cursor-pointer"
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
          <>
            {/* Backdrop — tap outside the panel to close */}
            <motion.button
              type="button"
              aria-label="Close mobile menu"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1, transition: { duration: 0.2 } }}
              exit={{ opacity: 0, transition: { duration: 0.15 } }}
              onClick={() => setMobileOpen(false)}
              className="fixed inset-0 top-14 z-30 bg-black/40 md:hidden"
            />
            <motion.div
              initial={{ opacity: 0, y: -16 }}
              animate={{ opacity: 1, y: 0, transition: { duration: 0.25, ease: "easeOut" } }}
              exit={{ opacity: 0, y: -16, transition: { duration: 0.2, ease: "easeIn" } }}
              className="fixed top-14 left-0 right-0 z-40 max-h-[calc(100dvh-3.5rem)] overflow-y-auto overscroll-contain bg-terminal-bg/95 backdrop-blur-md border-b border-terminal-border p-4 font-mono space-y-1 md:hidden"
            >
            <button
              onClick={() => { openInMode("ai"); setMobileOpen(false); }}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm text-git-green bg-git-green/5 border border-git-green/20 hover:bg-git-green/10 transition-colors cursor-pointer"
            >
              <Sparkles size={14} className="text-git-green/70" />
              <span>Ask AI about {profile.name.split(" ")[0]}</span>
            </button>

            {/* Terminal trigger — opens the command palette */}
            <button
              onClick={() => { openInMode("terminal"); setMobileOpen(false); }}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm text-git-green bg-git-green/5 border border-git-green/20 hover:bg-git-green/10 transition-colors cursor-pointer"
            >
              <Terminal size={14} className="text-git-green/70" />
              <span>Open terminal</span>
            </button>

            <Link
              to="/recruiter"
              onClick={() => setMobileOpen(false)}
              className="w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm text-text-muted hover:text-git-green hover:bg-terminal-surface border border-terminal-border"
            >
              <FileSearch size={14} className="text-git-green/70" />
              <span>Recruiter mode</span>
              <span className="ml-auto text-xs px-1.5 py-0.5 rounded bg-git-green/15 border border-git-green/30 text-git-green uppercase tracking-wider">
                new
              </span>
            </Link>

            <div className="border-t border-terminal-border my-1" />

            {NAV_ITEMS.map((item) => {
              const isActive = item.to
                ? location.pathname === item.to
                : activeSection === item.id;
              const className = cn(
                "w-full text-left px-4 py-3 rounded-lg text-sm transition-colors",
                isActive
                  ? "text-git-green bg-git-green/10"
                  : "text-text-muted hover:text-text-primary hover:bg-terminal-surface",
              );
              const body = (
                <>
                  <span className="text-text-faint mr-3">$</span>
                  git checkout {item.label}
                </>
              );
              return item.to ? (
                <Link
                  key={item.label}
                  to={item.to}
                  onClick={() => setMobileOpen(false)}
                  className={className}
                >
                  {body}
                </Link>
              ) : (
                <button
                  key={item.label}
                  onClick={() => {
                    goTo(item.id!);
                    setMobileOpen(false);
                  }}
                  className={className}
                >
                  {body}
                </button>
              );
            })}

            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
