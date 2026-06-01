
import { cn } from "@/lib/utils";
import { ButtonHTMLAttributes, forwardRef } from "react";

const variants = {
  primary:
    "border-git-green-dim bg-git-green-muted text-git-green hover:bg-git-green-dim/30",
  danger:
    "border-git-red/40 bg-git-red/10 text-git-red hover:bg-git-red/20",
  ghost:
    "border-terminal-border bg-terminal-surface text-text-secondary hover:bg-terminal-border/30",
} as const;

interface LoadingButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean;
  loadingText?: string;
  variant?: keyof typeof variants;
}

export const LoadingButton = forwardRef<HTMLButtonElement, LoadingButtonProps>(
  ({ loading, loadingText, variant = "primary", className, children, disabled, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={cn(
          "inline-flex items-center justify-center gap-2 rounded-lg border px-3 py-1.5 font-mono text-xs transition-all duration-200",
          "disabled:opacity-50 disabled:cursor-not-allowed",
          variants[variant],
          className
        )}
        {...props}
      >
        {loading && (
          <span
            className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
            aria-hidden="true"
          />
        )}
        {loading && loadingText ? loadingText : children}
      </button>
    );
  }
);

LoadingButton.displayName = "LoadingButton";
