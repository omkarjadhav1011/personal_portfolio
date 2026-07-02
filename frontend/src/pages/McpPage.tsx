import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, ArrowRight, Check, Copy, Play, Plug, ShieldCheck } from "lucide-react";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import { TerminalWindow } from "@/components/ui/TerminalWindow";
import { copyToClipboard } from "@/lib/clipboard";
import { BASE_URL } from "@/lib/api";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

// Backend origin: VITE_API_URL in prod (the Render backend); falls back to the current
// origin in dev. The MCP SSE endpoint is always `${backend}/mcp/sse`.
const BACKEND = BASE_URL || (typeof window !== "undefined" ? window.location.origin : "");
const SSE_URL = `${BACKEND}/mcp/sse`;

// ── Demo video ───────────────────────────────────────────────────────────────
// Drop a URL here to show the demo. A direct .mp4/.webm file renders inline; any
// other URL renders a "Watch demo ↗" link (keeps the SPA's strict CSP simple — no
// third-party iframe needed). Leave empty to show a "coming soon" placeholder.
const DEMO_VIDEO_URL = "";

const CLAUDE_CONFIG = `{
  "mcpServers": {
    "omkar-portfolio": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "${SSE_URL}"]
    }
  }
}`;

const INSPECTOR_CMD = "npx @modelcontextprotocol/inspector";

const TOOLS: ReadonlyArray<readonly [string, string]> = [
  ["get_profile", "Name, headline, current role, location, availability, and links."],
  ["get_availability", "Open-to-work status, current focus, and location."],
  ["list_projects", "All projects — optionally filtered by a tech or keyword."],
  ["get_project", "Full detail on one project by its slug."],
  ["list_skills", "The full skill set, each with a proficiency level."],
  ["get_experience", "Depth in an area: matching skills, roles, and projects."],
  ["get_resume_summary", "Structured résumé highlights + the extracted text."],
  ["match_against_jd", "Paste a job description → a structured fit score + reasons."],
];

const EXAMPLE_PROMPTS = [
  "What is this candidate's current role, and are they available?",
  "List their projects that use Spring Boot.",
  "How much PostgreSQL experience do they have?",
  "Here's a job description: <paste>. Is this candidate a fit?",
];

export default function McpPage() {
  useDocumentTitle("MCP Server");

  return (
    <section className="pt-20 pb-16 sm:pt-24 px-4 scroll-mt-14">
      <div className="max-w-3xl mx-auto">
        <ScrollReveal>
          <Link
            to="/"
            className="inline-flex items-center gap-1.5 mb-6 font-mono text-xs text-text-muted hover:text-git-green transition-colors"
          >
            <ArrowLeft size={12} /> cd ..
          </Link>

          <div className="flex items-center gap-3 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">$</span>
            <span>npx mcp-remote — connect your AI</span>
          </div>
          <h1 className="flex items-center gap-3 font-mono font-bold text-3xl sm:text-4xl md:text-5xl mb-3 text-text-primary">
            <Plug className="text-git-purple shrink-0" size={30} />
            MCP Server
          </h1>
          <p className="text-text-muted text-sm sm:text-base font-mono max-w-2xl leading-relaxed">
            This portfolio runs a public, read-only{" "}
            <strong className="text-text-primary">Model Context Protocol</strong> server. Point your
            own AI assistant at it and ask whether I&apos;m a fit — it pulls real, current data
            straight from my portfolio instead of guessing.
          </p>
        </ScrollReveal>

        {/* What is MCP? */}
        <ScrollReveal delay={0.05}>
          <div className="mt-8 rounded-lg border border-terminal-border bg-terminal-window/50 p-4 font-mono text-sm text-text-muted leading-relaxed">
            <p className="mb-2 text-git-green"># what is this?</p>
            <p>
              MCP is an open standard that lets AI apps (like Claude Desktop) discover and call
              external <span className="text-text-primary">tools</span>. This server exposes my
              portfolio as read-only tools, so a recruiter&apos;s AI can evaluate me using live,
              accurate data. It&apos;s read-only and public — no account, nothing to install on my side.
            </p>
          </div>
        </ScrollReveal>

        {/* Demo */}
        <ScrollReveal delay={0.1}>
          <SectionHeading label="git show --demo" title="Demo" />
          <VideoSlot />
        </ScrollReveal>

        {/* Connect */}
        <ScrollReveal delay={0.1}>
          <SectionHeading label="git remote add mcp" title="Connect your AI" />

          <p className="mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">1.</span> Server endpoint (SSE transport):
          </p>
          <CopyRow value={SSE_URL} />

          <p className="mt-6 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">2.</span> Claude Desktop — add this to your MCP config
            (the <code className="text-git-blue">mcp-remote</code> bridge lets a desktop client reach
            the remote URL):
          </p>
          <TerminalWindow title="claude_desktop_config.json">
            <div className="flex items-start justify-between gap-3">
              <pre className="overflow-x-auto text-xs sm:text-sm text-text-primary whitespace-pre">
                {CLAUDE_CONFIG}
              </pre>
              <CopyButton value={CLAUDE_CONFIG} />
            </div>
          </TerminalWindow>

          <p className="mt-6 mb-3 font-mono text-sm text-text-muted">
            <span className="text-git-green">3.</span> Or inspect it directly with the{" "}
            <span className="text-text-primary">MCP Inspector</span> (point it at the endpoint above):
          </p>
          <CopyRow value={INSPECTOR_CMD} />
        </ScrollReveal>

        {/* Tools */}
        <ScrollReveal delay={0.1}>
          <SectionHeading label="git ls-tree --tools" title="Available tools" />
          <ul className="space-y-2 font-mono text-sm">
            {TOOLS.map(([name, desc]) => (
              <li
                key={name}
                className="flex flex-col sm:flex-row sm:items-baseline gap-1 sm:gap-3 rounded-md border border-terminal-border bg-terminal-window/40 px-3 py-2"
              >
                <span className="text-git-blue shrink-0">{name}</span>
                <span className="text-text-muted">{desc}</span>
              </li>
            ))}
          </ul>
        </ScrollReveal>

        {/* Example prompts */}
        <ScrollReveal delay={0.1}>
          <SectionHeading label="echo --try-asking" title="Try asking your AI" />
          <ul className="space-y-2 font-mono text-sm">
            {EXAMPLE_PROMPTS.map((p) => (
              <li key={p} className="flex items-start gap-2 text-text-muted">
                <ArrowRight size={14} className="mt-0.5 shrink-0 text-git-green" />
                <span>&ldquo;{p}&rdquo;</span>
              </li>
            ))}
          </ul>
        </ScrollReveal>

        {/* Security note */}
        <ScrollReveal delay={0.1}>
          <div className="mt-10 flex items-start gap-3 rounded-lg border border-git-green/20 bg-git-green/5 p-4 font-mono text-xs text-text-muted leading-relaxed">
            <ShieldCheck size={16} className="mt-0.5 shrink-0 text-git-green" />
            <p>
              <span className="text-git-green">read-only &amp; public by design.</span> Every tool
              returns only curated public portfolio data — no secrets, no private files. There are no
              write tools. Requests are rate-limited per IP, and a pasted job description is treated
              strictly as data to compare against, never as instructions.
            </p>
          </div>
        </ScrollReveal>
      </div>
    </section>
  );
}

