# Plan: OAuth2 (Google + GitHub) login + Admin-Panel Hardening + TOTP MFA

A single document for Claude Code to follow top-to-bottom. Each phase has a concrete deliverable and a **Verify** step. Implement in order; do not start a phase until the previous phase's Verify passes. The goal: **no outsider can reach the admin panel** even with a valid Google/GitHub account or a leaked password.

> **Agent rules**
> - Keep the security chain **STATELESS**, CSRF off (token-based), exactly as today.
> - Never persist the JWT anywhere but in-memory in the SPA (`frontend/src/store/auth.ts`).
> - The JWT must **never** appear in a URL — only a single-use 60s `code` does.
> - OAuth2 registrations + MFA must be **optional at startup** so local/test boots without provider keys or a configured TOTP secret.
> - Stop and report if any Verify step fails.

---

## Context

The admin panel authenticates today via username/password (`ADMIN_USERNAME` + `ADMIN_PASSWORD_HASH` → Bearer JWT). We are adding three things, layered:

1. **OAuth2 login** (Google + GitHub) — sign in without typing a password, with an **email allowlist** so only the owner can enter even after a valid provider sign-in. Password login stays as fallback.
2. **Admin-panel hardening** — close the gaps that let an outsider probe or brute-force the panel.
3. **TOTP MFA** (Google Authenticator / Authy) — a second factor on top of *both* password and OAuth2 login, so a leaked password **or** a hijacked Google account still cannot get in without the owner's authenticator device.

The hard constraint: the SPA holds the JWT **in memory only** — never persisted (XSS exfiltration defense). OAuth2 is a full-page redirect, so the in-memory token is wiped during the round-trip. The backend hands the freshly-minted JWT back via a **short-lived one-time code** (token never in the URL).

**Defense-in-depth summary (what stops an outsider):**

| Attack | Defense (phase) |
|---|---|
| Random visitor hits `/admin/**` API | All admin endpoints `hasRole("ADMIN")`; security headers; no info-leak errors (P5) |
| Valid Google/GitHub account, not the owner | Email **allowlist** — rejected after provider sign-in (P2) |
| Leaked admin password | TOTP MFA second factor required (P6) |
| Hijacked Google/GitHub account | TOTP MFA second factor required (P6) |
| Brute-force password / OTP | Per-IP rate limiting + lockout on the login + exchange + MFA-verify endpoints (P5) |
| Token theft via URL/logs | One-time 60s single-use code; JWT never in URL (P1) |
| Stolen device / replayed OTP | TOTP 30s window + used-step cache; recovery codes single-use (P6) |

---

## Phase 0 — Baseline & guardrails (no behavior change)

- Read and note the current shape of: `SecurityConfig.java`, `AuthController.java`, `JwtService`, `JwtAuthFilter`, `JwtSessionGuard`, `RateLimiter`, `frontend/src/store/auth.ts`, `src/lib/api.ts`, `src/router.tsx`, `RequireAuth.tsx`.
- Confirm the existing test style in `JwtServiceTest.java` and `RequireAuth.test.tsx` (new tests must match).
- Create a feature branch. Do **not** touch behavior yet.
- **Verify:** `cd backend && mvn test` and `cd frontend && npm run test` both green on a clean checkout before any change.

---

## Phase 1 — One-time-code plumbing (backend foundation for the redirect)

The reusable mechanism every redirect-based login needs: hand the SPA a JWT after a full-page redirect without putting the token in the URL.

### New class — `com.portfolio.security.OneTimeCodeStore`
- In-memory `code → (jwt, expiresIn, expiryInstant)` map; **single-use**, **60s TTL**.
- `issue(token, expiresIn) → code` (cryptographically random, URL-safe, ≥128 bits).
- `redeem(code) → Optional<LoginResponse>` — removes the entry on read (single-use), returns empty if missing/expired.
- Same in-memory, single-instance scope already accepted for `JwtSessionGuard` — document it identically.

