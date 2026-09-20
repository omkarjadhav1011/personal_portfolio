# Content brief: Envelope encryption for file storage in Spring Boot

- **slug:** `envelope-encryption-spring-boot`
- **primary keyword:** envelope encryption aes gcm spring boot s3
- **supporting:** aes-256-gcm java example, per-file data key encryption, DEK KEK key wrapping java, javax.crypto AES/GCM/NoPadding, encrypt files before uploading to s3, single-use download token spring boot, client-side encryption minio java, spring boot encrypt file upload postgres metadata, gcm nonce reuse per-file key, short-lived download link spring security permitAll
- **target length:** 1600 words
- **meta description:** Omkar Jadhav explains envelope encryption in Spring Boot: per-file AES-256-GCM data keys wrapped by a master key, ciphertext in S3, metadata in Postgres.

## Outline

### What Omkar Jadhav's document vault stores, and where
- Open by naming the entity and the thing: Omkar Jadhav built a Secure Document Vault into the Spring Boot 3 backend of his portfolio (Java 21, Postgres, S3-compatible object storage) to hold his own documents.
- State the split in one sentence up front: ciphertext goes to object storage, metadata plus a wrapped per-file key goes to Postgres, and the master key exists only as an environment variable.
- The subsystem is optional. Every bean in the package carries @ConditionalOnProperty(name = "STORAGE_ENDPOINT"), so the application and the test suite boot with no object store, no master key, and no MinIO running. Cite EnvelopeCryptoService.java:30 and DriveStorageConfig.java:30.
- Storage is S3-compatible rather than S3-specific: the S3Client is built with endpointOverride and pathStyleAccessEnabled(true), because MinIO addresses buckets in the URL path. The same client shape works against R2 or S3. DriveStorageConfig.java:39-50.
- Set scope honestly: this is a single-owner vault. drive_file has no owner_id column and the ADMIN identity is implicit (V8__add_drive.sql:5). No sharing, no per-user keys, no multi-tenancy.
- Close the section by naming what the article covers: the envelope shape, why per-file keys, the one rule about the master key, and how a public route safely serves a private file.
- Internal link: /projects (the portfolio's project index) on first mention of the vault.

### Envelope encryption in plain terms: a data key per file, one master key over all of them
- Define the two keys in this codebase's own constants, not in the abstract: a 256-bit AES data key (DEK) generated fresh for each file, and a 32-byte AES master key (KEK) decoded from DRIVE_MASTER_KEY. EnvelopeCryptoService.java:33-38.
- Both layers use AES/GCM/NoPadding with a 12-byte (96-bit) IV and a 128-bit authentication tag. Same primitive twice, different jobs.
- State the division of labour precisely: the DEK encrypts the file's bytes; the KEK encrypts 32 bytes of DEK and nothing else. The master key never touches file content.
- What is persisted is the DEK's ciphertext. The plaintext DEK exists only inside the JVM for the duration of one encrypt or decrypt call.
- Name what an attacker actually obtains from a full dump: ciphertext in the bucket plus a wrapped DEK in the row, which is itself ciphertext, and neither opens without the KEK. EnvelopeCryptoService.java:15-21; V8__add_drive.sql:2-4.
- Be precise about the boundary rather than overselling it: the separation is between (database plus object store) on one side and the environment variable on the other. It is not a claim that the database and the bucket protect each other, because the wrapped key and the object pointer sit in the same row.

### Generating and wrapping the data key with javax.crypto
- Lead with the implementation fact: the whole thing uses the JDK's javax.crypto - KeyGenerator, Cipher, GCMParameterSpec, SecureRandom - and adds no dependency.
- Code block, lifted verbatim from EnvelopeCryptoService.java:80-90 and 102-137: encrypt(), generateDek(), randomIv(), wrapDek(), unwrapDek(), gcm(). Keep the real method bodies; do not paraphrase them into pseudocode.
- Walk the wrapped-key layout explicitly: wrapDek returns wrapIv(12 bytes) || GCM(KEK, wrapIv, dekBytes), and unwrapDek slices the first 12 bytes back off before decrypting. One self-contained paragraph, because this is the detail most reimplementations get wrong.
- Note that the JDK appends the 128-bit GCM tag to each ciphertext, so no separate tag field is needed (EnvelopeCryptoService.java:76-78). Flag now that the schema nonetheless carries an unused enc_tag column, to be picked up in the limits section.
- The constructor validates the master key and fails fast: blank, non-base64, or a decode length other than 32 bytes all throw IllegalStateException at startup, and each message names the fix (openssl rand -base64 32). EnvelopeCryptoService.java:47-66.
- Make the design point: the app either starts with a usable key or does not start at all - there is no silently-degraded mode where files get written unencrypted.

### What goes to Postgres and what goes to the bucket
- Open concretely: the drive_file table holds metadata and two byte arrays, and nothing else about the file's content.
- Code block: the real drive_file DDL from V8__add_drive.sql:25-41, showing enc_iv bytea, enc_wrapped_key bytea, storage_key, is_sensitive, and the folder FK.
- Trace the upload path in order from DriveService.uploadFile (DriveService.java:142-182): reject empty, check the declared content type against an allowlist, verify magic bytes against that declared type, read the bytes, encrypt, put the ciphertext, then save metadata.
- The storage key is a fresh random UUID, not the filename (DriveService.java:163). The object name leaks nothing: no filename, no folder path, no owner.
- StorageService is a thin boundary that only ever moves ciphertext and writes every object as application/octet-stream, so the stored object's type is opaque. StorageService.java:16-19, 25, 36-44.
- Ordering is chosen so a partial failure cannot lie: if the metadata save throws after the object is already stored, the object is deleted (DriveService.java:175-181); on delete, the row goes first and then the object (DriveService.java:184-189), so a row never points at an object that is gone.

### Why a per-file data key beats encrypting everything with one key
- Blast radius first: a leaked DEK decrypts exactly one file. A leaked KEK decrypts all of them. Per-file keys reduce the set of secrets that must be perfectly protected to one, and that one never lands on disk or in a backup.
- Rekeying arithmetic: with an envelope, changing the master key means unwrapping and rewrapping 32 bytes per file. With a single global key it means downloading, decrypting, re-encrypting and re-uploading every object. Present this as the structural argument, and be explicit that the rewrap path is not implemented here (see the next section).
- The GCM nonce argument, the one most write-ups skip: AES-GCM fails catastrophically if a key and IV pair ever repeats. A 96-bit random IV is only comfortable when the number of encryptions under a single key stays small. A fresh 256-bit key per file means each key performs exactly one encryption, so random IVs are a non-issue by construction.
- Show, do not assert: the test asserts that encrypting the same plaintext twice yields a different ciphertext, a different IV and a different wrapped key. EnvelopeCryptoServiceTest.java:44-56. Include this short test as a code block.
- Deletion becomes cheap and final. The wrapped DEK exists in exactly one row; dropping that row destroys the only copy of that file's key, so even an object left behind in the bucket is unreadable. V8__add_drive.sql:33 and DriveService.java:187.
- Cost side, stated plainly: an extra 12 bytes of wrap IV, 32 bytes of wrapped key and a 16-byte tag per file, and one extra GCM operation over 32 bytes. Give the byte arithmetic, not a timing claim.

### The one rule: DRIVE_MASTER_KEY is set once and never rotated casually
- Open with the stake: DRIVE_MASTER_KEY wraps every data key in Omkar Jadhav's vault, so it is the single value whose loss loses everything and whose change breaks everything.
- Say exactly why a casual rotation is fatal in this implementation: EnvelopeCryptoService holds one masterKey field loaded from one env var (EnvelopeCryptoService.java:40-45), and drive_file has no key-id or key-version column (V8__add_drive.sql:25-41). Swap the variable and every previously wrapped DEK becomes unopenable.
- Name the failure mode, because it is not the obvious one: the application starts perfectly. Nothing fails at boot. Files fail one at a time at read time with 'Failed to decrypt document (wrong key or tampered data)'. EnvelopeCryptoService.java:93-100.
- The test states the same fact from the other direction: a service constructed with a different master key throws when handed a payload encrypted under the first. EnvelopeCryptoServiceTest.java:59-66.
- Sketch what real rotation would require, clearly marked as not built: a key-version column on drive_file, both keys loaded simultaneously, and a pass that unwraps each DEK with the old key and rewraps it with the new. Cheap in bytes, but it is a Flyway migration plus a code change - never an environment-variable edit.
- Close with the honest trade-off for a single-owner vault: Omkar Jadhav chose the simpler shape and accepted the constraint, and writing the constraint down is part of accepting it. State the operational rule the way it belongs in a runbook: generate once, store it where the rest of the deployment's secrets live, and treat a change to it as data loss.

### Short-lived single-use tokens: how a public route serves a private file
- Open with the surprising fact: the vault's download endpoint is public. GET /api/drive/download/{token} is permitAll while every other /api/drive route requires ADMIN.
- The matcher order is load-bearing. The permitAll download matcher sits on the line above the hasRole("ADMIN") catch-all for /api/drive/**, and swapping those two lines changes behaviour silently. SecurityConfig.java:106-107.
- Explain why public at all: the browser fetches the file directly, so it cannot carry a bearer header on a plain navigation. The token in the path is the authorization.
- Code block: the real issue() and redeem() from DownloadTokenService.java:50-74.
- Token properties in one paragraph each. Entropy: 32 bytes from SecureRandom, URL-safe base64 without padding (DownloadTokenService.java:29-37). TTL: 5 minutes, long enough to start a download, short enough to limit exposure. Single use: redeem() calls remove() on the first read whether or not the entry is still valid, so any attempt burns it (DownloadTokenService.java:65-74).
- Housekeeping detail worth copying: issue() evicts timed-out entries first, so tokens that are issued and never redeemed cannot pile up. DownloadTokenService.java:51-59, 81-84.
- The two halves of the flow sit on opposite sides of the auth boundary: issuing the token is the ADMIN-authenticated request (DriveController.java:103-109), redeeming it is the public one (DriveController.java:127-141). The response sets Content-Disposition: attachment and Cache-Control: no-cache, no-store, must-revalidate.
- Files marked is_sensitive add a second factor before a token is ever issued: a 6-digit email OTP bound to that file id, and the check fails closed with 403 when email is not configured. DriveService.java:197-205, 226-240; EmailOtpService.java:14-26.
- Contrast with the obvious alternative, a presigned object-store URL: a presigned link is valid for its whole window, reusable by anyone who has it, and points at the bucket directly. This token is burned on first use and the bytes are decrypted server-side, so the object store never serves a client at all. DownloadTokenService.java:15-23.

### What the tests pin down
- Frame the section: these tests construct the classes directly with an injected key and an injected clock, so they need no Postgres, no MinIO and no network.
- Round trip and empty input both return the original bytes. EnvelopeCryptoServiceTest.java:25-41.
- Tampering is detected on both layers: flipping one bit of the ciphertext throws, and flipping the last byte of the wrapped key throws. EnvelopeCryptoServiceTest.java:69-86. Make the point that this is GCM providing authentication, not only confidentiality - a truncated or edited object fails loudly instead of decrypting to garbage.
- Startup guards are tested as behaviour, not just written as comments: missing key, 128-bit key, and non-base64 key each fail fast. EnvelopeCryptoServiceTest.java:88-103.
- Time-dependent behaviour is tested with a clock, not a sleep: the token service takes a LongSupplier, and the tests advance an AtomicLong to assert the token is valid at exactly the 5-minute boundary and rejected one millisecond past it. DownloadTokenServiceTest.java:15-16, 29-43. Include this test as a code block - it is the transferable technique in the whole article.
- Single use is asserted directly: the second redemption fails while still well inside the TTL. DownloadTokenServiceTest.java:19-26. Unknown and null tokens are rejected, and each issue produces a distinct token.

### Limits Omkar Jadhav left in on purpose
- Open by naming the posture: these are known constraints of a single-owner vault, written down rather than papered over.
- Whole-file buffering. uploadFile calls file.getBytes() and download returns a byte[], so the practical file-size ceiling is heap, and there is no streaming encryption path. DriveService.java:154-164, 262-264; StorageService.java:46-51. A streaming version would need chunked GCM with per-chunk nonces, which is a different design, not a tweak.
- The token store is an in-memory ConcurrentHashMap. One instance only: two replicas behind a load balancer would fail every redemption that landed on the wrong node, and a restart drops outstanding tokens. The class says so in its own javadoc and names Redis as the fix. DownloadTokenService.java:20-23, 36.
- The leftover enc_tag column. V8 reserves enc_tag bytea for a separately stored GCM tag, but the implementation appends the tag to the ciphertext, so the column is always null. V8__add_drive.sql:34; EnvelopeCryptoService.java:76-78.
- Object cleanup on folder delete is best-effort: keys are collected before the cascade, then deleted one by one, and a storage failure leaks an object while keeping the database consistent, logged as a warning. DriveService.java:125-138, 326-332.
- Nothing records a download. The download path writes no audit row anywhere in the drive package. One-sentence bridge, kept strictly to the confirmed record: at Nonstop IO Technologies, Omkar Jadhav implemented end-to-end user audit functionality tracking and logging user actions across the application for compliance and traceability - which is exactly the piece this personal vault does not have yet. No detail about that work beyond this sentence.
- Close with the honest ranking of what he would build next if the vault ever had more than one owner: a key-version column so the master key becomes rotatable, then a shared token store, then download audit records.
- Internal links: /experience on the Nonstop IO sentence, /about in the closing line, and the vault's own project page (slug pending) or /projects.

## Grounded claims (claim -> evidence)

- The whole vault subsystem is gated on @ConditionalOnProperty(name = "STORAGE_ENDPOINT"), so the app and the test suite boot with no object store configured.  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:30, StorageService.java:22, DownloadTokenService.java:26, DriveService.java:33, DriveController.java:45, DriveStorageConfig.java:30`
- The object store is S3-compatible and reached via endpointOverride with path-style access enabled, so the same client works against MinIO locally and R2/S3 in production.  
  `backend/src/main/java/com/portfolio/drive/DriveStorageConfig.java:39-50`
- The crypto parameters are AES/GCM/NoPadding, a 12-byte (96-bit) IV, a 128-bit auth tag, a 256-bit per-file data key, and a 32-byte master key.  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:33-38`
- encrypt() generates a fresh 256-bit DEK and a fresh random IV per call, GCM-encrypts the plaintext under the DEK, then wraps the DEK under the master key, returning EncryptedPayload(ciphertext, iv, wrappedKey).  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:72-90`
- The wrapped key is laid out as wrapIv(12 bytes) || GCM(KEK, wrapIv, dekBytes); unwrap slices the first 12 bytes back off before decrypting.  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:114-131`
- The JDK appends the GCM auth tag to the ciphertext, so no separate tag is stored.  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:76-78`
- The constructor fails fast at startup when DRIVE_MASTER_KEY is blank, not valid base64, or does not decode to exactly 32 bytes, and the error message names the generation command (openssl rand -base64 32).  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:47-66`
- A failed decrypt surfaces as IllegalStateException("Failed to decrypt document (wrong key or tampered data)") at read time, not at boot.  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:93-100`
- EnvelopeCryptoService holds exactly one masterKey field loaded from a single env var, and drive_file has no key-id or key-version column, so there is no implemented rotation path.  
  `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:40-45; backend/src/main/resources/db/migration/V8__add_drive.sql:25-41`
- StorageService only ever moves ciphertext and writes every object as application/octet-stream, so the stored object's type is opaque.  
  `backend/src/main/java/com/portfolio/drive/StorageService.java:16-19, 25, 36-44`
- Postgres stores metadata plus enc_iv (bytea) and enc_wrapped_key (bytea) only; the bytes themselves live in object storage under storage_key.  
  `backend/src/main/resources/db/migration/V8__add_drive.sql:25-41`
- The object key is a freshly generated random UUID, not the filename, so the stored object name leaks nothing about the file.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:162-171`
- Upload order is: read bytes, verify magic bytes against the declared content type, encrypt, put ciphertext, then save metadata; if the metadata save throws, the already-stored object is deleted so nothing is orphaned.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:154-181`
- deleteFile removes the metadata row first and then the object, so a row never points at a deleted object.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:184-189`
- Encrypting the same plaintext twice produces a different ciphertext, a different IV, and a different wrapped key — asserted by test.  
  `backend/src/test/java/com/portfolio/drive/EnvelopeCryptoServiceTest.java:44-56`
- A service holding a different master key cannot decrypt a payload encrypted under the first — asserted by test.  
  `backend/src/test/java/com/portfolio/drive/EnvelopeCryptoServiceTest.java:59-66`
- Flipping one bit of the ciphertext, or one bit of the wrapped key, makes decryption throw — the GCM tag detects tampering on both layers.  
  `backend/src/test/java/com/portfolio/drive/EnvelopeCryptoServiceTest.java:69-86`
- Missing, wrong-size (128-bit) and non-base64 master keys are all covered by fail-fast tests.  
  `backend/src/test/java/com/portfolio/drive/EnvelopeCryptoServiceTest.java:88-103`
- Download tokens are 32 random bytes from SecureRandom (256 bits of entropy), URL-safe base64 without padding, held in a ConcurrentHashMap with a 5-minute TTL.  
  `backend/src/main/java/com/portfolio/drive/DownloadTokenService.java:29-37, 51-59`
- redeem() removes the entry on first read whether or not it is still valid, then checks expiry — so any redemption attempt burns the token.  
  `backend/src/main/java/com/portfolio/drive/DownloadTokenService.java:65-74`
- issue() evicts timed-out entries first, so abandoned tokens that are never redeemed cannot accumulate.  
  `backend/src/main/java/com/portfolio/drive/DownloadTokenService.java:51-59, 81-84`
- The token store is in-memory and single-instance by design, documented as needing Redis if the panel ever runs more than one instance.  
  `backend/src/main/java/com/portfolio/drive/DownloadTokenService.java:20-23, 36`
- Tests inject a clock (AtomicLong) so TTL behavior is asserted without sleeping: valid at exactly the 5-minute boundary, rejected one millisecond past it, and a second redemption fails well inside the TTL.  
  `backend/src/test/java/com/portfolio/drive/DownloadTokenServiceTest.java:15-16, 19-43`
- Unknown and null tokens are rejected, and each issue() produces a distinct token — asserted by test.  
  `backend/src/test/java/com/portfolio/drive/DownloadTokenServiceTest.java:45-60`
- download(token) redeems the token or returns 410 Gone, fetches the ciphertext, decrypts it, and returns plaintext with the original filename and content type.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:251-265`
- GET /api/drive/download/** is permitAll and is matched before /api/drive/** hasRole("ADMIN") — the order of those two lines is load-bearing.  
  `backend/src/main/java/com/portfolio/security/SecurityConfig.java:106-107`
- Issuing a token is the ADMIN-authenticated step (GET /api/drive/files/{id}/download-token); redeeming it is the public step (GET /api/drive/download/{token}).  
  `backend/src/main/java/com/portfolio/drive/DriveController.java:103-109, 127-141`
- The download response sets Content-Disposition: attachment with the original filename and Cache-Control: no-cache, no-store, must-revalidate.  
  `backend/src/main/java/com/portfolio/drive/DriveController.java:134-140`
- A file marked is_sensitive requires a valid 6-digit email OTP before a download token is issued, and fails closed with 403 when email is not configured.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:197-205, 226-240; EmailOtpService.java:14-26`
- The design deliberately does not hand the client a presigned object-store URL — the app decrypts and serves the bytes itself.  
  `backend/src/main/java/com/portfolio/drive/DownloadTokenService.java:15-23; DriveService.java:256-265`
- Files are fully buffered in memory: uploadFile calls file.getBytes() and StorageService.get returns a byte[], so there is no streaming encryption path.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:154-164, 262-264; StorageService.java:46-51`
- The V8 migration reserves an enc_tag bytea column for a separately-stored GCM tag, but the implementation appends the tag to the ciphertext, so that column is always null.  
  `backend/src/main/resources/db/migration/V8__add_drive.sql:34; backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:76-78`
- Deleting a folder collects the subtree's object keys before the DB cascade, then deletes objects best-effort — a storage failure leaks an object but keeps the DB consistent, logged as a warning.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:125-138, 326-332`
- The vault is single-owner: drive_file has no owner_id column and the ADMIN identity is implicit.  
  `backend/src/main/resources/db/migration/V8__add_drive.sql:5`
- Nothing in the drive package records a download — there is no audit or engagement write on the download path.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:251-265 (no recorder call; grep for EngagementRecorder across com/portfolio/drive returns no matches)`
- Uploads are restricted to an allowlist of content types and each binary type's magic bytes are checked against the declared type.  
  `backend/src/main/java/com/portfolio/drive/DriveService.java:38-49, 340-363`

## Must not say

- Do not claim the vault is used by anyone other than Omkar Jadhav, or give file counts, user counts, upload volumes, or traffic numbers - none exist in the code.
- Do not claim any security audit, penetration test, or compliance certification (SOC 2, ISO 27001, HIPAA, FIPS validation). Nothing supports that.
- Do not say AWS KMS, Cloud KMS, HashiCorp Vault, or any managed key service is used. The KEK is a base64 env var (DRIVE_MASTER_KEY) read in a constructor.
- Do not describe a master-key rotation procedure as if it is implemented. There is no key-id/key-version column and no dual-key loading - rotation is explicitly not built.
- Do not say the public download route is rate-limited, IP-bound, referrer-checked, or CAPTCHA-gated. It is not. The only protections are the 5-minute TTL and single use.
- Do not say download tokens survive a restart or work across multiple instances. The store is an in-memory ConcurrentHashMap, single-instance by design.
- Do not say the file bytes are 'streamed' through encryption. uploadFile calls file.getBytes() and download returns a byte[] - whole-file buffering.
- Do not claim presigned S3/R2 URLs are issued. The code deliberately avoids them; the app decrypts and serves the bytes.
- Do not claim enc_tag is populated. The column exists in V8 but the JDK appends the tag to the ciphertext, so it is always null.
- Do not claim the database and the object store sit in different providers, accounts, or trust zones - that is not established anywhere in the repo.
- Do not overstate GCM nonce safety as a guarantee. IVs are 96-bit random; explain why per-file keys make that comfortable, do not claim uniqueness is enforced.
- Do not describe or invent anything from the Nonstop IO codebase: no table names, schemas, class names, architecture, or code. The only permitted work reference is the verbatim confirmed line about audit functionality, used as a one-sentence bridge.
- Do not list Next.js, FastAPI, ChromaDB, vector databases, or RAG pipelines as Omkar Jadhav's skills. This article does not need them at all - keep them out entirely.
- Do not call him a student, fresher, intern (as a current role), or job-seeker. He is a Software Development Engineer I at Nonstop IO Technologies.
- Do not inflate: no 'years of experience', 'architected', 'at scale', 'led the design', 'enterprise-grade security'. He is an SDE-I explaining code he wrote.
- Do not invent performance numbers - no encryption overhead in ms, no throughput figures, no 'adds under 5ms'.
- Do not claim the vault is deployed to production on Render/R2 unless the owner confirms it (see open questions). 'Runs against MinIO locally' is the safe framing.
- No marketing voice, no 'in today's fast-paced world', no exclamation marks, no emoji.
- Do not open a section or paragraph with a bare pronoun. Each retrieved paragraph must name Omkar Jadhav or the concrete subject.

## Open questions for the owner

- Does the blog live at /blog/<slug> on the portfolio? router.tsx has no blog route today (only /, /about, /projects, /projects/:slug, /experience, /education, /resume, /recruiter, /mcp), so the publishing route and canonical URL need confirming before the internal links are finalized.
- What is the project slug for the Secure Document Vault on /projects/:slug? The planned link to a vault project detail page needs the real slug; otherwise fall back to /projects.
- Is the vault actually deployed (Render backend + Cloudflare R2), or does it currently run only locally against MinIO? The draft must not imply production deployment without this.
- Is it acceptable to publish the exact crypto parameters and key-generation command (openssl rand -base64 32)? They are already in source and in the error message, and disclosing parameters is standard practice, but confirm he is comfortable.
- Should the article name the unused enc_tag column as a known wart? It is honest and provable, but it is also a public admission of a leftover in his own schema.
- Should the 'what I would change' closing mention concrete next steps (key-version column, Redis-backed token store, download audit logging)? Confirm he wants those framed as not-built rather than planned with dates.
- What publication date should be stamped on the article for the dated statements the extraction rules require?
- Is the repository public? If a reader can follow along in the source, the article should link the repo; if not, the code blocks must stand alone.
