---
title: "Building a public read-only MCP server with Spring AI"
slug: "spring-ai-mcp-server"
description: "Omkar Jadhav walks through the read-only MCP server in his Spring Boot portfolio: Spring AI @Tool methods, a shared query layer, and SSE rate limits."
primaryKeyword: "spring ai mcp server tutorial"
status: draft-unverified
grounded: true
wordCount: 1780
verifyLensesRun:
  - "proprietary-leak"
  - "seo-structure"
  - "withdrawn-and-inflation"
verifyLensesNotRun:
  - "grounding"
unresolvedFindings:
  - "[proprietary-leak/low] This is the only mention of the employer in the draft, and it is a role/title/location/start-date statement rather than a codebase detail, so it does not leak anything. The one residual risk is adjacency: a ~3,000-word technical walkthrough of Spring Boot / Java 21 / Spring AI code closes with an employer byline, and a skimming reader (or an LLM summarising the page) could carry the Java/Spring stack over to the Nonstop IO role, which the public record describes only as backend work on an enterprise reporting product. The article does say \"one small read-only server on a personal portfolio backend\" two paragraphs earlier, so this is a hardening note, not a defect. -> Optional: make the separation explicit in the closing line, e.g. \"This MCP server is part of Omkar Jadhav's personal portfolio project, built outside his day job. He is a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, a role he has held since August 2026.\" Verify the title/location/date against the site's own profile data (frontend/src/lib/identity.ts and frontend/src/data/experience.ts already state exactly this), and do not add any description of what he builds there beyond the three approved sentences."
  - "[withdrawn-and-inflation/medium] Two problems in one sentence. First, the number is wrong: backend/src/test/java/com/portfolio/mcp/McpToolOutputBoundaryTest.java is 99 lines, not eighty. Second, \"would do the same job on something much larger\" is an unevidenced scaling claim — he has not run this on anything larger, and it is the closest thing in the piece to the \"at scale\" framing the rules bar. The self-deflating sentence before it (\"This is one small read-only server\") is undercut by the speculation that follows. -> Replace with: \"The interesting part is the boundary discipline, not the size.\" and stop there — delete the trailing clause about something much larger. If the sentence is kept, correct \"eighty-line\" to \"hundred-line\" and confine the claim to this repository."
  - "[withdrawn-and-inflation/medium] The line count is inaccurate — the actual test file is 99 lines. In a piece governed by a no-invented-numbers rule, a stated figure that is ~20% low is a fabricated metric even though it reads as a casual approximation. -> Change \"about eighty lines\" to \"about a hundred lines\", or drop the number entirely: \"a reflective test over the layer's annotated surface is short and never forgets.\""
  - "[withdrawn-and-inflation/low] Imperative advice register — instructing other engineers on general practice rather than explaining what he built. This is the \"staff engineer surveying a field\" voice rule 4 warns against. The same register appears in \"The rule generalises: throttle where requests are counted, not where connections are held.\" It is substantially mitigated by the honest paragraph that follows, so this is a tone note rather than a false claim. -> Reframe as his own reuse rather than prescription: \"Four things here are what Omkar Jadhav would reuse on the next server. The public data shape is defined once and exposed twice, so the chatbot and the tool surface cannot drift apart. The boundary returns records rather than entities, so curation is a type and not a habit.\""
  - "[withdrawn-and-inflation/low] This section opens with a bare \"this server\" / \"it\" and names no entity. \"What transfers\" is the section most likely to be lifted as a standalone summary, and a retrieved paragraph cannot resolve which server or whose it is — the disambiguation problem the brief calls out, given at least fifteen engineers share the name. -> Open with the entity: \"Four things from Omkar Jadhav's portfolio MCP server generalise past it.\""
  - "[withdrawn-and-inflation/low] Undated, unresolvable reference. \"The branch\" has no antecedent, and the extraction rules ask for concrete dates rather than floating temporal references like \"recently\" or \"currently\". A retrieved paragraph cannot tell which branch or when. -> Replace with a dated anchor, e.g. \"using the code as it stands in September 2026\", or name the branch explicitly."
  - "[withdrawn-and-inflation/low] \"The candidate\" recurs throughout the quoted tool descriptions, and the article also describes get_availability and the availableForWork field. These are verbatim quotes of his own source code, which is legitimate as a description of software — and the closing paragraph correctly anchors him as an employed SDE-I. The risk is extraction-only: a retrieval model lifting a mid-article paragraph could read \"the candidate\" plus \"availability\" as present-tense job-seeker framing about Omkar, which the rules bar. -> Add one clarifying sentence after the first quoted tool block, e.g.: \"The 'candidate' wording is the tool contract's own, aimed at recruiter-side clients; Omkar Jadhav has been a Software Development Engineer I at Nonstop IO Technologies since August 2026.\" No code quote needs changing."
  - "[seo-structure/high] Length is fine (53 chars) and it is not clickbait, but the primary keyword \"spring ai mcp server tutorial\" is absent. The two halves appear in reverse order (\"MCP server ... with Spring AI\") and the word \"tutorial\" appears nowhere in the entire draft, so nothing on the page carries the target phrase. -> Front-load the exact phrase: `title: Spring AI MCP server tutorial: a public read-only build` (57 chars). Keep it lowercase-natural rather than stuffed."
  - "[seo-structure/high] The single H1 (correct: there is exactly one) does not contain the primary keyword, for the same reason as the title. H1 is the strongest on-page relevance signal and currently matches no query form of the target phrase. -> Mirror the corrected title: `# Spring AI MCP server tutorial: building a public read-only server`. Use the phrase once here and do not repeat it in the next sentence."
  - "[seo-structure/high] The primary keyword does not appear in the first 100 words — in fact \"Spring AI\" as a phrase does not appear in the intro at all (only \"Spring Boot\"). The opening is the paragraph most likely to be retrieved as the answer chunk, and it never states what the article is a tutorial for. -> Rewrite the third sentence to carry the phrase once, naturally: \"This Spring AI MCP server tutorial walks through how it is wired, what it is allowed to return, and where the rate limiting sits.\" Leave the rest of the intro unchanged."
  - "[seo-structure/high] No H2 in the article contains the primary keyword — all eleven H2s are framing-style headings (\"The whole server is...\", \"One query layer, exposed twice\", \"What transfers\"). The rule requires the keyword in at least one H2, and this one is the natural host since the section is literally the Spring AI starter dependency and its config. -> Retitle to `## The whole Spring AI MCP server is a dependency and a config block`. Do not add the keyword to a second H2 — once is enough."
  - "[seo-structure/medium] Length (149 chars) and voice (active) are correct, but the exact primary keyword is not present — \"Spring AI\" and \"MCP server\" are separated by 40 characters and \"tutorial\" is missing, so the snippet does not match the target query. -> Replace with (158 chars): \"A Spring AI MCP server tutorial: Omkar Jadhav wires a public read-only MCP server in Spring Boot — @Tool methods, one shared query layer, and SSE rate limits.\""
  - "[seo-structure/medium] Extraction quality: the paragraph opens with a bare demonstrative \"This\" whose antecedent (the `ToolCallbackProvider` bean in the previous code block) is outside the paragraph. Retrieved on its own, the chunk never says what cuts against the reflex. -> \"Registering MCP tools as a `ToolCallbackProvider` bean cuts against the reflex a Spring developer brings.\""
  - "[seo-structure/medium] Extraction quality: opens with a bare \"That\" pointing at the previous paragraph's Javadoc rule. Standing alone, the paragraph never names what the guarantee is about, and the following \"the facade\" is also unnamed inside this paragraph. -> \"`PortfolioQueryService`'s delegation rule is a structural guarantee rather than a stylistic one. Because the facade cannot reach a repository...\""
  - "[seo-structure/medium] Extraction quality: \"it\" has no antecedent in this paragraph — `McpToolOutputBoundaryTest` was named three paragraphs earlier. A retrieved chunk asserting \"what it proves\" cannot be attributed to anything. -> \"Be precise about what `McpToolOutputBoundaryTest` proves: the output shape carries no forbidden field names, and no output is an entity.\""
  - "[seo-structure/medium] A heading built on a bare pronoun. Headings are the anchor text answer engines use to label a passage; \"it\" labels nothing and the section is also the article's most query-shaped one (how to connect a client to a Spring AI MCP server). -> \"## Pointing an MCP client at the server\" — or, if you would rather place the keyword here than in the dependency H2, \"## Pointing an MCP client at the Spring AI server\"."
  - "[seo-structure/medium] Vague, undated version reference: no date and no branch name, on an article that otherwise dates itself precisely (\"23 June 2026\"). It also silently ages — a reader in 2027 has no way to know what \"as it stands\" meant. -> Name both: \"using the code as it stands in September 2026 (commit `adb6754`)\", or name the actual branch the MCP feature lives on."
  - "[seo-structure/low] Positional cross-reference (\"later in this article\") is meaningless in a retrieved passage or an AI-generated answer, where there is no \"later\". -> Point at the thing, not the position: \"That asymmetry is why the rate-limit filter is registered on `/mcp/message` and never on `/mcp/sse`.\""
  - "[seo-structure/low] The summary section — the passage most likely to be lifted whole as an answer — opens with \"this server\" and closes on \"it\", neither of which identifies the subject outside the article's context. -> \"Four things from this Spring AI MCP server generalise past the portfolio it runs on.\""
  - "[seo-structure/low] Past tense in a heading dates an otherwise evergreen reference section; a query like \"what does a Spring AI MCP server expose\" matches present tense better. -> \"## What Omkar Jadhav exposes over MCP, and why read-only\"."
