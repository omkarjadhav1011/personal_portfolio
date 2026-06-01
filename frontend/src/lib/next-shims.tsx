import type { AnchorHTMLAttributes, ReactNode } from "react";

/**
 * Minimal stand-ins for the Next.js APIs used by copied components.
 * Real client-side routing arrives in the app-shell phase; until then `Link`
 * renders a plain anchor and the router methods fall back to the browser.
 */

type LinkProps = { href: string; children?: ReactNode } & Omit<
  AnchorHTMLAttributes<HTMLAnchorElement>,
  "href"
>;

export function Link({ href, children, ...rest }: LinkProps) {
  return (
    <a href={href} {...rest}>
      {children}
    </a>
  );
}

export function useRouter() {
  return {
    push: (href: string) => window.location.assign(href),
    replace: (href: string) => window.location.replace(href),
    refresh: () => {},
    back: () => window.history.back(),
    forward: () => window.history.forward(),
    prefetch: () => {},
  };
}

export function usePathname(): string {
  return typeof window !== "undefined" ? window.location.pathname : "/";
}
