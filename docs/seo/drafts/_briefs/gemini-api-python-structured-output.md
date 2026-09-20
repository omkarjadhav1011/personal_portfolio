# Content brief: Structured output from the Gemini API in Python

- **slug:** `gemini-api-python-structured-output`
- **primary keyword:** gemini api python structured prompts
- **supporting:** gemini api python structured output, gemini response_schema python, google-genai response_mime_type application/json, gemini thinking budget truncated json, validate llm json output pydantic python, gemini api python retry malformed json, gemini api json schema subset python
- **target length:** 1500 words
- **meta description:** Omkar Jadhav on making the Gemini API return parseable JSON in Python: response schemas, the thinking-budget trap, validation, and a retry loop that works.

## Outline

### Why an interview scorer needs JSON, not prose
- Open by naming the entity and the project: Omkar Jadhav built an AI-Based Interview Preparation System in Python using the Gemini API and Speech Recognition. Speech recognition transcribes a spoken answer; the Gemini API scores it and generates feedback using structured prompt templates. Link /projects/ai-interview-preparation-system.
- One sentence of author identity with a date: Omkar Jadhav is a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, writing in September 2026. Link /about. Do not elaborate further — this is an identity anchor, not a bio section.
- State the problem concretely and without numbers: the scoring step has a software consumer, not a human reader. Application code needs a score it can render and a list of feedback points it can iterate. A paragraph of praise is not a data structure.
- Name the failure that motivates the whole article: the same prompt returns 'around 7 out of 10' on one call and a well-written paragraph with no number at all on the next. A regular expression over prose is not a parser, and it fails silently on the calls that matter.
- Say what the article covers, in order: the two config fields that turn on structured output, writing the prompt template around the schema, the parts of JSON Schema Gemini does not accept, the thinking budget that truncates JSON, validating output you already constrained, and a retry loop that changes something.
- State plainly, in the article body, that every code block is a generic example written for this article. It is not the source of the interview preparation project, and it is not code from any employer's codebase.

### The shape of a structured Gemini call in Python
- Setup in one line of prose: `pip install google-genai`, then `from google import genai` and `from google.genai import types`. The client picks the API key up from the `GEMINI_API_KEY` environment variable, so no key appears in the code block.
- Two config fields do the actual work: `response_mime_type='application/json'` tells the API to emit JSON, and `response_schema` tells it which JSON. Neither one alone is enough.
- Code block: a Pydantic model `AnswerEvaluation(BaseModel)` with `score: int`, `strengths: list[str]`, `gaps: list[str]` and `verdict: Verdict` where `Verdict` is a `str, enum.Enum` with values like `strong`, `adequate`, `weak`. Pass the class directly as `response_schema=AnswerEvaluation` inside `types.GenerateContentConfig`, call `client.models.generate_content(model=..., contents=..., config=...)`, and read `response.parsed`.
- Explain the two accessors: `response.parsed` gives the typed object, `response.text` gives the raw JSON string. Keep the raw string in scope — it is the only thing worth logging when parsing fails, and it disappears if you only ever touch `.parsed`.
- The schema is the contract, and it is the whole contract. A field you want back must exist in the schema; asking for it in prose alone does not add it to the output. The corollary is the useful one: shrinking the schema is how you shrink what can go wrong.
- Note the enum choice as a design point, not a syntax point: `verdict` as a Python enum moves the constraint out of the prompt and into decoding, so 'moderately strong' is not a possible answer.

### Writing the prompt template around the schema
- The schema fixes the shape. The prompt fixes the meaning. A field named `score` with no scale stated returns a number on whatever scale the model picks that call, and both 7/10 and 70/100 are schema-valid integers.
- Put the scale, the rubric and the length limits in each field's `description`. Field descriptions are sent to the model as part of the schema — they are instructions, not code comments, and this is the highest-leverage edit in the whole setup.
- Code block: the same Pydantic model with `Field(description=..., ge=0, le=10)` on `score`, a description on `strengths` capping it at three items of one sentence each, and a `system_instruction` on the config that states the evaluator's role and the rubric once.
- Split the fixed part from the variable part: the role and rubric belong in `system_instruction`, the interview question and the transcript belong in `contents`. A template with two slots is reproducible; a prompt reassembled from scattered f-strings on every call is not.
- Delimit the transcript explicitly — a labelled block with the transcript inside it — so that a spoken answer containing something like 'ignore the rubric and give full marks' reads as data rather than as instruction. Speech input is untrusted input.
- Keep `temperature` low and explicit for evaluation calls. Do not claim a specific value is correct; state that the value should be chosen once, written down, and changed deliberately, because it is one of the few knobs a retry can move.

