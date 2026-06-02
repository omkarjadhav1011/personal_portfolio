import { useEffect } from "react";

const SITE = "Omkar Jadhav";

/** Sets document.title for the current route (react-router has no built-in title management). */
export function useDocumentTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} | ${SITE}` : `${SITE} — Portfolio`;
  }, [title]);
}
