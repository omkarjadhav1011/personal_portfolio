import { Link } from "react-router-dom";
import { crumbsFor } from "@/lib/breadcrumbs";

interface BreadcrumbsProps {
  /** Site-relative route. Pass the resolved path, not a pattern. */
  route: string;
  /** Overrides the last crumb's label where the URL segment is not the reader's word for it. */
  leafName?: string;
  className?: string;
}

/**
 * The visible breadcrumb trail.
 *
 * Built from `crumbsFor`, which the JSON-LD `BreadcrumbList` also reads, so the
 * markup and the structured data cannot drift apart — schema may only describe
 * what a reader can actually see.
 *
 * Real `<a>` elements rather than click handlers: a crawler follows links, and
 * these are part of how the dedicated pages stay reachable with JS disabled.
 */
export function Breadcrumbs({ route, leafName, className }: BreadcrumbsProps) {
  const crumbs = crumbsFor(route, leafName);
  if (crumbs.length <= 1) return null;

  return (
    <nav aria-label="Breadcrumb" className={className ?? "font-mono text-xs text-text-faint"}>
      <ol className="flex flex-wrap items-center gap-1.5">
        {crumbs.map((crumb, index) => {
          const isLast = index === crumbs.length - 1;
          return (
            <li key={crumb.path} className="flex items-center gap-1.5">
              {index > 0 && <span aria-hidden="true">/</span>}
              {isLast ? (
                <span className="text-text-muted" aria-current="page">
                  {crumb.name}
                </span>
              ) : (
                <Link to={crumb.path} className="hover:text-git-green transition-colors">
                  {crumb.name}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
