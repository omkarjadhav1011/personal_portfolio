"use client";

import { useEffect, useRef, useState } from "react";
import { Mail } from "lucide-react";
import { InlineMarkdown } from "@/components/ui/InlineMarkdown";
import { parseSseChunk } from "@/hooks/useAI";
import type { MatchResult } from "@/lib/recruiter/types";

interface CoverLetterStreamProps {
  jobDescription: string;
  matchResult: MatchResult;
}

const NETWORK_ERROR =
  "Couldn't reach the cover-letter generator. Check your connection and retry.";
const RATE_LIMITED = "Cover letter rate-limited. Try again in a minute.";
const GENERIC_ERROR = "The cover letter ran into a problem.";

export function CoverLetterStream({
  jobDescription,
  matchResult,
}: CoverLetterStreamProps) {
  const [content, setContent] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ranRef = useRef(false);

  useEffect(() => {
    // Strict mode runs effects twice in dev — guard so we only kick off one
    // request per mount. The component is also remounted whenever the parent
    // gets a new matchResult, which is exactly when we want to re-fetch.
    if (ranRef.current) return;
    ranRef.current = true;

    const controller = new AbortController();
    let cancelled = false;

    (async () => {
      try {
        const res = await fetch("/api/recruiter/letter", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ jobDescription, matchResult }),
          signal: controller.signal,
        });

        if (!res.ok) {
          if (cancelled) return;
          setError(
            res.status === 429
              ? RATE_LIMITED
              : res.status === 503
                ? "Cover letter service is temporarily unavailable."
                : GENERIC_ERROR
          );
          return;
        }
        if (!res.body) {
          if (cancelled) return;
          setError(GENERIC_ERROR);
          return;
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        let sawError = false;

        while (true) {
          const { value, done: streamDone } = await reader.read();
          if (streamDone) break;

          const chunk = decoder.decode(value, { stream: true });
          const parsed = parseSseChunk(buffer, chunk);
          buffer = parsed.buffer;

          for (const event of parsed.events) {
            if (cancelled) break;
            if (event.type === "delta") {
              setContent((prev) => prev + event.text);
            } else if (event.type === "error") {
              sawError = true;
              setError(GENERIC_ERROR);
            }
          }
          if (sawError || cancelled) break;
        }

        if (!cancelled && !sawError) {
          setDone(true);
        }
      } catch (err: unknown) {
        if (cancelled) return;
        if (err instanceof Error && err.name === "AbortError") return;
        setError(NETWORK_ERROR);
      }
    })();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [jobDescription, matchResult]);

  return (
    <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2.5 bg-terminal-bg border-b border-terminal-border font-mono text-[11px] text-text-faint">
        <span className="flex items-center gap-2">
          <Mail size={11} className="text-git-green/70" />
          cover-letter.md
        </span>
        <span>{done ? "ready" : error ? "error" : "streaming…"}</span>
      </div>

      <div className="p-5 sm:p-6 text-sm sm:text-base leading-relaxed text-text-secondary font-sans whitespace-pre-wrap">
        {error ? (
          <span className="text-git-red font-mono text-xs">{error}</span>
        ) : content.length === 0 ? (
          <span className="text-text-faint font-mono text-xs">
            Drafting a tailored note based on the matches above…
          </span>
        ) : (
          <>
            <InlineMarkdown content={content} />
            {!done && (
              <span className="inline-block w-1.5 h-4 bg-git-green/70 align-middle ml-0.5 animate-cursor-blink" />
            )}
          </>
        )}
      </div>
    </div>
  );
}