### Exchange endpoint — `AuthController.java`
- `POST /api/auth/oauth/exchange` taking `{code}` → `OneTimeCodeStore.redeem` → existing `LoginResponse {token, expiresIn}`; `400/404` on unknown/expired.
- Reuse the existing per-IP `RateLimiter` guard used in `login`.

### Wire-up — `SecurityConfig.java`
- Permit `POST "/api/auth/oauth/exchange"` **before** the `anyRequest().hasRole("ADMIN")` catch-all.
- Register `OneTimeCodeStore` as a bean.

- **Verify:** `OneTimeCodeStoreTest` (issue/redeem, single-use returns empty on 2nd read, expiry); `AuthControllerTest` for exchange (valid code → token; unknown/expired → 4xx). Both pass.

---

## Phase 2 — OAuth2 login (Google + GitHub) with email allowlist

### Dependency — `pom.xml`
- Add `spring-boot-starter-oauth2-client`.

### Config — `application.yml` + `docker-compose.yml`
- `spring.security.oauth2.client.registration.{google,github}` with `client-id`/`client-secret` from env. GitHub registration requests scope `read:user,user:email`.
- New env vars (documented like the existing `ADMIN_*` block, with `:?` guards in compose): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `OAUTH_ALLOWED_EMAILS` (comma-separated), `APP_FRONTEND_URL` (dev `http://localhost:5173` / compose `http://localhost:3000` / Vercel prod).
- `server.forward-headers-strategy: framework` so redirect-uri is built with `https` behind Render's proxy.
- Registrations must be **optional**: only auto-configure when client-ids are present (guard via empty-string defaults + conditional, or omit registrations when env unset) so local/test boots without keys.

### New classes — `com.portfolio.security`
- **`HttpCookieOAuth2AuthorizationRequestRepository`** — stores the OAuth2 authorization request (state/PKCE) in a short-lived `httpOnly` / `SameSite=Lax` cookie instead of an HTTP session, so the chain stays `STATELESS`.
- **`GitHubEmailOAuth2UserService`** — extends `DefaultOAuth2UserService`; for the `github` registration, calls `https://api.github.com/user/emails`, picks the **primary verified** email, exposes it as the `email` attribute. Google uses the default OIDC user service.
- **`OAuth2SuccessHandler`** — extracts email (Google: `email` claim; GitHub: above), checks `OAUTH_ALLOWED_EMAILS` (case-insensitive, trimmed). **Allowed** → `jwtService.generate(email)` → `OneTimeCodeStore.issue` → redirect `{APP_FRONTEND_URL}/admin/oauth/callback?code=…`; clear the request cookie. **Rejected** → `OAuth2FailureHandler`. *(If MFA is live — Phase 6 — issue a PRE_AUTH code instead; see Phase 6.)*
- **`OAuth2FailureHandler`** — redirect `{APP_FRONTEND_URL}/admin/login?error=oauth_denied`.

### Wire-up — `SecurityConfig.java`
```
.oauth2Login(o -> o
    .authorizationEndpoint(a -> a.authorizationRequestRepository(cookieRepo))
    .userInfoEndpoint(u -> u.userService(gitHubEmailService))  // Google: default oidcUserService
    .successHandler(successHandler)
    .failureHandler(failureHandler))
```
- `authorizeHttpRequests`: permit `"/oauth2/**"`, `"/login/oauth2/**"` (the exchange endpoint was permitted in P1), all **before** `anyRequest().hasRole("ADMIN")`.
- Beans for the cookie repo, handlers, GitHub user service.

### Flow
1. SPA → `window.location.assign({BASE_URL}/oauth2/authorization/google|github)`.
2. Spring runs the Authorization-Code flow → callback `/login/oauth2/code/{provider}` → load email.
3. Success handler: allowlist → JWT → one-time code → redirect to callback; else error redirect.
4. SPA callback → `POST /api/auth/oauth/exchange {code}` → `{token, expiresIn}` → `setToken` → `invalidateQueries` → navigate `/admin`.