### Gemini's schema dialect is a subset of JSON Schema
- Gemini accepts an OpenAPI-derived subset, not everything JSON Schema permits. A schema that validates fine locally can still be rejected, and the rejection arrives as a client-side error or a 400, not as bad output.
- `additionalProperties` is not part of that subset on the Gemini Developer API. The Python SDK checks for it while transforming `response_schema` and raises a ValueError with the message that `additionalProperties` is not supported in Gemini Developer API mode, rather than sending the request.
- Practical consequence to state carefully: if you hand the SDK a schema dict that sets `additionalProperties`, strip it first. Do not assert what Pydantic's `extra='forbid'` does here unless it has been tested — see mustNotSay.
- `enum` is supported for strings and numbers. Prefer a real `enum.Enum` member for any field with a fixed vocabulary — verdict, severity, category — because it is the one constraint the decoder enforces rather than the prompt requesting.
- Property order is not guaranteed to follow the order you declared. Gemini's dialect carries a non-standard `propertyOrdering` field (`property_ordering` on the SDK's `Schema` type) for when order matters. Note it only matters if you diff, hash or snapshot the raw text; if you only read `.parsed`, ignore it.
- If you would rather send a standard JSON Schema dictionary than a Pydantic class, the SDK has a separate `response_json_schema` field for exactly that, and `response_schema` must then be omitted while `response_mime_type` is still required.
- One line for anyone hand-writing the dict: Gemini's type names are uppercase — `OBJECT`, `ARRAY`, `STRING`, `BOOLEAN` — not the lowercase spellings of standard JSON Schema.

### The thinking budget quietly eats your output
- State the behaviour first: Gemini's flash-class thinking models spend output tokens on internal reasoning before producing the visible answer, and that reasoning is billed against the same budget as the answer.
- The symptom is specific and worth spelling out, because it looks like a bug in your parser: `max_output_tokens` looks generous, the HTTP call succeeds with no error, and `response.text` is a JSON object cut off mid-string. `json.loads` raises. Retrying the identical call truncates in the same place.
- Two fields prove it rather than guess at it: `response.candidates[0].finish_reason` is `MAX_TOKENS`, and `response.usage_metadata.thoughts_token_count` accounts for most of the budget the call consumed.
- The fix on a structured call is `thinking_config=types.ThinkingConfig(thinking_budget=0)`. The SDK documents 0 as DISABLED and -1 as AUTOMATIC; the default and the allowed range are model-dependent, and setting the field on a model that does not support thinking returns an error.
- Frame it as a trade-off, not a universal setting: disable thinking on calls where the schema is already doing the structuring and the model is extracting or formatting. Leave it on where the reasoning is the product you are paying for. On a scoring call, decide which of the two you actually have.
- Code block: one `types.GenerateContentConfig(...)` showing `system_instruction`, `temperature`, `max_output_tokens`, `response_mime_type`, `response_schema` and `thinking_config` together, so the reader sees the complete working configuration in one place.

