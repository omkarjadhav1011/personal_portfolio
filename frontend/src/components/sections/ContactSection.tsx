
import { useState, useRef, type FormEvent } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ExternalLink, Copy, Check } from "lucide-react";
import { ScrollReveal } from "@/components/ui/ScrollReveal";
import { TerminalWindow } from "@/components/ui/TerminalWindow";
import { sendContactEmail } from "@/lib/actions/contact";
import { copyToClipboard } from "@/lib/clipboard";
import { profile } from "@/data/profile";
import type { ContactFormState } from "@/types";

export function ContactSection() {
  const [state, setState] = useState<ContactFormState>({ status: "idle" });
  const [copiedEmail, setCopiedEmail] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  async function handleCopyEmail() {
    const ok = await copyToClipboard(profile.email);
    if (ok) {
      setCopiedEmail(true);
      setTimeout(() => setCopiedEmail(false), 2000);
    }
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    // Next.js used a function form `action`; under React 18 we submit via onSubmit.
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    setState({ status: "loading" });
    const result = await sendContactEmail(formData);
    if (result.success) {
      setState({ status: "success", message: result.message });
      formRef.current?.reset();
    } else {
      setState({ status: "error", message: result.message });
    }
  }

  return (
    <section id="contact" className="py-16 sm:py-24 px-4 scroll-mt-14">
      <div className="max-w-2xl mx-auto">
        <ScrollReveal>
          <div className="flex items-center gap-3 mb-4">
            <span className="text-git-green font-mono text-sm">$</span>
            <span className="font-mono text-text-muted text-sm">
              git remote add origin — contact me
            </span>
          </div>
          <h2 className="text-xl sm:text-2xl md:text-3xl font-bold font-mono text-text-primary mb-2">
            Get in Touch
          </h2>
          <p className="text-text-muted text-sm font-mono mb-10">
            # open to internships, collaborations, and interesting problems
          </p>
        </ScrollReveal>

        <ScrollReveal delay={0.1}>
          <TerminalWindow title={`${profile.handle}@portfolio: ~/contact`}>
            <form ref={formRef} onSubmit={handleSubmit} className="space-y-5">
              {/* Honeypot — hidden from real users, filled by bots */}
              <input
                type="text"
                name="honeypot"
                tabIndex={-1}
                aria-hidden="true"
                className="absolute opacity-0 pointer-events-none h-0"
                autoComplete="off"
              />

              {/* Name */}
              <TerminalField
                label="--name"
                name="name"
                type="text"
                placeholder="Your Name"
                required
                disabled={state.status === "loading" || state.status === "success"}
              />

              {/* Email */}
              <TerminalField
                label="--email"
                name="email"
                type="email"
                placeholder="your@email.com"
                required
                disabled={state.status === "loading" || state.status === "success"}
              />

              {/* Message */}
              <div className="space-y-1">
                <div className="flex items-center gap-2 font-mono text-sm">
                  <span className="text-git-green">$</span>
                  <span className="text-git-blue">--message</span>
                </div>
                <textarea
                  name="message"
                  required
                  rows={4}
                  placeholder="What's on your mind..."
                  disabled={state.status === "loading" || state.status === "success"}
                  className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-4 py-3 font-mono text-sm text-text-primary placeholder-text-faint outline-none focus:border-git-green/60 focus-visible:ring-1 focus-visible:ring-git-green/30 transition-colors resize-none disabled:opacity-50"
                />
              </div>

              {/* Submit */}
              <div className="pt-2">
                <button
                  type="submit"
                  disabled={state.status === "loading" || state.status === "success"}
                  className="w-full flex items-center justify-center gap-2 px-4 sm:px-6 py-2.5 sm:py-3 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green font-mono text-xs sm:text-sm hover:bg-git-green/20 hover:border-git-green/70 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 overflow-hidden"
                >
                  {state.status === "loading" ? (
                    <>
                      <span className="animate-pulse">●</span>
                      <span>git send-email — sending...</span>
                    </>
                  ) : state.status === "success" ? (
                    <span>✓ Message delivered</span>
                  ) : (
                    <>
                      <span className="text-text-muted">$</span>
                      <span className="truncate">git send-email --to={`"${profile.handle}"`}</span>
                    </>
                  )}
                </button>
              </div>

              {/* Status output */}
              <AnimatePresence>
                {(state.status === "success" || state.status === "error") && (
                  <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
                    className={`p-3 rounded-lg border font-mono text-sm ${
                      state.status === "success"
                        ? "border-git-green/30 bg-git-green/5 text-git-green"
                        : "border-git-red/30 bg-git-red/5 text-git-red"
                    }`}
                  >
                    <span className="mr-2">
                      {state.status === "success" ? "✓" : "✗"}
                    </span>
                    {state.status === "success"
                      ? `remote: ${state.message}`
                      : `error: ${state.message}`}
                  </motion.div>
                )}
              </AnimatePresence>
            </form>

            {/* Direct links */}
            <div className="mt-8 pt-6 border-t border-terminal-border space-y-2 font-mono text-sm">
              <div className="flex items-center justify-between mb-3">
                <p className="text-text-muted text-xs"># or reach me directly:</p>
                <button
                  onClick={handleCopyEmail}
                  className="flex items-center gap-1.5 px-2 py-1 rounded text-xs text-text-faint hover:text-text-muted hover:bg-terminal-border/30 transition-colors"
                >
                  {copiedEmail ? (
                    <>
                      <Check size={12} className="text-git-green" />
                      <span className="text-git-green">Copied!</span>
                    </>
                  ) : (
                    <>
                      <Copy size={12} />
                      <span>Copy email</span>
                    </>
                  )}
                </button>
              </div>
              {profile.socials.map((s) => (
                <a
                  key={s.label}
                  href={s.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2 text-text-muted hover:text-git-blue transition-colors cursor-pointer"
                >
                  <span className="text-git-green">→</span>
                  {s.label}
                  <ExternalLink size={10} className="opacity-40" />
                </a>
              ))}
            </div>
          </TerminalWindow>
        </ScrollReveal>
      </div>
    </section>
  );
}

function TerminalField({
  label,
  name,
  type,
  placeholder,
  required,
  disabled,
}: {
  label: string;
  name: string;
  type: string;
  placeholder: string;
  required?: boolean;
  disabled?: boolean;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2 font-mono text-sm">
        <span className="text-git-green">$</span>
        <span className="text-git-blue">{label}</span>
      </div>
      <input
        type={type}
        name={name}
        required={required}
        disabled={disabled}
        placeholder={placeholder}
        className="w-full bg-terminal-bg border border-terminal-border rounded-lg px-4 py-2.5 font-mono text-sm text-text-primary placeholder-text-faint outline-none focus:border-git-green/60 focus-visible:ring-1 focus-visible:ring-git-green/30 transition-colors disabled:opacity-50"
      />
    </div>
  );
}