- **Verify:** allowlisted Google sign-in lands on `/admin` authenticated; GitHub same; a **non-allowlisted** account is bounced to `/admin/login?error=oauth_denied` with **no** token issued; JWT never in the address bar.

---

## Phase 3 — Frontend OAuth2 wiring

### `src/pages/admin/Login.tsx`
- Below the password form: a divider + two terminal-themed buttons that full-page `window.location.assign` to `{BASE_URL}/oauth2/authorization/google` and `/github` (not `apiFetch`). Read `BASE_URL` like `src/lib/api.ts` (`VITE_API_URL`).
- Surface `?error=` query param as the existing error line.

### New `src/pages/admin/OAuthCallback.tsx`
- On mount: read `?code` / `?error`; `POST /api/auth/oauth/exchange` via `apiFetch`; `setToken`; `invalidateQueries`; navigate `/admin`; on error navigate `/admin/login?error=…`. Terminal-style "Authenticating…" state. *(In Phase 6, also handle a `?mfa=1` PRE_AUTH response by routing to the MFA-verify page.)*

### `src/router.tsx`
- Add `admin/oauth/callback` → `OAuthCallback` (lazy), a **sibling** of `admin/login` — NOT under `RequireAuth` (runs before a token exists) and NOT under `RedirectIfAuthed` (it is the thing that sets the token).

- **Verify:** end-to-end Google + GitHub click-through works in the browser; extend `RequireAuth.test.tsx` pattern with an `OAuthCallback` test (mock `apiFetch`, assert token set + redirect).

---