claimsNeedingConfirmation:
  - "The Claude Desktop config block currently prints a `<backend>/mcp/sse` placeholder. The real value comes from VITE_API_URL at build time — confirm the exact public SSE URL to print, or confirm the placeholder should stay."
  - "router.tsx has no /blog route as of 20 Sept 2026. Confirm where this article will live (a new /blog/:slug route, an external host, or a static page) so the canonical URL and any cross-links can be written correctly."
  - "The article does not claim the server has been exercised end-to-end against Claude Desktop or @modelcontextprotocol/inspector. Confirm whether it has, and in what month, so a dated sentence can be added."
  - "backend/pom.xml pins Spring AI 1.0.9 on this branch. Confirm the version at publish time, since the missing progressToken in the tool bridge is version-specific."
  - "The article names the RateLimiter capacity (10 tokens, refilling at 10/minute) and MAX_JD_LENGTH (8000) because both are constants in the repository, but deliberately omits AI_DAILY_REQUEST_CAP and the per-IP daily cap values. Confirm whether those cost ceilings may be named with numbers."
  - "Internal links used: /mcp, /projects, /recruiter, /about — all four exist in frontend/src/router.tsx. Confirm whether a specific project detail page (/projects/<slug>) is a better anchor than /projects for the portfolio backend itself, and supply the real slug if so."
  - "The article includes a short one-paragraph MCP primer before the Spring AI wiring. Confirm whether that primer should stay or be cut for a reader assumed to know the protocol."
  - "The closing line states Omkar Jadhav has been an SDE-I at Nonstop IO Technologies since August 2026. Confirm this phrasing is acceptable in a technical article's closing, or whether it should be dropped entirely."
