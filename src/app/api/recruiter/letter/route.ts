import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { getPortfolioContext } from "@/lib/chatbot/context";
import { checkRateLimit, clientIpFrom } from "@/lib/chatbot/rate-limit";
import { SSE_HEADERS, encodeSseEvent } from "@/lib/chatbot/sse";
import { buildLetterPrompt } from "@/lib/recruiter/prompt";
import { JD_MAX, JD_MIN, matchResultSchema } from "@/lib/recruiter/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const requestSchema = z.object({
  jobDescription: z.string().min(JD_MIN).max(JD_MAX),
  matchResult: matchResultSchema,
});

const MODEL = "gemini-2.0-flash";
const MAX_TOKENS = 512;
const RATE_LIMIT_PREFIX = "recruiter-letter";

export async function POST(request: NextRequest) {
  const ip = clientIpFrom(request.headers);
  const limit = checkRateLimit(`${RATE_LIMIT_PREFIX}:${ip}`);
  if (!limit.ok) {
    return NextResponse.json(
      { error: "Too many submissions. Try again in a minute." },
      {
        status: 429,
        headers: { "Retry-After": String(limit.retryAfterSeconds ?? 60) },
      }
    );
  }

  let parsed;
  try {
    const body = await request.json();
    parsed = requestSchema.safeParse(body);
  } catch {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }
  if (!parsed.success) {
    return NextResponse.json(
      { error: "Invalid request payload" },
      { status: 400 }
    );
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return NextResponse.json(
      { error: "Recruiter mode is temporarily unavailable." },
      { status: 503 }
    );
  }

  let systemPrompt: string;
  try {
    const ctx = await getPortfolioContext();
    systemPrompt = buildLetterPrompt(
      ctx,
      parsed.data.jobDescription,
      parsed.data.matchResult
    );
  } catch (err) {
    console.error("[recruiter-letter] failed to load context:", err);
    return NextResponse.json(
      { error: "Recruiter mode is temporarily unavailable." },
      { status: 503 }
    );
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({
    model: MODEL,
    generationConfig: { maxOutputTokens: MAX_TOKENS, temperature: 0.7 },
  });

  const abortController = new AbortController();
  request.signal.addEventListener("abort", () => abortController.abort());

  const readable = new ReadableStream<Uint8Array>({
    async start(controller) {
      try {
        const result = await model.generateContentStream(systemPrompt);
        for await (const chunk of result.stream) {
          if (abortController.signal.aborted) break;
          const text = chunk.text();
          if (text) {
            controller.enqueue(encodeSseEvent({ type: "delta", text }));
          }
        }
        controller.enqueue(encodeSseEvent({ type: "done" }));
        controller.close();
      } catch (err: unknown) {
        if (
          (err instanceof Error && err.name === "AbortError") ||
          abortController.signal.aborted
        ) {
          try {
            controller.close();
          } catch {
            /* already closed */
          }
          return;
        }
        console.error("[recruiter-letter] streaming error:", err);
        try {
          controller.enqueue(
            encodeSseEvent({
              type: "error",
              message: "The cover letter ran into a problem.",
            })
          );
          controller.close();
        } catch {
          /* already closed */
        }
      }
    },
    cancel() {
      abortController.abort();
    },
  });

  return new Response(readable, { headers: SSE_HEADERS });
}