## Phase 4 — Provider setup (owner, out-of-band — document in `DEPLOY.md`)
- Google Cloud OAuth client → Authorized redirect URI `{backend}/login/oauth2/code/google`.
- GitHub OAuth App → callback URL `{backend}/login/oauth2/code/github`.
- Per environment, list redirect URIs (local `http://localhost:8081/...`, prod Render URL). Add all new env vars to `DEPLOY.md`'s env table.
- **Verify:** `DEPLOY.md` updated; both dev OAuth apps created; redirect URIs match exactly (a mismatch is the #1 OAuth failure).

---

## Phase 5 — Admin-panel hardening (lock the perimeter)

Independent of OAuth/MFA — closes the gaps an outsider would probe. Do this before MFA so MFA sits behind a hardened door.

### Authorization & surface
- Audit every controller: every non-public route is `hasRole("ADMIN")`; the `anyRequest().hasRole("ADMIN")` catch-all is the **last** matcher. Only `/oauth2/**`, `/login/oauth2/**`, `POST /api/auth/login`, `POST /api/auth/oauth/exchange`, and genuinely public portfolio GETs are permitted.
- Verify the public-GET catch-all (if present, as in the portfolio) does not accidentally expose any `/api/admin/**` or `/api/auth/**` route.

### Rate limiting & lockout
- Apply the existing per-IP `RateLimiter` to **all** auth-sensitive endpoints: `POST /api/auth/login`, `POST /api/auth/oauth/exchange`, and (Phase 6) `POST /api/auth/mfa/verify`.
- Add progressive lockout: after N failed attempts (e.g. 5) per IP+identity within a window, return `429` with `Retry-After` and back off. Failed OTP and failed password share the counter for an identity.

### Security headers & transport
- Add response headers: `Strict-Transport-Security` (prod), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (or CSP `frame-ancestors 'none'`), a restrictive `Content-Security-Policy`, `Referrer-Policy: no-referrer`. Spring Security `headers(...)` DSL.
- Ensure cookies (the OAuth request cookie) are `Secure` in prod, `HttpOnly`, `SameSite=Lax`.

### Error hygiene & logging
- Login/exchange/MFA failures return a **generic** message ("invalid credentials") — never "no such user" vs "wrong password" (no user enumeration).
- Log auth **events** (success/failure, IP, provider, allowlist-reject, MFA-fail) at INFO/WARN without logging secrets, tokens, or OTPs. This is the owner's tripwire for intrusion attempts.

### JWT posture
- Confirm short JWT TTL (matches today); confirm `JwtSessionGuard` logout kill-switch invalidates **OAuth- and MFA-issued** tokens identically (OAuth JWTs carry email as `subject`; password JWTs carry `ADMIN_USERNAME`; both → `ROLE_ADMIN`).

- **Verify:** unauthenticated request to any admin route → 401/403; 6th rapid failed login → 429 with `Retry-After`; headers present in response (curl `-I`); a wrong password and an unknown user return the **same** body; logout invalidates an OAuth-issued token.

---

## Phase 6 — TOTP MFA (second factor on password *and* OAuth2)

RFC 6238 TOTP, compatible with Google Authenticator / Authy. MFA is required for the single admin once enrolled; this is the layer that defeats both a leaked password and a hijacked Google account.

### Dependencies — `pom.xml`
- `com.warrenstrange:googleauth:1.5.0` (TOTP generate/verify) and `com.google.zxing:core` + `com.google.zxing:javase` (QR PNG). Pin current versions.

### Storage / config
- The TOTP secret is per-admin and **encrypted at rest** (reuse an env master key like `JwtService` does — never store the raw Base32 secret in plaintext). For a single-admin panel this can live in a small `admin_mfa` table (`secret_enc`, `enabled`, `recovery_codes_hash[]`, `created_at`) or, if no DB write is desired, an encrypted env value. Prefer the table so recovery codes and enabled-state persist.
- MFA must be **optional at startup**: if not enrolled, login behaves exactly as today (so first-run and tests work).

### Enrollment (owner, one-time, behind an authenticated session)
- `POST /api/auth/mfa/setup` (ADMIN) → generate Base32 secret → return an `otpauth://totp/...` provisioning URI + a QR PNG (ZXing) for the SPA to render.
- `POST /api/auth/mfa/enable {otp}` (ADMIN) → verify a live code against the new secret → on success persist `enabled=true` and generate **10 single-use recovery codes** (return once, store only salted hashes).
- `POST /api/auth/mfa/disable {otp|recoveryCode}` (ADMIN) → verify, then disable.

### Two-step login (the gate)
Introduce a **PRE_AUTH** intermediate state so the first factor never yields a full ADMIN token while MFA is enabled.
- **Password path:** `POST /api/auth/login` with valid credentials and MFA enabled → return a short-lived (e.g. 5 min) **PRE_AUTH** token/code (authority `PRE_AUTH` only, *not* `ROLE_ADMIN`) instead of the final JWT.
- **OAuth path:** `OAuth2SuccessHandler`, when MFA enabled, issues a PRE_AUTH one-time code and redirects to the callback with `?mfa=1`; the SPA routes to the MFA-verify page instead of `/admin`.
- **Verify step:** `POST /api/auth/mfa/verify {otp}` (carrying the PRE_AUTH token) → validate TOTP (allow ±1 time-step skew) or a recovery code → on success mint the **final** `{token, expiresIn}` ADMIN JWT (via the one-time code mechanism for the OAuth path). Rate-limited + lockout (Phase 5). Cache the consumed TOTP time-step to block immediate replay.
- Recovery codes are single-use: mark used on redemption.

### Frontend
- `src/pages/admin/MfaSetup.tsx` (authenticated) — show QR + secret, input to confirm a code, then display recovery codes once with a copy/download.
- `src/pages/admin/MfaVerify.tsx` — terminal-themed OTP input reached after first factor when `mfa` is required (both password and OAuth paths); on success `setToken` → `/admin`.
- Router: `admin/mfa/verify` as a sibling of `admin/login` (runs with a PRE_AUTH token, before the ADMIN token exists); `admin/mfa/setup` under `RequireAuth`.

### Wire-up
- `SecurityConfig`: permit `POST /api/auth/mfa/verify` for PRE_AUTH-token holders only (a dedicated matcher / authority check), `before` the ADMIN catch-all. `setup`/`enable`/`disable` require `ROLE_ADMIN`.
- `JwtAuthFilter` / `JwtService`: support the `PRE_AUTH` authority and a separate short TTL for the pre-auth token; ensure PRE_AUTH tokens **cannot** access any ADMIN route.

- **Verify (must all pass):**
  1. Enroll MFA; scanning the QR in Google Authenticator yields codes that verify.
  2. Password login with MFA enabled → returns PRE_AUTH only, **no** ADMIN access until `mfa/verify` succeeds.
  3. OAuth (allowlisted) login with MFA enabled → routed to MFA-verify, ADMIN token only after correct OTP.
  4. Wrong OTP → rejected + counts toward lockout; replayed OTP (same time-step) → rejected.
  5. A recovery code logs in once, then is rejected on reuse.
  6. **Leaked-password simulation:** correct password + no authenticator → cannot reach `/admin`.
  7. **Hijacked-Google simulation:** allowlisted email signs in but no OTP → cannot reach `/admin`.
  8. PRE_AUTH token cannot call any `hasRole("ADMIN")` endpoint.

---

## Phase 7 — Tests

- **Backend** (`src/test/java/com/portfolio`, match `JwtServiceTest` style):
  - `OneTimeCodeStoreTest` (P1).
  - `AuthControllerTest` — `oauth/exchange` valid/invalid; `login` returns PRE_AUTH when MFA enabled vs final JWT when not; lockout returns 429.
  - `MfaServiceTest` — secret gen, TOTP verify with ±1 skew, replay rejection, recovery-code single-use.
  - `SecurityConfigTest` (or slice tests) — unauthenticated admin route → 401/403; PRE_AUTH token → 403 on ADMIN route; non-allowlisted OAuth email rejected.
- **Frontend** (extend `RequireAuth.test.tsx` pattern): `OAuthCallback` test (token set + redirect), `MfaVerify` test (PRE_AUTH → OTP → token → `/admin`).
- **Verify:** `mvn test` and `npm run test` green.

---

## Phase 8 — End-to-end verification (manual, full stack)

1. Create dev Google + GitHub OAuth apps; set ids/secrets + `OAUTH_ALLOWED_EMAILS=<your email>` + `APP_FRONTEND_URL=http://localhost:5173` in backend `.env`/run config.
2. `cd backend && mvn test` then run the app; `cd frontend && npm run dev`.
3. Password login (MFA off) still works unchanged.
4. Enroll MFA from the authenticated panel; confirm QR scans and a code enables it.
5. Log out; password login now demands the OTP; correct OTP → `/admin`.
6. Google sign-in (allowlisted) now demands the OTP; correct OTP → `/admin`.
7. **Negative:** non-allowlisted Google account → `/admin/login?error=oauth_denied`, no token.
8. **Negative:** correct password, no OTP → blocked. Allowlisted Google, no OTP → blocked.
9. Rapid wrong attempts → 429 with `Retry-After`.
10. `POST /api/auth/logout` (kill-switch) invalidates an OAuth+MFA-issued token.
11. JWT never appears in the address bar (only the single-use 60s `code`).

---

## Notes / trade-offs

- **One-time code store and the consumed-TOTP-step cache are in-memory** (single-instance) — the same accepted limitation as `JwtSessionGuard`; fine for the single Render instance, redeemed within seconds. If the panel ever scales to multiple instances, move both to Redis.
- **OAuth-issued JWTs carry email as `subject`** (vs `ADMIN_USERNAME` for password login); both map to `ROLE_ADMIN` in `JwtAuthFilter`, so downstream authorization is identical. PRE_AUTH tokens carry only the `PRE_AUTH` authority and a short TTL.
- **MFA is the layer that makes the panel truly outsider-proof:** the allowlist stops the wrong person from signing in, but MFA is what survives a *compromised* password or *compromised* Google account. Recommend enrolling it immediately after Phase 6 lands.
- **Recovery codes** are the only break-glass path if the authenticator device is lost — store the printed copy offline.
- Suggested implementation order if you want value sooner: P0 → P1 → P2 → P3 → **P5** → P6 (hardening before MFA), with P4/P7/P8 alongside. P5 is cheap and high-impact; do not defer it.