---
# Building a public read-only MCP server with Spring AI

Omkar Jadhav's portfolio backend — Spring Boot 3.5 on Java 21 — runs a small public Model Context Protocol server alongside the ordinary REST API. The MCP package first landed in the repository on 23 June 2026. This article walks through how it is wired, what it is allowed to return, and where the rate limiting sits, using the code as it stands on the branch.

## What Omkar Jadhav exposed over MCP, and why read-only

The Model Context Protocol lets an MCP client — Claude Desktop, the MCP Inspector, anything that speaks the protocol — discover a server's tools and call them. The server publishes tool names, descriptions and parameter schemas; the client's model reads those descriptions and decides what to call. There is no UI to design and no prompt to write on the server side. The tool surface is the API.

The portfolio server exposes exactly eight tools, all defined in `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java`: `get_profile`, `get_availability`, `list_projects`, `get_project`, `list_skills`, `get_experience`, `get_resume_summary`, and `match_against_jd`. Nothing mutates. There is no auth, deliberately — everything the tools return is already published on the site, so an access-control layer would guard nothing.

Three ideas carry the design, and they are what the rest of this article is about: one query layer exposed twice, view records that cannot reach past the public boundary, and a rate-limit filter registered on the message endpoint and not on the stream. The live server, with its client config and tool list, is on [the MCP page](/mcp).

