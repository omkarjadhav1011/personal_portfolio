-- Lead capture (Phase D1): one append-only stream for all passive engagement signals
-- (resume downloads, recruiter matches/letters, MCP tool calls, chat sessions — D2 writes them).
-- Widens the ai_usage_event idea from future_plan A2 into a general engagement stream: the same
-- table serves AI-cost visibility AND interest signals. Privacy stance: first-party only, no
-- cookies, client IP stored only as a SHA-256 hash. score is optional per-event context
-- (e.g. a match's fit score).
CREATE TABLE engagement_event (
    id             uuid                        NOT NULL DEFAULT gen_random_uuid(),
    event_type     varchar(40)                 NOT NULL,
    detail         varchar(200),
    client_ip_hash varchar(64),
    score          integer,
    created_at     timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT engagement_event_pkey PRIMARY KEY (id)
);

-- The D3 dashboard/digest aggregates counts by type over a time window.
CREATE INDEX idx_engagement_event_type_created ON engagement_event (event_type, created_at);
