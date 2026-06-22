-- Resume Q&A: the curated TEXT extracted from the uploaded PDF resume, so it can be
-- chunked + embedded into the RAG corpus and answered over via /api/chat. This is the extracted
-- text only — the raw document bytes stay in resume_data (V6) and are NEVER sent to the model.
ALTER TABLE profile ADD COLUMN IF NOT EXISTS resume_text TEXT;