function SectionHeading({ label, title }: { label: string; title: string }) {
  return (
    <div className="mt-10 mb-4">
      <div className="flex items-center gap-2 mb-1 font-mono text-xs text-text-faint">
        <span className="text-git-green">$</span>
        <span>{label}</span>
      </div>
      <h2 className="font-mono text-lg sm:text-xl font-bold text-text-primary">{title}</h2>
    </div>
  );
}

/** A read-only value (URL / command) in a terminal-styled row with a copy button. */
function CopyRow({ value }: { value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-terminal-border bg-terminal-bg px-3 py-2.5 font-mono text-xs sm:text-sm">
      <code className="overflow-x-auto whitespace-nowrap text-git-green">{value}</code>
      <CopyButton value={value} />
    </div>
  );
}

function CopyButton({ value, label }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  async function handle() {
    if (await copyToClipboard(value)) {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }
  return (
    <button
      type="button"
      onClick={handle}
      aria-label={label ?? "Copy to clipboard"}
      className="inline-flex shrink-0 items-center gap-1.5 rounded px-2 py-1 text-xs text-text-faint hover:text-text-muted hover:bg-terminal-border/30 transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-green/30"
    >
      {copied ? (
        <>
          <Check size={12} className="text-git-green" />
          <span className="text-git-green">Copied!</span>
        </>
      ) : (
        <>
          <Copy size={12} />
          <span>{label ?? "Copy"}</span>
        </>
      )}
    </button>
  );
}

/** Demo video: inline <video> for a direct file, a link-out otherwise, else a placeholder. */
function VideoSlot() {
  const isFile = /\.(mp4|webm|ogg)(\?|$)/i.test(DEMO_VIDEO_URL);

  if (DEMO_VIDEO_URL && isFile) {
    return (
      <video
        controls
        preload="metadata"
        className="w-full rounded-xl border border-terminal-border shadow-terminal"
      >
        <source src={DEMO_VIDEO_URL} />
        Your browser doesn&apos;t support embedded video — {/* eslint-disable-next-line */}
        <a href={DEMO_VIDEO_URL} className="text-git-blue underline">open the demo</a>.
      </video>
    );
  }

  if (DEMO_VIDEO_URL) {
    return (
      <a
        href={DEMO_VIDEO_URL}
        target="_blank"
        rel="noopener noreferrer"
        className="group flex items-center justify-center gap-3 rounded-xl border border-git-purple/30 bg-git-purple/5 py-10 font-mono text-sm text-git-purple hover:bg-git-purple/10 transition-colors"
      >
        <Play size={18} className="transition-transform group-hover:scale-110" />
        Watch the demo ↗
      </a>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-terminal-border bg-terminal-window/40 py-12 text-center font-mono text-sm text-text-faint">
      <Play size={22} className="opacity-50" />
      <span>Demo video coming soon.</span>
      <span className="text-xs">Meanwhile, connect your AI using the steps below.</span>
    </div>
  );
}
