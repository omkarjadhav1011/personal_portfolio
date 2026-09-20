---
title: "Envelope encryption for file storage in Spring Boot"
slug: "envelope-encryption-spring-boot"
description: "Omkar Jadhav explains envelope encryption in Spring Boot: per-file AES-256-GCM data keys wrapped by a master key, ciphertext in S3, metadata in Postgres."
primaryKeyword: "envelope encryption aes gcm spring boot s3"
status: draft-unverified
grounded: true
wordCount: 1836
verifyLensesNotRun:
  - "grounding"
  - "withdrawn-and-inflation"
  - "proprietary-leak"
  - "seo-structure"
claimsNeedingConfirmation:
  - "Publishing route and canonical URL: frontend/src/router.tsx has no /blog route today (only /, /about, /projects, /projects/:slug, /experience, /education, /resume, /recruiter, /mcp). The publishing path for this article and its canonical URL need to be decided before the internal links and any sitemap entry are finalized."
  - "Vault project slug: there is no confirmed /projects/:slug page for the Secure Document Vault, so the first internal link points at /projects. Swap it for the real slug if a vault project page exists or gets added."
  - "Deployment status: the article deliberately avoids saying the vault runs in production. It says the S3-compatible client shape works against R2 or S3 by changing the endpoint, without claiming that is deployed. Confirm whether the vault is actually deployed (Render backend + Cloudflare R2) or currently runs only locally against MinIO, and adjust that one sentence if needed."
  - "Publishing exact crypto parameters and the key-generation command: the article states AES/GCM/NoPadding, 96-bit IV, 128-bit tag, 256-bit DEK, 32-byte KEK, and quotes the startup error message containing 'openssl rand -base64 32'. All of this is already in the repo source and in a runtime error message, and disclosing parameters is standard practice, but confirm you are comfortable publishing it."
  - "Naming the unused enc_tag column as a known wart: the Limits section states publicly that V8 reserves enc_tag bytea but the implementation appends the tag to the ciphertext, so the column is always null. Honest and provable, but it is a public admission of a leftover in your own schema."
  - "Framing of not-built work: the article says a key-version column, a shared (Redis-backed) token store, and download audit records are not built, and lists them as the order of work 'if the vault ever had more than one owner'. No dates or commitments are attached. Confirm you want them mentioned at all."
  - "Publication date: the article opens with 'since August 2026' (a confirmed employment fact) but carries no publication date of its own. Decide the date to stamp on the post, since the site's extraction rules favour dated statements."
  - "Repository visibility: if the portfolio repo is public, the article should link it so readers can follow the cited file paths and line numbers; if it is private, the code blocks stand alone as written and the file-path citations are informational only."
  - "Line-number citations: all cited paths and line numbers were checked against the working tree on branch seo/overhaul on 20 Sept 2026. Later edits to the drive package will drift these numbers, so re-verify before publishing if the branch moves."
---
# Envelope encryption for file storage in Spring Boot

Omkar Jadhav has been a Software Development Engineer I at Nonstop IO Technologies in Kharadi, Pune since August 2026. This article walks through one subsystem of his personal portfolio backend: a Secure Document Vault that encrypts every file before it reaches object storage. The pattern is envelope encryption — a fresh AES-256 data key per file, wrapped under one long-lived master key — implemented with nothing but the JDK's `javax.crypto`.

## What Omkar Jadhav's document vault stores, and where

Omkar Jadhav built the vault into the Spring Boot 3 backend of his portfolio (Java 21, Postgres, S3-compatible object storage) to hold his own documents. It is a feature of [the portfolio project](/projects), not a product.

The split is the whole design in one sentence: ciphertext goes to object storage, metadata plus a wrapped per-file key goes to Postgres, and the master key exists only as an environment variable in the running application.

The subsystem is optional. Every bean in the `com.portfolio.drive` package carries `@ConditionalOnProperty(name = "STORAGE_ENDPOINT")` — see `backend/src/main/java/com/portfolio/drive/EnvelopeCryptoService.java:30` and `DriveStorageConfig.java:30` — so the application and the test suite boot with no object store, no master key, and no MinIO running.

Storage is S3-compatible rather than S3-specific. The `S3Client` is built with `endpointOverride` and `pathStyleAccessEnabled(true)`, because MinIO addresses buckets in the URL path rather than as a virtual host (`DriveStorageConfig.java:39-50`). The same client shape works against Cloudflare R2 or AWS S3 by changing the endpoint.

Scope, stated honestly: this is a single-owner vault. `drive_file` has no `owner_id` column and the ADMIN identity is implicit (`backend/src/main/resources/db/migration/V8__add_drive.sql:5`). There is no sharing, no per-user key hierarchy, and no multi-tenancy.

