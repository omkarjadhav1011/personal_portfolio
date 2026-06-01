import { useCallback, useRef, useState } from "react";

export interface AIMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
}

// ─── SSE event types ──────────────────────────────────────────────────────

export type ChatEvent =
  | { type: "delta"; text: string }
  | { type: "done" }
  | { type: "error"; message: string };

// Pure parser, exported for testing. Feed it raw chunks of an SSE stream and
// it yields parsed events plus any leftover buffer to pass to the next call.
export function parseSseChunk(
  buffer: string,
  chunk: string
): { events: ChatEvent[]; buffer: string } {
  const combined = buffer + chunk;
  const segments = combined.split("\n\n");
  const remaining = segments.pop() ?? "";
  const events: ChatEvent[] = [];

  for (const segment of segments) {
    const line = segment.split("\n").find((l) => l.startsWith("data:"));
    if (!line) continue;
    const payload = line.slice(5).trim();
    if (!payload) continue;
    try {
      const parsed = JSON.parse(payload) as ChatEvent;
      if (parsed && typeof parsed === "object" && "type" in parsed) {
        events.push(parsed);
      }
    } catch {
      // Malformed line — skip.
    }
  }

  return { events, buffer: remaining };
}

// ─── Hook ─────────────────────────────────────────────────────────────────

const ERROR_FALLBACK = "Something went wrong — try again?";
const RATE_LIMITED = "You're sending messages a little fast. Try again in a moment.";
const NETWORK_ERROR = "Couldn't reach the assistant. Check your connection and retry.";

export function useAI() {
  const [messages, setMessages] = useState<AIMessage[]>([]);
  const [isTyping, setIsTyping] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const replaceAssistantWithError = useCallback(
    (assistantId: string, errMessage: string) => {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, content: errMessage }
            : m
        )
      );
      setError(errMessage);
    },
    []
  );

  const ask = useCallback(
    async (userInput: string) => {
      const trimmed = userInput.trim();
      if (!trimmed || isTyping) return;

      // Cancel any in-flight request before starting a new one
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      const userMsg: AIMessage = {
        id: `user-${Date.now()}`,
        role: "user",
        content: trimmed,
      };
      const assistantId = `ai-${Date.now()}`;
      const assistantMsg: AIMessage = {
        id: assistantId,
        role: "assistant",
        content: "",
      };

      setError(null);
      setIsTyping(true);

      // Snapshot the history we'll send before mutating state. Server caps at 10.
      const history = [...messages, userMsg].slice(-10).map(({ role, content }) => ({
        role,
        content,
      }));

      setMessages((prev) => [...prev, userMsg, assistantMsg]);

      try {
        const res = await fetch("/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ messages: history }),
          signal: controller.signal,
        });

        if (!res.ok) {
          const errMsg =
            res.status === 429
              ? RATE_LIMITED
              : res.status === 503
                ? "The assistant is temporarily unavailable."
                : ERROR_FALLBACK;
          replaceAssistantWithError(assistantId, errMsg);
          return;
        }

        if (!res.body) {
          replaceAssistantWithError(assistantId, ERROR_FALLBACK);
          return;
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        let sawError = false;

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          const parsed = parseSseChunk(buffer, chunk);
          buffer = parsed.buffer;

          for (const event of parsed.events) {
            if (event.type === "delta") {
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? { ...m, content: m.content + event.text }
                    : m
                )
              );
            } else if (event.type === "error") {
              sawError = true;
              replaceAssistantWithError(assistantId, ERROR_FALLBACK);
            }
            // "done" is implicit when the stream closes.
          }

          if (sawError) break;
        }
      } catch (err: unknown) {
        if (err instanceof Error && err.name === "AbortError") return;
        replaceAssistantWithError(assistantId, NETWORK_ERROR);
      } finally {
        if (abortRef.current === controller) {
          abortRef.current = null;
        }
        setIsTyping(false);
      }
    },
    [messages, isTyping, replaceAssistantWithError]
  );

  const clearChat = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setMessages([]);
    setError(null);
    setIsTyping(false);
  }, []);

  return { messages, isTyping, error, ask, clearChat };
}
