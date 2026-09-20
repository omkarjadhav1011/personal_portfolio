import { Suspense, lazy, useEffect, useRef } from "react";
import { useCommandPaletteStore } from "@/store/commandPalette";

const CommandPalette = lazy(() =>
  import("@/components/layout/CommandPalette").then((m) => ({ default: m.CommandPalette })),
);

/**
 * Keeps the Ctrl+K shortcut working while the palette itself stays out of the
 * initial bundle.
 *
 * `CommandPalette` is not a small component: it carries the terminal emulator,
 * the AI chat UI, a markdown renderer, and — through `useTerminal` — every
 * static content file in `src/data`. All of that used to ship on first paint of
 * every public page, for a feature most visitors never open.
 *
 * It cannot simply be lazy-loaded where it stood, because it also owned the
 * global keydown listener: deferring the module would have deferred the
 * shortcut with it. So the listener lives here, in a component small enough to
 * stay eager, and the heavy chunk is fetched the first time the palette is
 * actually opened.
 *
 * Once opened it stays mounted. Unmounting on close would be tidier, but it
 * would also throw away the conversation the visitor is in the middle of.
 */
export function CommandPaletteHost() {
  const open = useCommandPaletteStore((state) => state.open);
  const hasOpened = useRef(false);

  if (open) hasOpened.current = true;

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key === "k") {
        event.preventDefault();
        useCommandPaletteStore.getState().toggle();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!hasOpened.current) return null;

  // No fallback: the palette animates itself in, and a spinner flashing behind
  // it would be worse than the brief nothing while the chunk arrives.
  return (
    <Suspense fallback={null}>
      <CommandPalette />
    </Suspense>
  );
}