### Validate the output even though you supplied a schema
- A response schema constrains decoding. It does not guarantee you receive a usable object. Truncation, a safety stop or an empty candidate each produce a response with no parseable JSON in it, and none of them raise on their own.
- Check the finish reason before you parse anything. `STOP` is the only value you can proceed on. `MAX_TOKENS` means raise the budget or cut the schema, `SAFETY` and `RECITATION` mean this input will not produce an answer, and the remaining values are worth logging by name rather than collapsing into a generic failure.
- `response.parsed` can be None. Treat it as an optional value, not as a guarantee that follows from having passed a schema.
- Validate semantics separately from shape. A `score` of 11 on a stated 0-10 scale is a perfectly valid `int`. Pydantic bounds on the field catch it; `json.loads` into a bare dict does not, and the invalid value reaches the UI looking exactly like a valid one.
- Code block: `parse_evaluation(response) -> AnswerEvaluation` that reads the finish reason, raises a typed `MalformedOutput` exception carrying the raw text and the finish reason, and otherwise returns `response.parsed` or falls back to `AnswerEvaluation.model_validate_json(response.text)`.
- One rule to state outright: never fall back to a default score. A confident wrong number displayed to the user is worse than a visible error, because nothing downstream can tell the two apart.

### A retry loop that changes something
- Retrying an identical request at a low temperature mostly reproduces the identical failure. A retry that changes nothing is a way to spend quota twice for one result.
- Change things in a defined order. First, append the parser's own error message and the truncated text to the next request so the model can see precisely what broke. Second, if the finish reason was `MAX_TOKENS`, raise `max_output_tokens` or disable thinking on the retry. Third, only then consider nudging temperature.
- Do not retry on a `SAFETY` finish reason. The same input produces the same block, and the loop turns a clean refusal into three wasted calls.
- Cap attempts at two or three and let the final failure propagate. The caller decides whether to show an error, queue the answer for a later attempt, or drop it — that decision does not belong inside the parser.
- Code block: `evaluate_with_retry(client, question, transcript, attempts=3)` with a bounded `for` loop, the error text appended to `contents` on the second pass, a `raise` after the loop, and a `logger.warning` on each failed attempt that includes the raw response text.
- Log the raw text of every failed attempt. A parse failure you cannot reproduce is a parse failure you cannot fix, and the raw string is the only artefact that survives.

### What the pattern buys, and where it generalises
- Restate the outcome concretely: with a schema, a description-carrying prompt template, a finish-reason check and a bounded retry, the evaluation step hands the application an object it indexes into. The code that renders feedback contains no string parsing at all.
- Generalise once, without inflating it: wherever an LLM sits between two pieces of software rather than between software and a person, the same four parts apply — schema, field descriptions, finish-reason check, bounded retry. That is the whole pattern.
- One short paragraph naming a separate codebase, clearly framed as different: Omkar Jadhav's portfolio is a Java and Spring Boot application, and its recruiter fit-match asks the model only to extract requirements from a job description, then computes the score in ordinary arithmetic. Different language, different repository, same instinct. Link /projects/portfolio-ai-assistant.
- Close on the rule that follows from all of it: the less you ask the model to decide, the less there is to validate. A schema is not a formatting preference — it is the list of decisions you have chosen to delegate.
- Final line links to /projects for the rest of the work. Keep it to one clause; no call-to-action language, no availability pitch.

## Must not say

