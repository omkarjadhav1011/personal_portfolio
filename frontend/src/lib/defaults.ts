import type { CurrentRole } from "@/types";

export const DEFAULT_ACCENT = "#00ff88";

export const DEFAULT_ROLE: CurrentRole = {
  enabled: false,
  title: "",
  company: "",
  monogram: "",
  logoUrl: "",
  url: "",
  location: "",
  startedAt: "",
  tenure: "",
  accent: DEFAULT_ACCENT,
};