## The whole server is a dependency and a config block

`backend/pom.xml` adds exactly one MCP artifact. Its version is managed by an imported `spring-ai-bom` pinned at 1.0.9, on Java 21:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

`backend/src/main/resources/application.yml` turns the server on and names it. There is no custom controller anywhere in the feature:

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: omkar-portfolio-mcp
        version: 0.1.0
        sse-endpoint: /mcp/sse
        sse-message-endpoint: /mcp/message
```

The WebMVC starter gives you the SSE transport, which is two endpoints with very different shapes. `GET /mcp/sse` is a long-lived stream held open for the whole session and carrying server-to-client traffic. `POST /mcp/message` is a short, ordinary request carrying one JSON-RPC message client-to-server. That asymmetry is the reason for the rate-limiting decision later in this article.

Both endpoints sit under `/mcp/**` on purpose, so the security config can open the surface with a single matcher.

## Registering tools: a ToolCallbackProvider bean, not a controller

`backend/src/main/java/com/portfolio/mcp/McpServerConfig.java` declares one bean. The starter auto-detects any `ToolCallbackProvider` and exposes its tools over the configured transport:

```java
@Bean
ToolCallbackProvider portfolioToolCallbackProvider(PortfolioMcpTools portfolioMcpTools) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(portfolioMcpTools)
            .build();
}
```

This cuts against the reflex a Spring developer brings. There is no `@RestController`, no request mapping, no DTO binding to write, no serialization config. The method signature is the schema: parameter types become the input schema, the return type becomes the output.

`@Tool(name, description)` names the tool and writes its contract; `@ToolParam(required = false, description = ...)` marks an optional argument:

```java
@Tool(name = "get_profile",
        description = "Public summary of the candidate: name, headline, current role/status, "
                + "location, availability, and public links. Call this to learn who the "
                + "candidate is.")
public ProfileView getProfile() {
    return portfolioQueryService.getProfile();
}

@Tool(name = "list_projects",
        description = "List the candidate's projects — each with name, description, language, "
                + "tech stack (tags), status, and links. Use the optional 'filter' to narrow to "
                + "projects matching a technology or keyword (e.g. \"Spring Boot\", \"Postgres\").")
public List<ProjectView> listProjects(
        @ToolParam(required = false,
                description = "Optional technology or keyword to filter projects by (matched "
                        + "against name, language, tags, and description). Omit to list all.")
        String filter) {
    return portfolioQueryService.listProjects(filter);
}
```

The description string is the API surface. The calling model reads it to choose a tool and to fill in arguments, so the descriptions say what the tool answers and how to chain. `get_project`'s description ends with "Get the slug from `list_projects` first." The practical consequence: a badly worded description is a bug, and the model never sees your Javadoc.

## One query layer, exposed twice

`PortfolioQueryService` (`backend/src/main/java/com/portfolio/query/PortfolioQueryService.java`) is the single body of code that both the in-browser chatbot and the public MCP server draw from. Its Javadoc states the rule it lives by: it delegates to `PortfolioContextService`, the corpus boundary, and never touches repositories directly.

That is a structural guarantee rather than a stylistic one. Because the facade cannot reach a repository, it physically cannot return a column the curated corpus does not already hold. Seven of the eight tools delegate straight to it; only `match_against_jd` calls a second service, `RecruiterMatchService`.

Every method returns a small purpose-built record — `ProfileView`, `ProjectView`, `ProjectDetailView`, `ExperienceView`, `SkillView`, `ResumeSummaryView`, `AvailabilityView` — never a JPA entity and never raw bytes. `ProfileView` is seven components long:

```java
public record ProfileView(
        String name,
        String headline,
        String currentBranch,
        String currentStatus,
        boolean availableForWork,
        String location,
        List<Link> links
) {
    /** A single public link (label + URL). The UI-only {@code icon} is intentionally dropped. */
    public record Link(String label, String url) {
    }
}
```

The dropped `icon` field is the detail worth noticing. The source `SocialLink` carries an icon name for the UI; `ProfileView.Link` keeps only label and URL. The view is a whitelist, not a projection of convenience.

Filtering happens server-side. `listProjects` lowercases the filter once and matches case-insensitively against name, language, slug, description and tags, so a model can ask for "Spring Boot" without pulling the whole list; a blank or null filter returns everything. `getProject` throws `IllegalArgumentException` when the slug is blank or matches no project. What `list_projects` returns, rendered for humans, is [the projects page](/projects).

## The boundary is a test, not a convention

`backend/src/test/java/com/portfolio/mcp/McpToolOutputBoundaryTest.java` enforces the output rule at build time. It reflects over every method on `PortfolioMcpTools` carrying `@Tool`, unwraps `List<X>` to `X`, and asserts the output type is a record — so a JPA entity or a `byte[]` fails the build. It then walks the record-component tree recursively, descending into nested records and `List<Record>`, collecting every field name in the output shape:

```java
private static final List<String> FORBIDDEN = List.of(
        "password", "secret", "hash", "apikey", "token", "credential",
        "avatardata", "resumedata", "rawbytes", "bytes",
        "createdat", "updatedat", "deletedat", "internal");

