import { ExternalLink as ExternalLinkIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { AnchorHTMLAttributes } from "react";

interface ExternalLinkProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  showIcon?: boolean;
}

export function ExternalLink({
  children,
  showIcon = true,
  className,
  ...props
}: ExternalLinkProps) {
  return (
    <a
      target="_blank"
      rel="noopener noreferrer"
      className={cn("inline-flex items-center gap-1", className)}
      {...props}
    >
      {children}
      {showIcon && <ExternalLinkIcon size={10} className="opacity-50" aria-hidden="true" />}
    </a>
  );
}
