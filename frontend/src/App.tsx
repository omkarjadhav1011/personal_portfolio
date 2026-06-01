/**
 * Placeholder page for Phase 7.1 — exercises the ported terminal theme
 * (terminal/git/text colors, mono font, shadows, grid background, animations)
 * so a styled page confirms Tailwind + theme wiring before real pages land.
 */
export default function App() {
  return (
    <main className="min-h-screen bg-terminal-bg bg-grid-pattern bg-grid font-sans text-text-primary flex items-center justify-center p-6">
      <div className="w-full max-w-2xl rounded-xl bg-terminal-window shadow-terminal-green overflow-hidden">
        {/* Title bar */}
        <div className="flex items-center gap-2 border-b border-terminal-border bg-terminal-surface px-4 py-3">
          <span className="h-3 w-3 rounded-full bg-git-red" />
          <span className="h-3 w-3 rounded-full bg-git-yellow" />
          <span className="h-3 w-3 rounded-full bg-git-green" />
          <span className="ml-3 font-mono text-2xs uppercase tracking-widest text-text-muted">
            portfolio — frontend
          </span>
        </div>

        {/* Body */}
        <div className="space-y-3 p-6 font-mono text-sm">
          <p className="text-text-muted">
            <span className="text-git-green">$</span> npm run dev
          </p>
          <p className="text-git-green">
            ✓ Vite + React 18 + TypeScript ready
          </p>
          <p className="text-git-blue">
            ✓ Tailwind theme ported from Next.js
          </p>
          <p className="text-text-secondary">
            Terminal theme, fonts, and animations are wired.
            <span className="ml-1 inline-block h-4 w-2 translate-y-0.5 animate-cursor-blink bg-git-green" />
          </p>
          <p className="pt-2 text-text-faint text-2xs">
            Phase 7.1 — scaffold + theme carry-over
          </p>
        </div>
      </div>
    </main>
  );
}
