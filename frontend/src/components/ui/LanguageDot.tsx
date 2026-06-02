import { cn } from "@/lib/utils";

interface LanguageDotProps {
  language: string;
  color: string;
  className?: string;
  showLabel?: boolean;
}

export function LanguageDot({
  language,
  color,
  className,
  showLabel = true,
}: LanguageDotProps) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-xs text-text-muted", className)}>
      <span
        className="w-2.5 h-2.5 rounded-full shrink-0 ring-1 ring-black/20"
        style={{ backgroundColor: color }}
      />
      {showLabel && <span className="font-mono">{language}</span>}
    </span>
  );
}