The rest of this article covers four things: the envelope shape, why a per-file key is worth the extra bytes, the one operational rule about the master key, and how a public HTTP route can safely serve a private file.

## Envelope encryption in plain terms: a data key per file, one master key over all of them

Envelope encryption in this codebase means two AES keys with different lifetimes. The data key (DEK) is 256 bits, generated fresh for every file. The master key (KEK) is 32 bytes, decoded from the `DRIVE_MASTER_KEY` environment variable at startup (`EnvelopeCryptoService.java:33-38`).

Both layers use the same primitive: `AES/GCM/NoPadding` with a 12-byte (96-bit) IV and a 128-bit authentication tag. Same algorithm twice, two different jobs.

The division of labour is strict. The DEK encrypts the file's bytes. The KEK encrypts 32 bytes of DEK and nothing else. The master key never touches file content.

What is persisted is the DEK's ciphertext, never the DEK. The plaintext data key exists only inside the JVM, for the duration of a single `encrypt` or `decrypt` call.

A full dump of both stores therefore yields ciphertext in the bucket plus a wrapped DEK in the database row — and the wrapped DEK is itself ciphertext. Neither opens without the KEK, which lives only in the application's environment (`EnvelopeCryptoService.java:15-21`, `V8__add_drive.sql:2-4`).

The boundary is worth stating precisely rather than overselling. The separation is between the two stores on one side and the environment variable on the other. It is not a claim that the database and the bucket protect each other, because the wrapped key and the pointer to the object sit in the same row.

## Generating and wrapping the data key with javax.crypto

The implementation adds no dependency. `KeyGenerator`, `Cipher`, `GCMParameterSpec` and `SecureRandom` come from the JDK, and the entire crypto surface is one class (`EnvelopeCryptoService.java`).

```java
public EncryptedPayload encrypt(byte[] plain) {
    try {
        SecretKey dek = generateDek();
        byte[] iv = randomIv();
        byte[] ciphertext = gcm(Cipher.ENCRYPT_MODE, dek, iv, plain);
        byte[] wrappedKey = wrapDek(dek);
        return new EncryptedPayload(ciphertext, iv, wrappedKey);
    } catch (Exception e) {
        throw new IllegalStateException("Failed to encrypt document", e);
    }
}

private SecretKey generateDek() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance(AES);
    kg.init(DEK_LENGTH_BITS, random);
    return kg.generateKey();
}

private byte[] randomIv() {
    byte[] iv = new byte[IV_LENGTH];
    random.nextBytes(iv);
    return iv;
}

/** Wraps the DEK as wrapIv(12) || GCM(KEK, wrapIv, DEK-bytes) (tag appended). */
private byte[] wrapDek(SecretKey dek) throws Exception {
    byte[] wrapIv = randomIv();
    byte[] wrapped = gcm(Cipher.ENCRYPT_MODE, masterKey, wrapIv, dek.getEncoded());
    byte[] out = new byte[wrapIv.length + wrapped.length];
    System.arraycopy(wrapIv, 0, out, 0, wrapIv.length);
    System.arraycopy(wrapped, 0, out, wrapIv.length, wrapped.length);
    return out;
}

private SecretKey unwrapDek(byte[] wrappedKey) throws Exception {
    byte[] wrapIv = new byte[IV_LENGTH];
    System.arraycopy(wrappedKey, 0, wrapIv, 0, IV_LENGTH);
    byte[] wrapped = new byte[wrappedKey.length - IV_LENGTH];
    System.arraycopy(wrappedKey, IV_LENGTH, wrapped, 0, wrapped.length);
    byte[] dekBytes = gcm(Cipher.DECRYPT_MODE, masterKey, wrapIv, wrapped);
    return new SecretKeySpec(dekBytes, AES);
}

private byte[] gcm(int mode, SecretKey key, byte[] iv, byte[] input) throws Exception {
    Cipher cipher = Cipher.getInstance(GCM);
    cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
    return cipher.doFinal(input);
}
```

The wrapped-key layout deserves its own paragraph, because it is the detail most reimplementations get wrong. `wrapDek` returns `wrapIv(12 bytes) || GCM(KEK, wrapIv, dekBytes)` as a single byte array, and `unwrapDek` slices those first 12 bytes back off before decrypting the remainder. The wrap IV travels with the wrapped key; it is not stored in its own column and it is not reused from the file's own IV.

