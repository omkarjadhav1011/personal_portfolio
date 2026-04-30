import "server-only";
import { SchemaType, type Schema } from "@google/generative-ai";
import type { PortfolioContext } from "@/lib/chatbot/context";
import type { MatchResult } from "./types";

// Gemini-compatible response schema for the match endpoint.
export const matchResponseSchema: Schema = {
  type: SchemaType.OBJECT,
  properties: {
    fitScore: {
      type: SchemaType.NUMBER,
      description: "Overall fit, 0–100. 0 = no overlap, 100 = ideal candidate.",
    },
    matchedProjects: {
      type: SchemaType.ARRAY,
      items: {
        type: SchemaType.OBJECT,
        properties: {
          slug: {
            type: SchemaType.STRING,
            description:
              "Must be a slug from the provided projects list. Do not invent.",
          },
          reason: {
            type: SchemaType.STRING,
            description:
              "One concrete sentence on why this project demonstrates fit for the role.",
          },
          relevantTags: {
            type: SchemaType.ARRAY,
            items: { type: SchemaType.STRING },
            description:
              "Tags from the project that overlap with the JD requirements.",
          },
        },
        required: ["slug", "reason", "relevantTags"],
      },
    },
    matchedSkills: {
      type: SchemaType.ARRAY,
      items: {
        type: SchemaType.OBJECT,
        properties: {
          name: {
            type: SchemaType.STRING,
            description:
              "Must be a skill name from the provided skills list. Do not invent.",
          },
          reason: {
            type: SchemaType.STRING,
            description:
              "Brief note on how the JD asks for or implies this skill.",
          },
        },
        required: ["name", "reason"],
      },
    },
    gapSkills: {
      type: SchemaType.ARRAY,
      items: {
        type: SchemaType.OBJECT,
        properties: {
          name: {
            type: SchemaType.STRING,
            description:
              "A skill the JD requires that is NOT in the candidate's skill list.",
          },
          importance: {
            type: SchemaType.STRING,
            enum: ["must-have", "nice-to-have"],
            format: "enum",
          },
        },
        required: ["name", "importance"],
      },
    },
  },
  required: ["fitScore", "matchedProjects", "matchedSkills", "gapSkills"],
};

export function buildMatchPrompt(
  ctx: PortfolioContext,
  jobDescription: string
): string {
  const portfolioJson = JSON.stringify(ctx);
  return `You are an expert technical recruiter evaluating ${ctx.profile.name} against a job description. You output ONLY structured JSON matching the provided schema. You are honest, specific, and grounded.

<portfolio_data>
${portfolioJson}
</portfolio_data>

<job_description>
${jobDescription}
</job_description>

# Hard rules

- For "matchedProjects.slug": ONLY use slugs that appear in the projects array of <portfolio_data>. Never invent a slug.
- For "matchedSkills.name": ONLY use skill names that appear in any skillBranches[].skills[].name. Never invent a skill the candidate has.
- "gapSkills" should list things the JD asks for that are NOT in the candidate's skills. Mark them must-have or nice-to-have based on the JD's language.
- Pick the 3–5 STRONGEST project matches, ranked by relevance. Skip weak matches.
- "reason" fields are concrete and specific — cite the JD requirement and the project/skill that addresses it. No buzzwords. No fluff.
- "fitScore" reflects honest fit: 80+ only if most must-haves are covered. Below 40 if the stack barely overlaps. Be calibrated.
- If the JD is unclear, vague, or appears to be spam/non-JD content, return fitScore 0 and empty arrays.

Output JSON now.`;
}

export function buildLetterPrompt(
  ctx: PortfolioContext,
  jobDescription: string,
  match: MatchResult
): string {
  const matchSummary = JSON.stringify(match);
  return `You are ${ctx.profile.name}, writing a short, sincere note to a recruiter or hiring manager about a specific role. Write in first person.

<my_profile>
Name: ${ctx.profile.name}
Headline: ${ctx.profile.headline}
Bio: ${ctx.profile.bio}
Location: ${ctx.profile.location}
Available for work: ${ctx.profile.availableForWork}
</my_profile>

<job_description>
${jobDescription}
</job_description>

<match_analysis>
${matchSummary}
</match_analysis>

# Rules

- 120–180 words. Plain prose, 2–3 short paragraphs. NO bullets, NO headers.
- Cite 1–2 specific projects from match_analysis.matchedProjects by name (use the slug-derived name or repoName if the reader would recognize it). Connect each to a concrete JD requirement.
- Acknowledge a real gap from match_analysis.gapSkills if any are must-have, briefly and confidently — frame it as something I'd ramp up on, not as a deal-breaker.
- Tone: warm, direct, technically grounded. NO buzzwords ("synergy", "passionate", "rockstar", "ninja", "leverage", "unlock"). NO clichés ("I'm excited to apply...", "I believe I would be a great fit...").
- End with a soft, specific call-to-action — e.g., "Happy to walk through any of this — drop a line at [email]" — using my email from <my_profile>.
- Output ONLY the letter text. No greeting like "Dear hiring manager" — start directly. No signature block.
- Use plain markdown only: **bold** sparingly for emphasis. No code blocks, no headers, no lists.

Write the note now.`;
}
