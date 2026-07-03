-- Lead capture (Phase C1): recruiters who ran a JD match can leave their email at the moment
-- of maximum intent. The match context (fit_score, matched_skills, jd_excerpt) is stored with
-- the lead so follow-up writes itself ("you matched 82% on X, Y, Z") — it is client-echoed,
-- untrusted display data for the owner's eyes only. Client IP stored only as a SHA-256 hash.
-- matched_skills holds a JSON array as text (repo convention: StringListJsonConverter).
CREATE TABLE recruiter_lead (
    id             uuid                        NOT NULL DEFAULT gen_random_uuid(),
    email          varchar(255)                NOT NULL,
    company        varchar(150),
    note           varchar(1000),
    fit_score      integer,
    matched_skills text,
    jd_excerpt     varchar(500),
    status         varchar(20)                 NOT NULL DEFAULT 'NEW',
    client_ip_hash varchar(64),
    created_at     timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT recruiter_lead_pkey PRIMARY KEY (id)
);

-- The admin inbox (C3) lists leads by status, newest first.
CREATE INDEX idx_recruiter_lead_status_created ON recruiter_lead (status, created_at);