The JDK appends the 128-bit GCM tag to each ciphertext it produces, so no separate tag field is needed anywhere in this design (`EnvelopeCryptoService.java:76-78`). The schema nonetheless carries an unused `enc_tag` column, which the limits section below returns to.

The constructor fails fast. A blank `DRIVE_MASTER_KEY`, a value that is not valid base64, or a value that decodes to any length other than 32 bytes each throws `IllegalStateException` at startup, and every message names the fix (`EnvelopeCryptoService.java:47-66`):

```java
if (key.length != KEK_LENGTH_BYTES) {
    throw new IllegalStateException(
            "DRIVE_MASTER_KEY must decode to exactly 32 bytes (AES-256); got " + key.length
            + ". Generate one with: openssl rand -base64 32");
}
```

The design point behind that guard: the application either starts with a usable key or does not start at all. There is no silently degraded mode in which files get written unencrypted.

## What goes to Postgres and what goes to the bucket

The `drive_file` table holds metadata and two byte arrays, and nothing else about the file's content (`V8__add_drive.sql:25-41`):

```sql
CREATE TABLE drive_file (
    id                uuid                        NOT NULL DEFAULT gen_random_uuid(),
    folder_id         uuid,
    original_filename varchar(255)                NOT NULL,
    content_type      varchar(100)                NOT NULL,
    size_bytes        bigint                      NOT NULL,
    storage_key       varchar(100)                NOT NULL,   -- UUID object key in MinIO/S3
    enc_iv            bytea                       NOT NULL,   -- AES-GCM IV (12 bytes)
    enc_wrapped_key   bytea                       NOT NULL,   -- DEK wrapped by DRIVE_MASTER_KEY
    enc_tag           bytea,                                  -- GCM tag, if stored separately
    is_sensitive      boolean                     NOT NULL DEFAULT false,
    created_at        timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT drive_file_pkey PRIMARY KEY (id),
    CONSTRAINT drive_file_folder_fk FOREIGN KEY (folder_id)
        REFERENCES drive_folder (id) ON DELETE CASCADE,
    CONSTRAINT drive_file_storage_key_uniq UNIQUE (storage_key)
);
```

`DriveService.uploadFile` runs the steps in a deliberate order (`DriveService.java:142-182`): reject an empty upload, check the declared content type against an allowlist, verify the file's magic bytes against that declared type, read the bytes, encrypt, put the ciphertext, then save the metadata row.

The storage key is a freshly generated random UUID, not the filename (`DriveService.java:163`). The object name in the bucket leaks nothing — no filename, no folder path, no owner.

`StorageService` is a thin boundary that only ever moves ciphertext, and it writes every object as `application/octet-stream`, so the stored object's type is opaque (`StorageService.java:16-19, 25, 36-44`).

The ordering is chosen so a partial failure cannot lie. If the metadata save throws after the object is already stored, the object is deleted rather than left orphaned (`DriveService.java:175-181`). On delete, the row goes first and the object second (`DriveService.java:184-189`), so a surviving row never points at an object that is already gone.

## Why a per-file data key beats encrypting everything with one key

Blast radius is the first argument. A leaked DEK decrypts exactly one file. A leaked KEK decrypts all of them. Per-file keys shrink the set of secrets that must be perfectly protected down to one value, and that one value never lands on disk or inside a backup.

Rekeying arithmetic is the second. With an envelope, changing the master key means unwrapping and rewrapping 32 bytes per file. With a single global key it means downloading, decrypting, re-encrypting and re-uploading every object. That is the structural argument for the shape — though the rewrap path is not implemented in this vault, as the next section says plainly.

The GCM nonce argument is the one most write-ups skip. AES-GCM fails catastrophically if a key and IV pair ever repeats, and a 96-bit random IV is only comfortable while the number of encryptions under a single key stays small. A fresh 256-bit key per file means each key performs exactly one encryption, so random IVs stop being a counting problem by construction. That is a structural reason the risk is low here, not an enforced uniqueness guarantee.

The test asserts the observable half of that (`backend/src/test/java/com/portfolio/drive/EnvelopeCryptoServiceTest.java:44-56`):

```java
@Test
void freshKeyAndIvPerCallSoCiphertextNeverRepeats() {
    byte[] plain = "secret".getBytes(StandardCharsets.UTF_8);

    EncryptedPayload a = crypto.encrypt(plain);
    EncryptedPayload b = crypto.encrypt(plain);

    // Ciphertext is not the plaintext...
    assertFalse(Arrays.equals(plain, a.ciphertext()));
    // ...and the same plaintext yields different ciphertext, IV and wrapped DEK each time.
    assertFalse(Arrays.equals(a.ciphertext(), b.ciphertext()));
    assertFalse(Arrays.equals(a.iv(), b.iv()));
    assertFalse(Arrays.equals(a.wrappedKey(), b.wrappedKey()));
}
```

