-- Hardening H2/H3: one persisted row per named daily counter. Backs DailyBudgetGuard (the
-- AI-spend ceiling, pentest Fix A) and the contact-form daily cap (pentest #30) so a Render
-- restart (free tier sleeps daily) no longer silently resets the "hard" limits.
CREATE TABLE daily_counter (
    name  varchar(40) NOT NULL,
    day   date        NOT NULL,
    count integer     NOT NULL,
    CONSTRAINT daily_counter_pkey PRIMARY KEY (name)
);
