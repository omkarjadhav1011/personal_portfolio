
import { ChevronUp, ChevronDown, ChevronsUpDown } from "lucide-react";
import { cn } from "@/lib/utils";

interface SortableHeaderProps {
  label: string;
  sortKey: string;
  currentKey: string;
  currentDir: "asc" | "desc";
  onSort: () => void;
  className?: string;
}

export function SortableHeader({
  label,
  sortKey,
  currentKey,
  currentDir,
  onSort,
  className,
}: SortableHeaderProps) {
  const isActive = sortKey === currentKey;
  return (
    <th
      className={cn(
        "cursor-pointer select-none px-3 py-2 text-left font-mono text-xs text-text-muted hover:text-text-primary transition-colors",
        className
      )}
      onClick={onSort}
    >
      <span className="inline-flex items-center gap-1">
        {label}
        {isActive ? (
          currentDir === "asc" ? (
            <ChevronUp size={12} className="text-git-green" />
          ) : (
            <ChevronDown size={12} className="text-git-green" />
          )
        ) : (
          <ChevronsUpDown size={10} className="opacity-30" />
        )}
      </span>
    </th>
  );
}
