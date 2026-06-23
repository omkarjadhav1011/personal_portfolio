import { Link } from "react-router-dom";
import { Plug, ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Eye-catching, on-theme badge advertising the public MCP server. Links to the
 * dedicated `/mcp` page. Reused on the hero and the recruiter page for discoverability.
 * The pulsing dot respects `prefers-reduced-motion` (Tailwind `motion-reduce:animate-none`).
 */
export function McpBadge({ className }: { className?: string }) {
  return (
    <Link
      to="/mcp"
      className={cn(
        "group inline-flex items-center gap-2 rounded-full border border-git-purple/40",
        "bg-git-purple/10 px-3 py-1.5 font-mono text-xs text-git-purple",
        "hover:bg-git-purple/20 hover:border-git-purple/70 transition-colors duration-150",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-git-purple/40",
        className,
      )}
    >
      <span className="relative flex h-2 w-2">
        <span className="absolute inline-flex h-full w-full rounded-full bg-git-purple/60 animate-ping motion-reduce:animate-none" />
        <span className="relative inline-flex h-2 w-2 rounded-full bg-git-purple" />
      </span>
      <Plug size={13} className="opacity-80" />
      <span className="font-semibold">Try it with your own AI</span>
      <span className="hidden sm:inline text-git-purple/70">— MCP server</span>
      <ArrowRight
        size={13}
        className="transition-transform duration-150 group-hover:translate-x-0.5"
      />
    </Link>
  );
}
