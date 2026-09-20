import type { SocialLink } from "@/types";

/**
 * The booking link (lead capture F1) is just a social the owner adds via the admin profile
 * editor (label "book a call", Cal.com URL) — no schema, no migration. This finds it so the
 * UI can feature it prominently; absent → callers render nothing (no dead button).
 */
export function findBookingLink(socials: SocialLink[]): SocialLink | undefined {
  return socials.find((s) => /book/i.test(s.label) || /cal\.com\//i.test(s.url));
}
