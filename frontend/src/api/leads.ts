import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { MessageStatus } from "@/api/messages";

/**
 * Payload for POST /api/recruiter/lead. Only `email` is required; the match
 * context (fitScore, matchedSkills, jdExcerpt) is self-reported display data
 * the backend sanitizes and stores for the owner's follow-up only.
 */
export interface LeadPayload {
  email: string;
  company?: string;
  note?: string;
  fitScore?: number;
  matchedSkills?: string[];
  jdExcerpt?: string;
  honeypot?: string;
}

export interface LeadResponse {
  success: boolean;
}

/** Public recruiter-lead submission (no auth; rate-limited server-side). */
export function useSubmitLead() {
  return useMutation({
    mutationFn: (payload: LeadPayload) =>
      apiFetch<LeadResponse>("/api/recruiter/lead", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  });
}

/** Admin-inbox view of a lead (C3). Status reuses the contact inbox's MessageStatus. */
export interface RecruiterLead {
  id: string;
  email: string;
  company: string | null;
  note: string | null;
  fitScore: number | null;
  matchedSkills: string[] | null;
  jdExcerpt: string | null;
  status: MessageStatus;
  createdAt: string;
}

/** Page envelope returned by GET /api/admin/leads. */
export interface LeadsPage {
  content: RecruiterLead[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const leadKeys = {
  all: ["admin-leads"] as const,
  list: (status?: MessageStatus) => ["admin-leads", status ?? "all"] as const,
};

function fetchLeads(status?: MessageStatus): Promise<LeadsPage> {
  const query = status ? `?status=${status}` : "";
  return apiFetch<LeadsPage>(`/api/admin/leads${query}`);
}

/** Admin leads list (ADMIN JWT attached by apiFetch), newest first. */
export function useLeads(status?: MessageStatus, enabled = true) {
  return useQuery({
    queryKey: leadKeys.list(status),
    queryFn: () => fetchLeads(status),
    enabled,
  });
}
