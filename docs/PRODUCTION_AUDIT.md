# Production audit — SpendSmart Expenses

Audited: 2026-10-22, against the live deployment `https://spendsmart-expenses-tau.vercel.app/` and the repository
at branch `fix/render-database-url`. Every finding below separates **confirmed evidence** from hypothesis.
Severity: critical > high > medium > low.

---

## F1 — Critical — Confirmed: no backend exists behind `/api/**` in production

**Where:** the Vercel deployment (`spendsmart-expenses-tau.vercel.app`) — every `/api/**` path.

**Evidence (curl, 2026-10-22, before the fix):**

```
GET /api/auth/me    → 404 text/plain  body: "The page could not be found\n\nNOT_FOUND\n\nbom1::…"
GET /api/auth/csrf  → 404 text/plain  (Vercel's own 404, not JSON problem details)
GET /               → 200 text/html   (the static React build serves fine)
```

**Root cause (confirmed):** a Vercel deployment of this repository serves only the built static Vite bundle.
There is no Node server, and Spring Boot — which owns `/api/**`, sessions and CSRF — was never deployed to any
host. Nothing in the repository configured rewrites (no `vercel.json`/`vercel.ts` existed anywhere). The Vite
dev proxy (`vite.config.ts`) exists only on `localhost:5173` and does not run in production.

**Why the UI said "The request could not be completed.":** `frontend/src/lib/api.ts` turns any non-JSON error
response into a generic `ApiError`; Vercel's HTML/text 404 has no `code`/`detail`, so registration, sign-in and
the `GET /api/auth/me` session check all surfaced that same generic message.

**User impact:** the product was completely unusable in production — no account could be created or used.

**Fix (implemented in this branch):**

- `frontend/vercel.ts` proxies `/api/(.*)` to the real backend origin. The origin comes from the
  `SPENDSMART_API_ORIGIN` environment variable read at deploy time, so no hostname is invented in the repository
  and no rewrite can point at a server that does not exist. Proxied same-origin keeps the `SPENDSMART_SESSION`
  cookie (`SameSite=Lax`), the `XSRF-TOKEN` cookie and the CSRF double-submit flow working unchanged — no CORS,
  no `SameSite=None`.
- `docs/DEPLOYMENT.md` defines the missing deployment: a Docker-free Java host (Railway/Heroku/VM instructions)
  plus managed PostgreSQL, with the environment-variable reference and a smoke-test procedure.

**Verification:** `scripts/smoke-test.sh <vercel-url>` covers frontend, proxy, registration, session, CSRF,
persistence and cleanup in one run. *Production verification is pending the two account-side steps in
docs/DEPLOYMENT.md §1–2 (backend host + `SPENDSMART_API_ORIGIN`), which require the repository owner's
credentials — no hosting account was available to this engineer.*

---

## F2 — High — Confirmed: client-side routes 404 on direct navigation/refresh

**Where:** `GET /register` (and any other React Router route) on the production URL.

**Evidence:**

```
GET /register → 404 text/plain  body: "The page could not be found … NOT_FOUND"
```

**Root cause (confirmed):** no SPA fallback rewrite existed; Vercel only serves files that exist in `dist/`.

**Impact:** refreshing or sharing any in-app link broke the page, including the registration page.

**Fix:** the fallback rewrite `/(.*) → /index.html` in `frontend/vercel.ts` (always active, independent of the
backend variable). **Verification:** `curl https://<vercel>/register` must return 200 text/html; asserted by the
smoke test. *Production check pending the redeploy described in F1.*

---

## F3 — High — Fixed: production datasource rejected platform `postgres://` URLs

**Where:** `backend/src/main/resources/application-prod.yml` + new
`backend/src/main/java/com/spendsmart/config/PostgresUrlEnvironmentPostProcessor.java` (registered via
`META-INF/spring.factories`).

