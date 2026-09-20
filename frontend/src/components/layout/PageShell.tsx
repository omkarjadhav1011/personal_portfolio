import type { ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { Breadcrumbs } from "@/components/layout/Breadcrumbs";

interface PageShellProps {
  /** The page's single <h1>. Should carry the name and the primary keyword. */
  title: ReactNode;
  /** Terminal-styled command shown above the heading, e.g. "git log --experience". */
  command?: string;
  /** One or two sentences under the heading. Name the entity — do not open with a pronoun. */
  intro?: ReactNode;
  /** Overrides the final breadcrumb label where the URL segment is not the reader's word for it. */
  leafName?: string;
  children: ReactNode;
}

/**
 * Chrome shared by every dedicated content page: breadcrumb trail, the single
 * <h1>, and an intro paragraph.
 *
 * Exists because Wave 3 adds five pages that all need the same three things
 * done correctly, and "correctly" here is load-bearing — exactly one <h1> per
 * page, a visible breadcrumb the JSON-LD can legitimately mirror, and an
 * opening sentence that names the subject so the paragraph survives being
 * extracted from its page by an answer engine.
 */
export function PageShell({ title, command, intro, leafName, children }: PageShellProps) {
  const { pathname } = useLocation();

  return (
    <main className="min-h-screen pt-20 pb-16 px-4">
      <div className="max-w-4xl mx-auto">
        <Breadcrumbs route={pathname} leafName={leafName} />

        <header className="mt-6 mb-10">
          {command && (
            <div className="flex items-center gap-2 mb-3 font-mono text-sm text-text-muted">
              <span className="text-git-green">$</span>
              <span>{command}</span>
            </div>
          )}
          <h1 className="font-mono font-bold text-2xl sm:text-3xl md:text-4xl text-text-primary leading-tight">
            {title}
          </h1>
          {intro && (
            <p className="mt-4 max-w-2xl text-sm sm:text-base text-text-muted leading-relaxed">
              {intro}
            </p>
          )}
        </header>

        {children}
      </div>
    </main>
  );
}
