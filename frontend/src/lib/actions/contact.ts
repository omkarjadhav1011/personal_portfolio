import { apiFetch, ApiError } from "@/lib/api";

export interface ContactActionResult {
  success: boolean;
  message: string;
}

/**
 * Submits the contact form to the backend (POST /api/contact). Replaces the Next.js
 * server action. The endpoint returns {success, message} with HTTP 200 even for
 * validation failures / bot drops; non-2xx (e.g. 429 rate limit) surfaces as an error.
 */
export async function sendContactEmail(formData: FormData): Promise<ContactActionResult> {
  const payload = {
    name: String(formData.get("name") ?? ""),
    email: String(formData.get("email") ?? ""),
    message: String(formData.get("message") ?? ""),
    honeypot: String(formData.get("honeypot") ?? ""),
  };

  try {
    return await apiFetch<ContactActionResult>("/api/contact", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  } catch (err) {
    return {
      success: false,
      message: err instanceof ApiError ? err.message : "Network error — please try again.",
    };
  }
}