for (String name : names) {
    String lower = name.toLowerCase(Locale.ROOT);
    for (String bad : FORBIDDEN) {
        assertFalse(lower.contains(bad),
                "@Tool '" + tool.getName() + "' output field '" + name
                        + "' matches forbidden fragment '" + bad + "'.");
    }
    assertNotEquals("id", lower, "raw entity id must not be exposed by a tool output");
    assertNotEquals("uuid", lower, "raw uuid must not be exposed by a tool output");
}
```

The property that makes this worth writing is that the test enumerates tools by reflection rather than from a hand-maintained list. A tool added six months later is covered without anyone remembering to cover it. It also asserts the tool list is non-empty, so deleting the tools does not make it trivially pass.

Be precise about what it proves: the output *shape* carries no forbidden field names, and no output is an entity. It is a structural check, not a claim that every value is harmless. The corpus boundary one layer in (`CorpusBoundaryTest`) and the facade test (`PortfolioQueryServiceTest`) cover the other layers.

The transferable form: when a rule is "this layer must never return X", a reflective test over the layer's annotated surface is short and never forgets.

## Rate limiting the message endpoint, never the stream

`McpServerConfig` registers the throttle with one url pattern, and the whole design hangs on that line:

```java
@Bean
FilterRegistrationBean<McpRateLimitFilter> mcpRateLimitFilter(RateLimiter rateLimiter,
                                                              ObjectMapper objectMapper,
                                                              AbuseLog abuseLog,
                                                              EngagementRecorder engagementRecorder) {
    FilterRegistrationBean<McpRateLimitFilter> registration =
            new FilterRegistrationBean<>(new McpRateLimitFilter(
                    rateLimiter, objectMapper, abuseLog, engagementRecorder));
    registration.addUrlPatterns("/mcp/message");
    registration.setName("mcpRateLimitFilter");
    return registration;
}
```

`/mcp/sse` is excluded for a mechanical reason. It is a single long-lived GET held open for the session. A per-request throttle there charges one token for a connection that then carries an unbounded number of tool calls — the wrong unit — and a 429 on it does not reject one call, it kills the client's session and the connection it would have retried over. The request-shaped endpoint is `/mcp/message`, so that is where a per-request limiter belongs. The rule generalises: throttle where requests are counted, not where connections are held.

A servlet filter rather than a check inside the `@Tool` method, because tools run off the servlet request thread and the client IP is not readable from inside a tool. The filter sits on the request thread, where the container-resolved remote address is available.

Reading the body twice needs help. A servlet input stream is single-pass, so `CachedBodyHttpServletRequest` buffers the small JSON-RPC body and replays it downstream; without it, inspecting the body in the filter would starve the framework.

The filter then parses the envelope and throttles only `tools/call`:

```java
String method = null;
String toolName = "unknown";
String arguments = "";
try {
    JsonNode root = objectMapper.readTree(cached.body());
    method = root.path("method").asText(null);
    if ("tools/call".equals(method)) {
        toolName = root.path("params").path("name").asText("unknown");
        arguments = root.path("params").path("arguments").toString();
    }
} catch (Exception ignored) {
    // Not a JSON-RPC body we recognize — let the MCP framework reject it; we don't throttle.
}
```

`initialize`, `notifications/initialized`, `tools/list` and `ping` pass through untouched, so a client can always connect and discover tools even from an IP that has spent its budget. An unparseable body is not throttled at all — the framework rejects it.

## Two buckets, because one tool costs money

`match_against_jd` is the only LLM-backed tool in the set. `McpRateLimitFilter` keys it under `mcp-match:<ip>` and everything else under `mcp:<ip>`, so cheap data-tool traffic cannot amplify or starve the expensive one:

```java
String prefix = MATCH_TOOL.equals(toolName) ? MATCH_RATE_LIMIT_PREFIX : RATE_LIMIT_PREFIX;
RateLimiter.Result limit = rateLimiter.check(prefix + ":" + clientIp);
if (!limit.ok()) {
    log.warn("[mcp] rate-limited tool={} ip={} retryAfter={}s",
            toolName, clientIp, limit.retryAfterSeconds());
    response.setStatus(429);
    response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
    response.setContentType("application/json");
    response.getWriter().write(
            "{\"error\":\"rate_limited\",\"message\":\"Rate limit reached for the "
                    + "portfolio MCP server. Try again in " + limit.retryAfterSeconds()
                    + "s.\"}");
    return;
}
```

The limiter itself is the same token bucket used by `/api/chat` and recruiter mode: capacity 10, refilling at 10 per minute, keyed by client IP. It is in-memory and per-instance, not distributed — its own Javadoc says so.

`McpRateLimitFilterTest` pins the behaviour with numbers rather than prose:

```java
@Test
void handshakeAndDiscoveryAreNeverRateLimited() throws Exception {
    for (int i = 0; i < 15; i++) {
        assertEquals(200, invoke("1.2.3.4", TOOLS_LIST),
                "tools/list call " + i + " must pass through unthrottled");
    }
}

