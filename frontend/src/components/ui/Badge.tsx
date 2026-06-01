import { cn } from "@/lib/utils";

interface BadgeProps {
  children: React.ReactNode;
  variant?: "branch" | "tag" | "language" | "status";
  color?: string;
  className?: string;
}

export function Badge({
  children,
  variant = "tag",
  color,
  className,
}: BadgeProps) {
  const base =
    "inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-mono font-medium border";

  const variants: Record<string, string> = {
    branch:
      "border-git-blue/40 bg-git-blue/10 text-git-blue before:content-['⑂_'] before:opacity-60",
    tag: "border-git-orange/40 bg-git-orange/10 text-git-orange before:content-['#'] before:text-[10px] before:opacity-60 before:mr-0.5",
    language: "border-terminal-border bg-terminal-surface text-text-secondary",
    status: "border-git-green/40 bg-git-green/10 text-git-green",
  };

  return (
    <span
      className={cn(base, variants[variant], className)}
      style={color ? { borderColor: `${color}40`, color, backgroundColor: `${color}15` } : {}}
    >
      {children}
    </span>
  );
}
