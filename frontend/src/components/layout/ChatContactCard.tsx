import { useState, type FormEvent } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { sendContactEmail } from "@/lib/actions/contact";

/** Session flag: once details are left via chat, don't ask again this browser session. */
const SENT_KEY = "chat-contact-sent";

/** True when the chip/form should never render again this session. */
export function chatContactAlreadySent(): boolean {
  return sessionStorage.getItem(SENT_KEY) === "1";
}

/**
 * Inline contact handoff inside the AI chat (lead capture E1): a "leave your details" chip
 * that expands into a compact form posting the existing /api/contact with source=CHATBOT —
 * the funnel is reused, not rebuilt. Rendered by CommandPalette after 3+ user messages.
 */
export function ChatContactCard() {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [error, setError] = useState<string>("");
  const shouldReduce = useReducedMotion();

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setStatus("loading");
    const result = await sendContactEmail(new FormData(e.currentTarget), "CHATBOT");
    if (result.success) {
      setStatus("success");
      sessionStorage.setItem(SENT_KEY, "1");
    } else {
      setStatus("error");
      setError(result.message);
    }
  }

  if (status === "success") {
    return (
      <p className="text-xs text-git-green font-mono" role="status">
        ✓ sent — Omkar will follow up by email
      </p>
    );
  }

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs bg-terminal-bg border border-terminal-border text-text-muted hover:text-git-green hover:border-git-green/40 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-green/40"
      >
        📨 leave your details
      </button>
    );
  }

  const busy = status === "loading";
  const inputCls =
    "w-full bg-terminal-bg border border-terminal-border rounded-md px-2.5 py-1.5 font-mono text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-green/60 focus-visible:ring-1 focus-visible:ring-git-green/30 transition-colors disabled:opacity-50";

  return (
    <motion.form
      initial={shouldReduce ? false : { opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.18 }}
      onSubmit={handleSubmit}
      className="rounded-lg border border-terminal-border bg-terminal-surface p-3 space-y-2"
    >
      <p className="text-2xs text-text-faint font-mono"># leave your details — Omkar will reach out</p>
      {/* Honeypot — hidden from real users, filled by bots */}
      <input
        type="text"
        name="honeypot"
        tabIndex={-1}
        aria-hidden="true"
        className="absolute opacity-0 pointer-events-none h-0"
        autoComplete="off"
      />
      <div className="flex gap-2">
        <input type="text" name="name" required minLength={2} maxLength={100} disabled={busy}
               placeholder="name" aria-label="Your name" className={inputCls} />
        <input type="email" name="email" required maxLength={255} disabled={busy}
               placeholder="your@email.com" aria-label="Your email" className={inputCls} />
      </div>
      <textarea
        name="message"
        required
        minLength={10}
        maxLength={2000}
        rows={2}
        disabled={busy}
        placeholder="what would you like to discuss? (min 10 chars)"
        aria-label="Your message"
        className={`${inputCls} resize-none`}
      />
      <div className="flex items-center gap-2">
        <button
          type="submit"
          disabled={busy}
          className="px-2.5 py-1 rounded-md border border-git-green/40 bg-git-green/10 text-git-green font-mono text-xs hover:bg-git-green/20 hover:border-git-green/70 disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
        >
          {busy ? "sending..." : "send"}
        </button>
        <button
          type="button"
          onClick={() => setOpen(false)}
          disabled={busy}
          className="text-2xs text-text-faint hover:text-text-muted transition-colors cursor-pointer"
        >
          cancel
        </button>
        {status === "error" && (
          <span className="text-2xs text-git-red font-mono" role="alert">✗ {error}</span>
        )}
      </div>
    </motion.form>
  );
}