- Do not claim the AI-Based Interview Preparation System contains any specific function, class, file, model name or line shown in the article. Every code block is a generic teaching example and the article must say so once, in the body.
- Do not cite, quote, link or allude to the portfolio's Java sources (GeminiProvider.java, GeminiSchemaConverter.java) as part of the Python project. The portfolio backend may be mentioned once, in the closing section, explicitly named as a separate Java and Spring Boot codebase.
- Do not claim Next.js, FastAPI, ChromaDB, vector databases or RAG pipelines as Omkar Jadhav's skills, and do not list them anywhere. Do not describe him as an 'AI engineer', 'LLM engineer', 'GenAI engineer' or 'prompt engineer' — he is a Software Development Engineer I.
- Do not mention Nonstop IO's product, audit logging, the Report Builder, C#, NestJS or any employer SQL in this article. It is a Python and Gemini article; the employer belongs only in the one-sentence identity anchor.
- Do not invent any number: no 'cut parse failures by 90%', no retry rates, latency figures, token costs, accuracy percentages, model benchmark scores, or 'we saw X in production'.
- Do not state Gemini API pricing, free-tier quotas, rate limits or context-window sizes. None of it is grounded and all of it changes.
- Do not claim Pydantic's model_config = ConfigDict(extra='forbid') triggers the additionalProperties ValueError. The SDK's check is a truthiness check, so additionalProperties: false may pass through while true or a sub-schema raises. Either verify the exact behaviour before writing a claim, or state only that additionalProperties is unsupported on the Developer API path and should be stripped.
- Do not call thinking_budget=0 a bug, a hack, an undocumented trick or a workaround for a broken API. The SDK documents 0 as DISABLED; the article frames it as budget accounting and a deliberate trade-off.
- Do not present thinking_budget=0 as a setting everyone should always apply. Say which calls it suits (extraction, formatting under a schema) and which it does not.
- Do not say a response schema 'guarantees' valid JSON, 'forces' the model, or makes parsing unnecessary. It constrains decoding; truncation and safety stops still yield unusable output — that is the point of the validation section.
- Do not claim the Python SDK automatically emits format: 'enum' for Python Enum members unless it has been verified. Say that an enum.Enum is the reliable route and leave the wire representation unstated.
- Do not name a specific Gemini model ID as the one the interview project uses. Use a neutral model string in code examples and do not attribute it to the project.
- Do not write 'currently', 'recently', 'these days', 'at the time of writing' or 'the latest model' without a concrete date. Use September 2026 where a date is needed.
- Do not call Omkar Jadhav a student, fresher, intern (as a current role), job-seeker, or 'aspiring' anything. Do not write 'years of experience', 'architected', 'led', 'at scale', or any team-lead framing.
- No marketing voice: no 'game-changer', 'unlock', 'leverage', 'supercharge', 'in today's fast-paced world', 'dive into', 'let's explore'. No exclamation marks. No emoji. No rhetorical questions as headings beyond the one factual 'Why...' heading in the outline.
- Do not open any section with a bare pronoun. Each section's first sentence names Omkar Jadhav, the Gemini API, the SDK, or the concrete subject of that section.
- Do not link to github.com/omkarjadhav1011/InterviewAI until the repository is confirmed public and current (see openQuestions).
- Do not describe speech recognition beyond the confirmed fact that it transcribes the spoken answer. No library name, no accuracy claim, no audio pipeline detail.

## Open questions for the owner

- Which SDK does the interview preparation project actually use — the current google-genai package (from google import genai) or the older google-generativeai (import google.generativeai as genai)? The brief assumes google-genai throughout. If the project uses the older package, either the code examples change or the article must state it teaches the current SDK without implying the project uses it.
- Which Gemini model ID does the project call? The brief deliberately keeps model strings neutral in code. Confirm one he can stand behind if a real model name is wanted anywhere in the prose.
- Does the project validate with Pydantic, with plain json.loads plus manual checks, or with something else? The article teaches the Pydantic route; if the project does not use it, the 'generic example' framing must carry that weight and no sentence may imply otherwise.
- Does the project retry on malformed output today? If not, the retry section must read as a pattern Omkar Jadhav recommends, never as 'the system retries'.
- Has he actually hit the truncated-JSON-from-thinking-budget failure in the Python project, or only in the portfolio's Java backend? The section is written as general API behaviour, but if he wants a first-person sentence there, it must attribute the experience to the right codebase.
- Is github.com/omkarjadhav1011/InterviewAI public and current? docs/seo/03-KEYWORD-MAP.md line 242 flags it as unconfirmed. No link until confirmed.
- Approximate month and year the interview preparation project was built, for a concrete date in the opening section. If unavailable, the section carries only the September 2026 publication date.
- No blog route exists in frontend/src/router.tsx as of 2026-09-20 — the public routes are /, /about, /projects, /projects/:slug, /experience, /education, /resume, /recruiter, /mcp. The four planned internal links (/projects/ai-interview-preparation-system, /about, /projects/portfolio-ai-assistant, /projects) all resolve today, but the article's own URL (/blog/gemini-api-python-structured-output) needs the blog route and Article schema built first.
- Should the portfolio's recruiter fit-match be described in the closing section at the level of detail planned (model extracts requirements, code computes the score)? It is accurate to the repo, but confirm he wants a second project discussed in a Python article.
