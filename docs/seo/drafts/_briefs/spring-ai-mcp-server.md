# Content brief: Building a public read-only MCP server with Spring AI

- **slug:** `spring-ai-mcp-server`
- **primary keyword:** spring ai mcp server tutorial
- **supporting:** spring boot mcp server, model context protocol java, spring ai @Tool annotation, MethodToolCallbackProvider, ToolCallbackProvider bean, mcp sse endpoint spring, spring-ai-starter-mcp-server-webmvc, read-only mcp server, mcp server rate limiting, mcp tools/call json-rpc, spring security mcp endpoint, expose java methods as mcp tools
- **target length:** 1600 words
- **meta description:** Omkar Jadhav walks through the read-only MCP server in his Spring Boot portfolio: Spring AI @Tool methods, a shared query layer, and SSE rate limits.

## Outline

### What Omkar Jadhav exposed over MCP, and why read-only
- Open by naming the entity and the artifact: Omkar Jadhav's portfolio backend (Spring Boot 3.5, Java 21) runs a public, read-only MCP server; the MCP package first landed in the repository on 23 June 2026.
- One-paragraph primer: the Model Context Protocol lets an MCP client (Claude Desktop, the MCP Inspector) discover a server's tools and call them. The server publishes tool names, descriptions and parameter schemas; the client's model decides what to call.
- State the shape up front: eight @Tool methods — get_profile, get_availability, list_projects, get_project, list_skills, get_experience, get_resume_summary, match_against_jd. Nothing mutates. No auth, because everything the tools return is already public on the site.
- Name the three ideas the article is actually about, so the reader knows what they are getting: one query layer exposed twice, view records that cannot reach past the public boundary, and a rate-limit filter deliberately registered on the message endpoint and not on the stream.
- Internal link: /mcp — the live page that publishes the client config and the tool list.

