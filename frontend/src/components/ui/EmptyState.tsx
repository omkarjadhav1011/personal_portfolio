import { cn } from "@/lib/utils";

interface EmptyStateProps {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  className?: string;
}

export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3 py-12 text-center", className)}>
      {icon && (
        <div className="text-text-faint" aria-hidden="true">
          {icon}
        </div>
      )}
      <div className="space-y-1">
        <p className="font-mono text-sm text-text-muted">{title}</p>
        {description && (
          <p className="font-mono text-xs text-text-faint">{description}</p>
        )}
      </div>
      {action && (
        <button
          onClick={action.onClick}
          className="mt-1 inline-flex items-center gap-1.5 rounded-lg border border-git-green-dim bg-git-green-muted px-3 py-1.5 font-mono text-xs text-git-green transition-colors hover:bg-git-green-dim/30"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
