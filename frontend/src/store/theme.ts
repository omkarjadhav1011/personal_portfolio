import { create } from "zustand";

type Theme = "light" | "dark" | "system";

interface ThemeStore {
  theme: Theme;
  resolved: "light" | "dark";
  setTheme: (t: Theme) => void;
}

function getSystemTheme(): "light" | "dark" {
  if (typeof window === "undefined") return "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

function resolve(theme: Theme): "light" | "dark" {
  return theme === "system" ? getSystemTheme() : theme;
}

function applyTheme(resolved: "light" | "dark") {
  const root = document.documentElement;
  root.classList.add("theme-transition");
  if (resolved === "dark") {
    root.classList.add("dark");
  } else {
    root.classList.remove("dark");
  }
  setTimeout(() => root.classList.remove("theme-transition"), 200);
}

export const useThemeStore = create<ThemeStore>((set) => ({
  theme: "system",
  resolved: "dark",
  setTheme: (theme) => {
    const resolved = resolve(theme);
    localStorage.setItem("theme", theme);
    applyTheme(resolved);
    set({ theme, resolved });
  },
}));

/** Call once on client mount to sync store with localStorage / system pref. */
export function initTheme() {
  const stored = localStorage.getItem("theme") as Theme | null;
  const theme: Theme =
    stored === "light" || stored === "dark" || stored === "system"
      ? stored
      : "system";
  const resolved = resolve(theme);

  // Apply without transition on first load (FOUC script already set the class)
  const root = document.documentElement;
  if (resolved === "dark") {
    root.classList.add("dark");
  } else {
    root.classList.remove("dark");
  }

  useThemeStore.setState({ theme, resolved });

  // Listen for OS-level preference changes when in "system" mode
  const mql = window.matchMedia("(prefers-color-scheme: dark)");
  mql.addEventListener("change", () => {
    const { theme: current } = useThemeStore.getState();
    if (current === "system") {
      const newResolved = getSystemTheme();
      applyTheme(newResolved);
      useThemeStore.setState({ resolved: newResolved });
    }
  });
}
