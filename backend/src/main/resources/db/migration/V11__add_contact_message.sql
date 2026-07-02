-- Lead capture (Phase A1): contact submissions become durable. The DB write is the system of
-- record; email (Resend) is a best-effort notification afterwards — a mailer failure now means
-- "lead waiting in the admin inbox", not "lead lost forever".
-- source tells the funnels apart (WEB / RECRUITER / CHATBOT / MCP); client IP is stored only
-- as a SHA-256 hash (privacy stance: no raw IPs at rest).
CREATE TABLE contact_message (
    id             uuid                        NOT NULL DEFAULT gen_random_uuid(),
    name           varchar(100)                NOT NULL,
    email          varchar(255)                NOT NULL,
    message        text                        NOT NULL,
    source         varchar(20)                 NOT NULL DEFAULT 'WEB',
    status         varchar(20)                 NOT NULL DEFAULT 'NEW',
    client_ip_hash varchar(64),
    created_at     timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT contact_message_pkey PRIMARY KEY (id)
);

-- The admin inbox lists by status, newest first.
CREATE INDEX idx_contact_message_status_created ON contact_message (status, created_at);