**Evidence:** managed Postgres platforms (Railway, Neon, Heroku, Render) inject
`DATABASE_URL=postgres://user:pass@host:5432/db?sslmode=require`. PostgreSQL JDBC only accepts
`jdbc:postgresql://`, and the driver rejects anything else with `IllegalArgumentException: 'url' must start with
"jdbc"` *before* Flyway starts — so a correctly deployed backend would still crash-loop on the first boot
whenever the operator wired the platform URL straight in.

**Root cause:** the app's prod config had no vocabulary for the platform URL format.

**Fix:**

- The `EnvironmentPostProcessor` rewrites `spring.datasource.url` from `postgres://`/`postgresql://` to
  `jdbc:postgresql://`, preserving query parameters (`sslmode=require`), extracting percent-encoded userinfo
  into credentials (explicit `DB_USERNAME`/`DB_PASSWORD` always win), and failing startup with an actionable,
  credential-free message for unsupported schemes, malformed URLs, or missing credentials.
- `application-prod.yml` now accepts `DB_URL` **or** the platform-injected `DATABASE_URL` directly, so no
  connection-string copying is needed.

**Verification:** 13 unit tests in `PostgresUrlEnvironmentPostProcessorTest` (including a test that the real
pgjdbc driver accepts the converted URL and that Spring Boot's own binder resolves it); full backend suite green
on H2 — see the results table in the README. A live boot against a real PostgreSQL instance requires the
deployment from F1.

---

## F4 — Medium — Fixed: rate limiting sees shared proxy IPs

**Where:** `AuthRateLimiter` (per-IP registration/login limits) behind the Vercel `/api` proxy.

**Evidence:** forwarded headers are deliberately untrusted in prod (`application-prod.yml`,
`forward-headers-strategy: none`, hardening added earlier to prevent `X-Forwarded-For` spoofing and rate-limit
bypass — see commit `4b949ff`), so `request.getRemoteAddr()` behind Vercel's proxy is an edge-IP, not the
client.

**Impact:** per-IP limits (10 registrations/hour, 5 failed logins/15 min) become coarser: they apply per proxy
edge IP, so one abusive client can consume a shared bucket, and legitimate users are rarely limited.

**Fix/decision:** left untrusted on purpose (trusting arbitrary forwarded headers re-opens the bypass); the
limitation is documented in `docs/DEPLOYMENT.md` §6 and `docs/SECURITY.md`. Revisit with a real client-IP
source if abuse appears.

---

## F5 — Medium — Accepted limitation: sessions are in memory

Restarting the backend signs every user out; horizontal scaling would need Spring Session. Documented in the
README and deployment guide. Not a correctness defect; recorded because it affects production behaviour.

---

## F6 — Low — Process gap closed: no production smoke path existed

There was no single command that could tell an operator "production actually works". Added
`scripts/smoke-test.sh` (frontend + SPA rewrite + proxy + auth lifecycle + persistence + cleanup, curl-only) and
a deployment checklist in `docs/DEPLOYMENT.md` §4, plus the CI dependency-scan swap described in the README.

---

## Previously found and fixed (earlier sessions — see git history and `docs/SECURITY.md`)

Not re-audited here; listed for completeness: vulnerable backend dependencies patched (`ac5a909`), Vitest
dev-only advisories (`89dce8c`), CSV export always-400 from the UI (`82ff8df`), unbounded export/analytics
queries (`b948fea`), user-timezone "today" and DB-level child-row ownership (`79ec7f0`), dashboard comparison
labelling (`99a4bd0`), container healthcheck (`8ea60c7`).

## Explicitly checked, no defect found

- **Cross-user isolation:** enforced in the DB (ownership constraints, `79ec7f0`) and covered by the existing
  isolation test suites (UI and direct API).
- **CSRF/session design:** double-submit cookie + session rotation on login; correct for the single-origin
  proxy architecture chosen here.
- **Secrets:** no credentials in the repository; `.env` is git-ignored; the frontend bundle carries no secrets
  (`VITE_*` values are public by design and none are set).
- **Mass assignment:** transaction/category/account DTOs bind only declared fields; unknown JSON is rejected.