@Test
void matchUsesASeparateBucketFromDataTools() throws Exception {
    // Drain the data-tool bucket (mcp:<ip>) completely...
    for (int i = 0; i < 10; i++) {
        assertEquals(200, invoke("5.5.5.5", TOOL_CALL));
    }
    // ...the match tool (mcp-match:<ip>) is still fully available for the same IP.
    assertEquals(200, invoke("5.5.5.5", MATCH_CALL),
            "match_against_jd must not share the data-tool bucket");
}
```

Two more tests assert that 13 tool calls from one IP produce exactly 3 responses of 429, and that the match bucket caps at 10 the same way. The numbers are asserted rather than described because "discovery is not throttled" is exactly the kind of claim that quietly stops being true after a refactor.

## Untrusted input on a public tool surface

`match_against_jd` guards its input before any LLM spend. An input cap is the cheapest cost control available:

```java
if (jdText == null || jdText.isBlank()) {
    throw new IllegalArgumentException("A job description is required.");
}
if (jdText.length() > MAX_JD_LENGTH) {
    throw new IllegalArgumentException(
            "Job description is too long (max " + MAX_JD_LENGTH + " characters).");
}
```

`MAX_JD_LENGTH` is 8000. The pasted job description is treated as data to compare against, never as instructions, and the tool description says so explicitly — which is part of the mitigation, since the description is what the calling model reads.

Injection screening is a detective control rather than a block. The filter passes the tool arguments through `AbuseLog.isSuspicious` and flags a hit with `warnSuspicious` at WARN. Injection-looking input is logged and still served: a false positive must not break a legitimate recruiter's call.

Every accepted call logs tool name and IP at INFO and records an `MCP_TOOL` engagement event — one of five passive signal types — because the filter is the one place on the request thread that sees both the tool name and the real client IP.

One line on determinism, since it is the reason `match_against_jd` is safe to expose at all: the model only extracts structure from the job description, and the fit score itself is computed by arithmetic in `MatchScoreCalculator`, so the same input yields the same number whichever provider answered. The browser version of the same flow is [recruiter mode](/recruiter).

## Security config: the matcher order is the exposure

`backend/src/main/java/com/portfolio/security/SecurityConfig.java` opens the surface with one line, for all HTTP methods rather than GET only:

```java
// Public, read-only MCP server. ALL methods,
// because the SSE transport uses GET /mcp/sse (stream) AND POST /mcp/message
// (client→server) — the GET /** catch-all below would miss the POST.
.requestMatchers("/mcp/**").permitAll()
// Public portfolio reads. Admin/auth GETs are already caught above.
.requestMatchers(HttpMethod.GET, "/**").permitAll()
```

Placement is the point. `/mcp/**` sits after the admin and vault matchers and before the public `GET /**` catch-all. Spring Security evaluates matchers in order, so moving that line up or down changes what is exposed without changing a single method body.

State the boundary honestly: `permitAll` here is safe only because everything reachable through it returns curated public views. The security config and the query layer are one argument, not two independent ones.

## Pointing a client at it

The site's `/mcp` page publishes the Claude Desktop config, which runs `mcp-remote` against the SSE endpoint:

```json
{
  "mcpServers": {
    "omkar-portfolio": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "<backend>/mcp/sse"]
    }
  }
}
```

For a faster look without a desktop client, `npx @modelcontextprotocol/inspector` connects to the same SSE URL and lists the tools.

One limitation is worth naming, because it shapes the code. MCP `notifications/progress` needs the client's `progressToken`, which the Spring AI 1.x tool bridge does not surface, so match progress is sent as a `loggingNotification` instead. Notification failures are swallowed: a progress signal must never fail the match it is reporting on. With no MCP exchange in the `ToolContext` — in a unit test, for instance — the progress listener degrades to `MatchProgressListener.NOOP`, so the tool stays testable without a client.

## What transfers

Four things from this server generalise past it. Define the public data shape once and expose it twice, so the chatbot and the tool surface cannot drift apart. Return records built for the boundary rather than entities, so curation is a type and not a habit. Enforce the boundary with a reflective test over the annotated surface, so new tools are covered by default. And throttle the endpoint that counts requests, not the one that holds a connection.

This is one small read-only server on a personal portfolio backend. The interesting part is the boundary discipline, not the size.

Omkar Jadhav is a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune, and has been in that role since August 2026. More about the work is on [the about page](/about).
