import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";

export type EngagementType =
  | "RESUME_DOWNLOAD"
  | "RECRUITER_MATCH"
  | "RECRUITER_LETTER"
  | "MCP_TOOL"
  | "CHAT_SESSION";

/** Shape of GET /api/admin/telemetry — see backend TelemetrySummary. */
export interface TelemetrySummary {
  days: number;
  total: number;
  byType: Partial<Record<EngagementType, number>>;
  avgFitScore: number | null;
  topTools: Array<{ tool: string; count: number }>;
  byDay: Array<{ date: string; count: number }>;
}

export const telemetryKeys = {
  summary: (days: number) => ["admin-telemetry", days] as const,
};

/** Engagement summary for the admin dashboard (ADMIN JWT attached by apiFetch). */
export function useTelemetry(days = 7) {
  return useQuery({
    queryKey: telemetryKeys.summary(days),
    queryFn: () => apiFetch<TelemetrySummary>(`/api/admin/telemetry?days=${days}`),
  });
}
