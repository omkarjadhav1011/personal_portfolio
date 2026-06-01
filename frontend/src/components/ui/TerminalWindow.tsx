
import { cn } from "@/lib/utils";

interface TerminalWindowProps {
  title?: string;
  children: React.ReactNode;
  className?: string;
  showDots?: boolean;
}

export function TerminalWindow({
  title = "bash — portfolio",
  children,
  className,
  showDots = true,
}: TerminalWindowProps) {
  return (
    <div
      className={cn(
        "rounded-xl overflow-hidden shadow-terminal border border-terminal-border",
        "bg-terminal-window font-mono text-sm",
        className
      )}
    >
      {/* Title bar */}
      <div className="flex items-center gap-2 px-4 py-3 bg-terminal-surface border-b border-terminal-border select-none">
        {showDots && (
          <div className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-full bg-[#ff5f57] block" />
            <span className="w-3 h-3 rounded-full bg-[#febc2e] block" />
            <span className="w-3 h-3 rounded-full bg-[#28c840] block" />
          </div>
        )}
        <span className="flex-1 text-center text-text-muted text-xs">{title}</span>
        <div className="w-[42px]" /> {/* spacer to center title */}
      </div>

      {/* Content */}
      <div className="p-3 sm:p-4 md:p-6 text-text-primary leading-relaxed overflow-x-auto">{children}</div>
    </div>
  );
}