### The whole server is a dependency and a config block
- backend/pom.xml adds exactly one artifact, spring-ai-starter-mcp-server-webmvc, with the version managed by an imported spring-ai-bom pinned at 1.0.9 on Java 21.
- application.yml configures spring.ai.mcp.server: enabled true, name omkar-portfolio-mcp, version 0.1.0, sse-endpoint /mcp/sse, sse-message-endpoint /mcp/message. Code block: those seven lines, verbatim.
- Explain what the WebMVC starter gives you: the SSE transport, which is two endpoints with very different shapes — a long-lived GET at /mcp/sse that stays open for the session, and a short POST at /mcp/message carrying each JSON-RPC message client-to-server. This asymmetry is the reason for the rate-limiting decision later in the article.
- Note the deliberate choice to keep both endpoints under /mcp/**, so the security config can open the surface with a single matcher.

### Registering tools: a ToolCallbackProvider bean, not a controller
- McpServerConfig declares one bean: MethodToolCallbackProvider.builder().toolObjects(portfolioMcpTools).build(). The starter auto-detects any ToolCallbackProvider and exposes its tools over the configured transport. Code block: the bean, verbatim from McpServerConfig.
- Contrast with the reflex a Spring developer brings: there is no @RestController, no request mapping, no DTO binding to write. The method signature is the schema.
- @Tool(name, description) names the tool and writes its contract; @ToolParam(required = false, description = ...) marks an optional argument. Code block: get_profile and list_projects as written, including the filter parameter.
- Make the point that the description string is the API surface: the client's model reads it to choose a tool, so the descriptions say what the tool answers and how to chain — get_project's description literally says 'Get the slug from list_projects first.'
- Practical consequence: a badly worded description is a bug. The model never sees your Javadoc.

### One query layer, exposed twice
- PortfolioQueryService is the single facade both the in-browser chatbot and the MCP server draw from. Its Javadoc states the rule: it delegates to PortfolioContextService (the corpus boundary) and never touches repositories directly.
- Why that matters structurally: because the facade cannot reach a repository, it physically cannot return a column that the corpus does not already hold. The boundary is enforced by what the class can reach, not by review discipline.
- Every method returns a small purpose-built record — ProfileView, ProjectView, ProjectDetailView, ExperienceView, SkillView, ResumeSummaryView, AvailabilityView — never a JPA entity, never raw bytes. Code block: ProfileView in full; it is seven components long.
- Concrete illustration of curation: the source SocialLink carries a UI-only icon field, and ProfileView drops it on the way out. The view is a whitelist, not a projection of convenience.
- listProjects filters server-side and case-insensitively against name, language, slug, description and tags, so the model can ask for 'Spring Boot' without pulling the whole list; getProject throws IllegalArgumentException on a blank or unknown slug.
- Internal link: /projects — what list_projects returns, rendered for humans.

### The boundary is a test, not a convention
- McpToolOutputBoundaryTest reflects over every method on PortfolioMcpTools carrying @Tool, unwraps List<X> to X, and asserts the return type is a record — so a JPA entity or a byte[] fails the build.
- It then walks the record-component tree recursively, descending into nested records and List<Record>, collecting every field name in the output shape.
- The forbidden list, quoted: password, secret, hash, apikey, token, credential, avatardata, resumedata, rawbytes, bytes, createdat, updatedat, deletedat, internal — plus an exact-match rejection of 'id' and 'uuid' so raw entity identifiers never leave. Code block: the FORBIDDEN constant and the assertion loop.
- The property that makes this worth writing: the test enumerates tools by reflection rather than by a hand-maintained list, so a tool added six months later is covered without anyone remembering to cover it. It also asserts the tool list is non-empty, so deleting the tools does not make it pass.
- Be precise about what it proves: the output *shape* carries no forbidden field names. It is a structural check, not a claim that every value is harmless — the corpus boundary one layer in (CorpusBoundaryTest) and the facade test (PortfolioQueryServiceTest) cover the other layers.
- Transferable form of the idea: when a rule is 'this layer must never return X', a reflective test over the layer's public surface costs about eighty lines and never forgets.

### Rate limiting the message endpoint, never the stream
- McpRateLimitFilter is registered through a FilterRegistrationBean with addUrlPatterns("/mcp/message") — one line, and the whole design hangs on it. Code block: the registration bean.
- Why /mcp/sse is excluded, stated as the mechanism: it is a single long-lived GET held open for the session. A per-request throttle there charges one token for a connection that then carries an unbounded number of tool calls — the wrong unit — and a 429 on it does not reject one call, it kills the client's session and the connection it would need to retry on.
- The request-shaped endpoint is /mcp/message, so that is where a per-request limiter belongs. The rule generalises: throttle where requests are counted, not where connections are held.
- Why a servlet filter rather than a check inside the @Tool method: tools run off the servlet request thread, so the client IP is not readable inside a tool. The filter sits on the request thread where the container-resolved remote address is available.
- Reading the body twice: a servlet input stream is single-pass, so CachedBodyHttpServletRequest buffers the small JSON-RPC body and replays it downstream; without it, inspecting the body in the filter would starve the framework.
- The filter parses the envelope and throttles only method == 'tools/call'. initialize, notifications/initialized, tools/list and ping pass through untouched, so a client can always connect and discover tools even from an IP that has spent its budget. An unparseable body is not throttled at all — the framework rejects it. Code block: the trimmed doFilterInternal.

### Two buckets, because one tool costs money
- match_against_jd is the only LLM-backed tool. The filter keys it under mcp-match:<ip> and everything else under mcp:<ip>, so cheap data-tool traffic cannot amplify or starve the expensive one.
- The limiter itself is the same in-memory token bucket used by /api/chat and recruiter mode: capacity 10, refilling at 10 per minute, keyed by client IP. Say plainly that it is per-instance and in-memory, not distributed.
- Over the limit: status 429, a Retry-After header, a small JSON error body, and a WARN log with tool name, IP and the retry hint. Code block: the 429 branch.
- McpRateLimitFilterTest pins the behaviour in four assertions — 15 consecutive tools/list calls all return 200; 13 tool calls from one IP produce exactly 3 responses of 429; draining the data bucket with 10 calls leaves match_against_jd available to the same IP; the match bucket caps at 10 too. Code block: two of those test methods.
- Note why the numbers are asserted rather than described: 'discovery is not throttled' is the kind of claim that quietly stops being true after a refactor.

### Untrusted input on a public tool surface
- match_against_jd rejects a null or blank job description and caps the text at 8000 characters before any LLM call — an input cap is the cheapest cost control there is. Code block: the two guards.
- The pasted job description is treated as data to compare against, never as instructions; the tool description says so explicitly, which is part of the mitigation.
- Detective control rather than a block: the filter screens tool arguments with AbuseLog.isSuspicious and flags a hit with warnSuspicious at WARN. Injection-looking input is logged and still served — a false positive must not break a legitimate recruiter's call.
- Every accepted call logs tool name plus IP at INFO and records an MCP_TOOL engagement event, because the filter is the one place on the request thread that sees both.
- One line on determinism, with a link out rather than a detour: the model only extracts structure from the job description; the fit score itself is computed by arithmetic, so the same input yields the same number. Internal link: /recruiter.

### Security config: the matcher order is the exposure
- SecurityConfig opens /mcp/** with permitAll for all HTTP methods, not just GET, because the SSE transport needs GET /mcp/sse and POST /mcp/message and the public GET /** catch-all would miss the POST. Code block: the matcher line with its comment.
- Placement is the point: /mcp/** sits after the admin and vault matchers and before the public GET /** catch-all. Matchers are evaluated in order, so moving a line changes what is exposed without changing a single method.
- State the boundary honestly: permitAll here is safe only because everything reachable through it returns curated public views — the security config and the query layer are one argument, not two independent ones.

### Pointing a client at it
- The /mcp page publishes the Claude Desktop config: an mcpServers entry running npx -y mcp-remote against the backend's /mcp/sse URL. Code block: that JSON.
- For a faster look without a desktop client, npx @modelcontextprotocol/inspector connects to the same SSE URL and lists the tools.
- One honest limitation, since it shapes the code: MCP progress notifications need the client's progressToken, which the Spring AI 1.x tool bridge does not surface, so match progress is sent as a loggingNotification instead — and notification failures are swallowed, because a progress signal must never fail the match it is reporting on.
- With no MCP exchange in the ToolContext, as in a unit test, the progress listener degrades to a no-op — the tool stays testable without a client.

### What transfers
- Four things worth stealing, each one sentence: define the public data shape once and expose it twice; return records built for the boundary rather than entities; enforce the boundary with a reflective test over the annotated surface; throttle the endpoint that counts requests, not the one that holds a connection.
- Close on scope, plainly: this is one small read-only server on a personal portfolio backend, and the interesting part is the boundary discipline rather than the size.
- Internal links: /about (who Omkar Jadhav is and where he works) and /projects (the rest of the backend this server sits in).

## Grounded claims (claim -> evidence)

- The portfolio backend runs a Spring AI MCP server named omkar-portfolio-mcp, version 0.1.0, with the SSE stream at /mcp/sse and the client-to-server message channel at /mcp/message, configured under spring.ai.mcp.server.  
  `backend/src/main/resources/application.yml:52-62`
- The MCP server is enabled by configuration (spring.ai.mcp.server.enabled: true) rather than by any custom controller.  
  `backend/src/main/resources/application.yml:58`
- Spring AI is pinned to 1.0.9 through an imported spring-ai-bom, on Java 21.  
  `backend/pom.xml:21-34`
- The only MCP dependency is spring-ai-starter-mcp-server-webmvc, whose version is managed by the BOM.  
  `backend/pom.xml:92-98`
- McpServerConfig exposes the tools by declaring a ToolCallbackProvider bean built with MethodToolCallbackProvider.builder().toolObjects(portfolioMcpTools); the starter auto-detects the bean.  
  `backend/src/main/java/com/portfolio/mcp/McpServerConfig.java:21-26`
- PortfolioMcpTools exposes exactly eight @Tool methods: get_profile, list_projects, get_experience, get_resume_summary, match_against_jd, get_project, list_skills, get_availability.  
  `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:74,82,94,106,114,159,168,175`
- Seven of the eight tools delegate straight to PortfolioQueryService; only match_against_jd calls a second service (RecruiterMatchService).  
  `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:79,91,103,111,130,165,172,179`
- Optional tool parameters are declared with @ToolParam(required = false, description = ...), and the descriptions tell the calling model how to chain calls ("Get the slug from list_projects first").  
  `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:87-90,159-164`
- PortfolioQueryService is a facade over PortfolioContextService (the corpus boundary) and never touches repositories directly, so it cannot reach past the public boundary.  
  `backend/src/main/java/com/portfolio/query/PortfolioQueryService.java:13-32`
- PortfolioQueryService is the single body of code that both the in-browser chatbot and the public MCP server draw from — one query layer, exposed twice.  
  `backend/src/main/java/com/portfolio/query/PortfolioQueryService.java:16-24`
- Each query method returns a small purpose-built record (a *View), never a JPA entity and never raw bytes.  
  `backend/src/main/java/com/portfolio/query/PortfolioQueryService.java:21-24`
- ProfileView carries only name, headline, currentBranch, currentStatus, availableForWork, location and a list of label/url links; the UI-only icon field on the source link is deliberately dropped.  
  `backend/src/main/java/com/portfolio/query/ProfileView.java:15-27`
- ProjectView carries slug, name, description, language, tags, status, pinned, liveUrl, repoUrl — the concise list shape, with long-form detail left to ProjectDetailView.  
  `backend/src/main/java/com/portfolio/query/ProjectView.java:6-25`
- listProjects filters server-side, case-insensitively, against name, language, slug, description and tags; a blank or null filter returns everything.  
  `backend/src/main/java/com/portfolio/query/PortfolioQueryService.java:60-92`
- getProject throws IllegalArgumentException when the slug is blank or matches no project.  
  `backend/src/main/java/com/portfolio/query/PortfolioQueryService.java:177-192`
- McpToolOutputBoundaryTest reflects over every method on PortfolioMcpTools annotated with @Tool, unwraps List<X> to X, and asserts the output type is a record.  
  `backend/src/test/java/com/portfolio/mcp/McpToolOutputBoundaryTest.java:37-52,68-78`
- The boundary test recursively walks record components, descending into nested records and List<Record>, and fails on any field name containing password, secret, hash, apikey, token, credential, avatardata, resumedata, rawbytes, bytes, createdat, updatedat, deletedat or internal.  
  `backend/src/test/java/com/portfolio/mcp/McpToolOutputBoundaryTest.java:31-35,53-61,80-98`
- The same test rejects a field named exactly "id" or "uuid", so raw entity identifiers cannot be exposed by a tool output.  
  `backend/src/test/java/com/portfolio/mcp/McpToolOutputBoundaryTest.java:62-63`
- The test asserts at least one @Tool method exists, so deleting the tools does not make it trivially pass, and new tools are covered automatically.  
  `backend/src/test/java/com/portfolio/mcp/McpToolOutputBoundaryTest.java:20-27,45`
- The rate-limit filter is registered via FilterRegistrationBean with addUrlPatterns("/mcp/message") only; the long-lived /mcp/sse stream is left alone.  
  `backend/src/main/java/com/portfolio/mcp/McpServerConfig.java:28-42`
- The filter exists because tools run off the servlet request thread, so the client IP cannot be read inside a @Tool method; the filter sits on the request thread where the container-resolved remote address is available.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:19-31`
- The filter parses the JSON-RPC envelope and throttles only method == "tools/call"; initialize, notifications/initialized, tools/list and ping pass through untouched so discovery always works.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:58-75`
- An unparseable body is not throttled — the filter swallows the parse error and lets the MCP framework reject the message.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:68-70`
- CachedBodyHttpServletRequest buffers the request body into memory and replays it, because a raw servlet input stream is single-pass and reading it in the filter would otherwise starve the framework downstream.  
  `backend/src/main/java/com/portfolio/mcp/CachedBodyHttpServletRequest.java:14-30`
- The LLM-backed match_against_jd tool uses a separate bucket prefix (mcp-match:<ip>) from the cheap data tools (mcp:<ip>), so its cost cannot be amplified by, and does not starve, the data tools.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:35-38,83-86`
- Over the limit the filter returns 429 with a Retry-After header and a JSON body, and logs the tool name, IP and retry hint at WARN.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:86-98`
- The MCP throttle reuses the same in-memory token-bucket RateLimiter as /api/chat and recruiter mode, whose capacity is 10 tokens refilling at 10 per minute.  
  `backend/src/main/java/com/portfolio/chatbot/RateLimiter.java:22-23`
- McpRateLimitFilterTest asserts 15 consecutive tools/list calls from one IP all return 200 — discovery is never throttled.  
  `backend/src/test/java/com/portfolio/mcp/McpRateLimitFilterTest.java:44-50`
- McpRateLimitFilterTest asserts that 13 tool calls from one IP produce exactly 3 responses with status 429, matching the bucket capacity of 10.  
  `backend/src/test/java/com/portfolio/mcp/McpRateLimitFilterTest.java:52-61`
- McpRateLimitFilterTest drains the data-tool bucket with 10 calls and asserts match_against_jd from the same IP still returns 200, proving the buckets are separate.  
  `backend/src/test/java/com/portfolio/mcp/McpRateLimitFilterTest.java:63-72`
- match_against_jd rejects a null or blank job description and caps the input at 8000 characters before any LLM spend.  
  `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:61-63,123-129`
- Tool arguments are screened by AbuseLog.isSuspicious and flagged with warnSuspicious when they look like prompt injection — logged, never blocked.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:77-81`
- Every accepted tool call is logged at INFO with the tool name and IP, and recorded as an MCP_TOOL engagement event, because the filter is the one place on the request thread that sees both the tool name and the real client IP.  
  `backend/src/main/java/com/portfolio/mcp/McpRateLimitFilter.java:99-102`
- MCP_TOOL is one of five passive engagement signal types stored as the enum name.  
  `backend/src/main/java/com/portfolio/telemetry/EngagementType.java:8-13`
- SecurityConfig permits /mcp/** for ALL HTTP methods — not just GET — because the SSE transport needs GET /mcp/sse and POST /mcp/message, and the public GET /** catch-all placed after it would miss the POST.  
  `backend/src/main/java/com/portfolio/security/SecurityConfig.java:112-121`
- The /mcp/** matcher sits after the admin and vault matchers and before the public GET /** catch-all, so the order determines what is exposed.  
  `backend/src/main/java/com/portfolio/security/SecurityConfig.java:100-122`
- Match progress is bridged to MCP as a loggingNotification rather than notifications/progress, because progress notifications need the client's progressToken, which the Spring AI 1.x tool bridge does not surface; notification failures are swallowed so a progress signal can never fail the match.  
  `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:133-157`
- With no MCP exchange in the ToolContext (for example in a unit test) the progress listener degrades to a no-op.  
  `backend/src/main/java/com/portfolio/mcp/PortfolioMcpTools.java:140-143`
- The site's /mcp page publishes the Claude Desktop client config, which points npx -y mcp-remote at ${backend}/mcp/sse, and suggests npx @modelcontextprotocol/inspector for a quick look.  
  `frontend/src/pages/McpPage.tsx:11-30`
- The /mcp page lists the same eight tools with one-line descriptions, matching the @Tool surface.  
  `frontend/src/pages/McpPage.tsx:32-41`
- The MCP package first landed in the repository on 23 June 2026, with the rate-limit filter and the query-layer tools added the same day.  
  `git log --date=short -- backend/src/main/java/com/portfolio/mcp/`
- The fit score returned by match_against_jd is computed by deterministic arithmetic in MatchScoreCalculator rather than by the model, per the repository's own architecture rules.  
  `CLAUDE.md (Architecture > AI surfaces > recruiter)`

## Must not say

- Do not list MCP, Spring AI, RAG, embeddings, pgvector or vector search among Omkar Jadhav's skills or in any bio sentence. The article describes code he wrote; it is a statement about software, not a personal skill claim.
- Do not claim Next.js, FastAPI, ChromaDB, vector databases or RAG pipelines as his skills anywhere in the piece.
- Do not invent usage numbers for the MCP server: no recruiter counts, no tool-call volume, no latency figures, no 'X% faster', no uptime claims.
- Do not describe Nonstop IO's audit-logging implementation, Report Builder internals, table names, schemas or class names. The three resume bullets are the entire public record; if work is mentioned at all, keep it to those words and teach patterns with neutral invented examples.
- Do not imply he contributed to the MCP specification, to Spring AI, or to any upstream project.
- Do not call the in-memory RateLimiter distributed, production-grade at scale, or cluster-safe. It is per-instance and in-memory (RateLimiter javadoc says so).
- Do not say the MCP server has authentication, API keys, or access control. It is deliberately public and unauthenticated because the data is already public.
- Do not say the SSE stream is rate-limited. The filter is registered on /mcp/message only, and the article's whole point is that /mcp/sse is excluded.
- Do not claim Spring AI 1.x supports MCP notifications/progress through the tool bridge. The code works around its absence with loggingNotification.
- Do not state a tool count other than eight, or name tools that are not in PortfolioMcpTools.
- Do not use 'years of experience', 'architected', 'led', 'scaled to', or any staff-engineer survey voice. He is an SDE-I explaining something he built.
- Do not call him a student, fresher, intern (as a current role), or job-seeker.
- Do not write 'recently', 'currently', 'these days' without an attached date.
- Do not open a section or paragraph with a bare pronoun. Name 'Omkar Jadhav' or the concrete subject (the filter, the query layer, the boundary test).
- Do not write marketing voice, exclamation marks, or emoji.
- Do not present untested or aspirational code. Every snippet must be copied from the repository files cited in groundedClaims, trimmed but never reworded into something that would not compile.
- Do not claim the boundary test proves the data is safe in general — it proves the output *shape* carries no forbidden field names; say exactly that.

## Open questions for the owner

- The public backend origin used by the /mcp page comes from VITE_API_URL at build time. Confirm the exact public SSE URL to print in the Claude Desktop config block, or confirm it should stay as a <your-backend>/mcp/sse placeholder.
- router.tsx has no /blog route today. Confirm where this article will live (a new /blog/:slug route, an external host, or a static page) so the internal links and the canonical URL can be written correctly.
- Confirm whether the server has been exercised end-to-end against Claude Desktop and/or @modelcontextprotocol/inspector, and in what month, so the article can say so with a date instead of implying it.
- backend/pom.xml pins Spring AI 1.0.9 as of this branch. Confirm the version at publish time, since the progressToken limitation is version-specific.
- Confirm whether the article may name the AI_DAILY_REQUEST_CAP / per-IP daily cap values, or whether cost ceilings should be described without numbers.
- Confirm the four internal link targets: /mcp (live server page), /projects, /recruiter, /about. If a specific project detail page (e.g. /projects/<slug>) is the better anchor for the portfolio backend, supply the real slug.
- Confirm whether a short 'what MCP is' primer is wanted for readers new to the protocol, or whether the article should assume the reader already knows and go straight to the Spring AI wiring.
