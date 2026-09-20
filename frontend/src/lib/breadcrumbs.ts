/**
 * The breadcrumb trail for a route — one definition, used twice.
 *
 * `PageShell` renders it as a visible <nav>, and `buildGraph` turns the same
 * array into the JSON-LD `BreadcrumbList`. That is deliberate: structured data
 * may only describe what a reader can see, and the surest way to satisfy that
 * is to make the markup and the schema read from the same source rather than
 * trusting two hand-written lists to stay in step.
 */
export interface Crumb {
  name: string;
  /** Site-relative path. The final crumb is the current page and is not a link. */
  path: string;
}

const LABELS: Record<string, string> = {
  about: "About",
  projects: "Projects",
  experience: "Experience",
  education: "Education",
  resume: "Resume",
  recruiter: "Recruiter Fit Match",
  mcp: "MCP Server",
};

/**
 * @param route  the site-relative route, e.g. "/projects/expense-tracker"
 * @param leafName  overrides the label of the last crumb — used for project
 *                  pages, where the slug is not the name a reader would expect
 */
export function crumbsFor(route: string, leafName?: string): Crumb[] {
  const segments = route.split("/").filter(Boolean);
  if (segments.length === 0) return [];

  const crumbs: Crumb[] = [{ name: "Home", path: "/" }];
  let path = "";

  segments.forEach((segment, index) => {
    path += `/${segment}`;
    const isLast = index === segments.length - 1;
    crumbs.push({
      name: isLast && leafName ? leafName : (LABELS[segment] ?? segment),
      path,
    });
  });

  return crumbs;
}
