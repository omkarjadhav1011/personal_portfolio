import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { GoogleGenerativeAI } from "@google/generative-ai";
import { getPortfolioContext } from "@/lib/chatbot/context";
import { checkRateLimit, clientIpFrom } from "@/lib/chatbot/rate-limit";
import { buildMatchPrompt, matchResponseSchema } from "@/lib/recruiter/prompt";
import { JD_MAX, JD_MIN, matchResultSchema } from "@/lib/recruiter/types";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const requestSchema = z.object({
  jobDescription: z.string().min(JD_MIN).max(JD_MAX),
});

const MODEL = "gemini-2.0-flash";
// Recruiter calls are heavier than chat — give them a slower-refilling bucket
// keyed separately so they don't share quota with the chatbot.
const RATE_LIMIT_PREFIX = "recruiter-match";

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
      {
        error: `Job description must be between ${JD_MIN} and ${JD_MAX} characters.`,
      },
      { status: 400 }
    );
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error("[recruiter] GEMINI_API_KEY is not set");
    return NextResponse.json(
      { error: "Recruiter mode is temporarily unavailable." },
      { status: 503 }
    );
  }

  let promptText: string;
  try {
    const ctx = await getPortfolioContext();
    promptText = buildMatchPrompt(ctx, parsed.data.jobDescription);
  } catch (err) {
    console.error("[recruiter] failed to load portfolio context:", err);
    return NextResponse.json(
      { error: "Recruiter mode is temporarily unavailable." },
      { status: 503 }
    );
  }

  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({
      model: MODEL,
      generationConfig: {
        responseMimeType: "application/json",
        responseSchema: matchResponseSchema,
        maxOutputTokens: 2048,
        temperature: 0.4,
      },
    });

    const result = await model.generateContent(promptText);
    const text = result.response.text();

    let json: unknown;
    try {
      json = JSON.parse(text);
    } catch {
      console.error("[recruiter] non-JSON model output:", text.slice(0, 300));
      return NextResponse.json(
        { error: "The model returned an unexpected response." },
        { status: 502 }
      );
    }

    const validated = matchResultSchema.safeParse(json);
    if (!validated.success) {
      console.error(
        "[recruiter] schema validation failed:",
        validated.error.flatten()
      );
      return NextResponse.json(
        { error: "The model returned an unexpected response." },
        { status: 502 }
      );
    }

    return NextResponse.json(validated.data);
  } catch (err) {
    console.error("[recruiter] generation error:", err);
    return NextResponse.json(
      { error: "Recruiter mode ran into a problem." },
      { status: 500 }
    );
  }
}
