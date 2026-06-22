-- pgvector: the vector store for RAG retrieval (chatbot/recruiter grounding).
-- Each row is one curated PUBLIC chunk (one project / experience / skill group / profile /
-- later a resume section) plus its embedding. Requires a pgvector-enabled Postgres image
-- (docker-compose uses pgvector/pgvector:pg16); the extension is created here.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS embedding (
    id          uuid                        NOT NULL DEFAULT gen_random_uuid(),
    -- source_type + source_id identify the chunk's origin so a single row can be re-indexed or
    -- deleted when its source changes (Phase D). UNIQUE together => upsert key (no duplicates).
    source_type varchar(64)                 NOT NULL,
    source_id   varchar(255)                NOT NULL,
    chunk_text  text                        NOT NULL,
    -- 768 dims = the pinned Gemini gemini-embedding-001 outputDimensionality (see GeminiEmbeddingClient).
    embedding   vector(768)                 NOT NULL,
    updated_at  timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT embedding_pkey PRIMARY KEY (id),
    CONSTRAINT embedding_source_uq UNIQUE (source_type, source_id)
);

-- HNSW approximate-nearest-neighbour index for cosine distance (the <=> operator with
-- vector_cosine_ops). No training step needed (unlike IVFFlat), good for a small/growing corpus.
CREATE INDEX IF NOT EXISTS embedding_cosine_idx ON embedding USING hnsw (embedding vector_cosine_ops);
