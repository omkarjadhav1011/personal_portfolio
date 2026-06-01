export interface ContactActionResult {
  success: boolean;
  message: string;
}

/**
 * STUB for the former Next.js server action (`@/app/actions/contact`).
 * The real delivery path is the backend POST /api/contact endpoint; wiring it
 * (with validation + the API client) is deferred to a later phase. For now this
 * keeps ContactSection compiling.
 */
export async function sendContactEmail(
  _formData: FormData,
): Promise<ContactActionResult> {
  return {
    success: false,
    message: "Contact form is not wired to the backend yet (stub).",
  };
}
