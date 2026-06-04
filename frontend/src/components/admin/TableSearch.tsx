
import { Search } from "lucide-react";
import { cn } from "@/lib/utils";

interface TableSearchProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

export function TableSearch({ value, onChange, placeholder = "Search...", className }: TableSearchProps) {
  return (
    <div className={cn("relative", className)}>
      <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-faint" />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full rounded-lg border border-terminal-border bg-terminal-bg py-2 pl-9 pr-3 font-mono text-xs text-text-primary placeholder:text-text-faint focus:border-git-blue/50 outline-none focus-visible:ring-1 focus-visible:ring-git-blue/30 transition-colors"
      />
    </div>
  );
}
