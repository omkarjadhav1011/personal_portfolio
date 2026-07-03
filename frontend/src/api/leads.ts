import { useMutation } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";

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