Deletion also becomes cheap and final. The wrapped DEK exists in exactly one row, so dropping that row destroys the only copy of that file's key (`V8__add_drive.sql:33`, `DriveService.java:187`). Even an object left behind in the bucket is unreadable afterwards.

The cost is small and countable: per file, 12 bytes of wrap IV plus 32 bytes of wrapped DEK plus a 16-byte wrap tag — 60 bytes in `enc_wrapped_key` — alongside the 12-byte `enc_iv`, and a 16-byte tag appended to the object itself. That is 88 extra bytes and one extra GCM operation over 32 bytes.

## The one rule: DRIVE_MASTER_KEY is set once and never rotated casually

`DRIVE_MASTER_KEY` wraps every data key in the vault. It is the single value whose loss loses everything and whose change breaks everything.

A casual rotation is fatal in this implementation for a concrete reason: `EnvelopeCryptoService` holds exactly one `masterKey` field loaded from one environment variable (`EnvelopeCryptoService.java:40-45`), and `drive_file` has no key-id or key-version column (`V8__add_drive.sql:25-41`). Swap the variable and every previously wrapped DEK becomes unopenable.

The failure mode is not the obvious one. The application starts perfectly — nothing fails at boot, because a new 32-byte key passes the startup guard. Files then fail one at a time at read time with `Failed to decrypt document (wrong key or tampered data)` (`EnvelopeCryptoService.java:93-100`).

A test states the same fact from the other direction: a service constructed with a different master key throws when handed a payload encrypted under the first (`EnvelopeCryptoServiceTest.java:59-66`).

Real rotation, clearly marked as not built here, would need three things: a key-version column on `drive_file`, both keys loaded at once, and a pass that unwraps each DEK with the old key and rewraps it with the new. Cheap in bytes, but it is a Flyway migration plus a code change — never an environment-variable edit.

For a single-owner vault, Omkar Jadhav took the simpler shape and accepted the constraint, and writing the constraint down is part of accepting it. As a runbook line: generate the key once, store it wherever the rest of the deployment's secrets live, and treat any change to it as data loss.

## Short-lived single-use tokens: how a public route serves a private file

The vault's download endpoint is public. `GET /api/drive/download/**` is `permitAll`, while every other `/api/drive/**` route requires ADMIN.

The matcher order is load-bearing. The `permitAll` download matcher sits on the line directly above the `hasRole("ADMIN")` catch-all for the rest of the vault (`backend/src/main/java/com/portfolio/security/SecurityConfig.java:106-107`), and swapping those two lines changes behaviour silently.

The route is public at all because a browser navigating to a download cannot attach an `Authorization` header. The token in the path is the authorization.

```java
/** Issues a fresh single-use token for fileId and returns it. */
public String issue(UUID fileId) {
    long now = clock.getAsLong();
    evictExpired(now);
    byte[] buf = new byte[TOKEN_BYTES];
    random.nextBytes(buf);
    String token = encoder.encodeToString(buf);
    tokens.put(token, new Entry(fileId, now + TTL_MS));
    return token;
}

public Optional<UUID> redeem(String token) {
    if (token == null) {
        return Optional.empty();
    }
    Entry entry = tokens.remove(token); // single-use: removed on first read, valid or not
    if (entry == null || clock.getAsLong() > entry.expiresAtMs()) {
        return Optional.empty();
    }
    return Optional.of(entry.fileId());
}
```

Entropy: each token is 32 random bytes from `SecureRandom`, URL-safe base64 without padding (`DownloadTokenService.java:29-37`).

Lifetime: 5 minutes — long enough to start a download, short enough to limit exposure.

Single use: `redeem` calls `remove` on the first read whether or not the entry is still valid, so any redemption attempt burns the token (`DownloadTokenService.java:65-74`).

Housekeeping worth copying: `issue` evicts timed-out entries before adding a new one, so tokens that are issued and never redeemed cannot accumulate in the map (`DownloadTokenService.java:51-59, 81-84`).

The two halves of the flow sit on opposite sides of the auth boundary. Issuing is the ADMIN-authenticated request, `GET /api/drive/files/{id}/download-token` (`DriveController.java:103-109`); redeeming is the public one, `GET /api/drive/download/{token}` (`DriveController.java:127-141`). The download response sets `Content-Disposition: attachment` with the original filename and `Cache-Control: no-cache, no-store, must-revalidate`.

A file marked `is_sensitive` adds a second factor before a token is ever issued: a 6-digit code emailed to the owner and bound to that file id. The check fails closed — when email is not configured, a sensitive file returns 403 rather than being released (`DriveService.java:197-205, 226-240`; `EmailOtpService.java:14-26`).

Contrast this with the obvious alternative, a presigned object-store URL. A presigned link stays valid for its entire window, is reusable by anyone who has it, and points at the bucket directly. This token is burned on first use, and the bytes are decrypted server-side, so the object store never serves a client at all (`DownloadTokenService.java:15-23`).

## What the tests pin down

The vault's unit tests construct these classes directly with an injected key and an injected clock, so they need no Postgres, no MinIO and no network.

Round trip and empty input both return the original bytes (`EnvelopeCryptoServiceTest.java:25-41`).

Tampering is detected on both layers: flipping one bit of the ciphertext throws, and flipping the last byte of the wrapped key throws (`EnvelopeCryptoServiceTest.java:69-86`). That is GCM providing authentication, not only confidentiality — a truncated or edited object fails loudly instead of decrypting to garbage.

Startup guards are tested as behaviour rather than described in comments: a missing key, a 128-bit key, and a non-base64 key each fail fast (`EnvelopeCryptoServiceTest.java:88-103`).

Time-dependent behaviour is tested with a clock, not a sleep. `DownloadTokenService` takes a `LongSupplier`, and the tests advance an `AtomicLong` (`backend/src/test/java/com/portfolio/drive/DownloadTokenServiceTest.java:15-16, 29-43`):

```java
private final AtomicLong now = new AtomicLong(1_000_000L);
private final DownloadTokenService tokens = new DownloadTokenService(now::get);

@Test
void expiredTokenIsRejected() {
    String token = tokens.issue(UUID.randomUUID());
    now.addAndGet(5 * 60 * 1000L + 1); // advance just past the 5-minute TTL

    assertTrue(tokens.redeem(token).isEmpty());
}

@Test
void tokenValidJustBeforeExpiry() {
    UUID fileId = UUID.randomUUID();
    String token = tokens.issue(fileId);
    now.addAndGet(5 * 60 * 1000L); // exactly at the boundary, not past it

    assertEquals(Optional.of(fileId), tokens.redeem(token));
}
```

Single use is asserted directly: the second redemption fails while still well inside the TTL (`DownloadTokenServiceTest.java:19-26`). Unknown and null tokens are rejected, and each `issue` produces a distinct token.

## Limits Omkar Jadhav left in on purpose

These are known constraints of a single-owner vault, written down rather than papered over.

Whole-file buffering. `uploadFile` calls `file.getBytes()` and the download path returns a `byte[]` (`DriveService.java:154-164, 262-264`; `StorageService.java:46-51`), so the practical file-size ceiling is heap and there is no streaming encryption path. A streaming version would need chunked GCM with per-chunk nonces, which is a different design rather than a tweak.

The token store is an in-memory `ConcurrentHashMap`. One instance only: two replicas behind a load balancer would fail every redemption that landed on the wrong node, and a restart drops outstanding tokens. The class says so in its own javadoc and names Redis as the fix (`DownloadTokenService.java:20-23, 36`).

The leftover `enc_tag` column. V8 reserves `enc_tag bytea` for a separately stored GCM tag, but the implementation appends the tag to the ciphertext, so the column is always null (`V8__add_drive.sql:34`; `EnvelopeCryptoService.java:76-78`).

Object cleanup on folder delete is best-effort. Keys are collected before the database cascade, then deleted one by one; a storage failure leaks an object while keeping the database consistent, and is logged as a warning (`DriveService.java:125-138, 326-332`).

Nothing records a download. The download path writes no audit row anywhere in the drive package. At Nonstop IO Technologies, Omkar Jadhav implemented end-to-end user audit functionality tracking and logging user actions across the application for compliance and traceability — which is exactly the piece this personal vault does not have yet. More about that role is on [the experience page](/experience).

If the vault ever had more than one owner, the honest order of work would be: a key-version column so the master key becomes rotatable, then a shared token store, then download audit records.

## Closing

Envelope encryption is worth its 88 bytes per file mostly for what it buys structurally: one secret to protect instead of many, a single-file blast radius, deletion that destroys a key rather than chasing bytes, and per-file keys that keep random 96-bit nonces out of counting territory. The parts that are cheap to get wrong — the wrapped-key layout, the fail-fast key guard, the matcher order in front of a public route — are the parts worth copying carefully.

Omkar Jadhav writes up pieces of his own systems as he builds them; more of his work is on [the about page](/about).
