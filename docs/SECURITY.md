# Security notes

This document records what was implemented and reviewed. Passing tests is **not** a certification: SpendSmart has
not had an independent security audit or penetration test.

## Controls in place

| Area | Implementation | Verified by |
| --- | --- | --- |
| Password storage | `DelegatingPasswordEncoder` (BCrypt, `{bcrypt}` prefix allows future upgrades). Policy: ≥ 10 characters, ≤ 72 bytes (BCrypt's input limit), must not contain the email | `AuthIntegrationTest.registrationHashesPasswordWithBcryptAndNeverReturnsIt` |
| Secrets in responses | DTOs never include the hash or credential version; request records mask passwords in `toString()` | same test; `AccountApiTest.accountExportContainsOwnDataButNoSecrets` |
| Sessions | HttpOnly `SPENDSMART_SESSION` cookie, `SameSite=Lax`, `Secure` in `prod`; 8-hour timeout; session id rotated at sign-in | `loginRotatesSessionAndLogoutEndsIt` |
| Session revocation | a credential version on the user is checked on each API request; a password change signs out every other session, and deleted accounts lose access immediately | `changingPasswordSignsOutOtherSessionsButKeepsCurrentOne`, `accountDeletion…` |
| CSRF | `CookieCsrfTokenRepository` (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header) with the SPA request handler. The token is rotated at sign-in, sign-out and password change | `stateChangingRequestsWithoutCsrfTokenAreRejected` |
| CORS | disabled unless `APP_CORS_ALLOWED_ORIGINS` lists exact origins (credentials allowed only for those) | configuration |
| Broken access control | every query is scoped by the session's user id; foreign ids return 404. Composite foreign keys stop cross-user references at the database level: transactions and budget lines to categories (V1), budget lines to budgets and contributions to goals (V2) | `CrossUserIsolationTest` (6 tests), `TransactionRepositoryTest`, `MigrationUpgradeTest`, Playwright `isolation.spec.ts` |
| Mass assignment | request records list only bindable fields; unknown JSON properties are rejected (400) | `requiredFieldsDatesAndUnknownPropertiesAreValidated` |
| SQL injection | JPA criteria / JPQL with bound parameters only; sort keys whitelisted; `LIKE` wildcards escaped | `listSupportsSearchFiltersSortingAndServerSidePagination` |
| XSS | React escapes all output; no `dangerouslySetInnerHTML`; strict CSP (`script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`); category icons come from a whitelist | code review (grep), header check |
| CSV formula injection | text cells starting with `= + - @ \t \r` are prefixed with `'`; RFC 4180 quoting | `CsvWriterTest`, `csvExportHonoursFiltersAndNeutralisesFormulas` |
| Brute force | in-memory sliding window: 5 failed sign-ins per email and 25 per IP in 15 minutes; 5 failed password re-confirmations per account; 10 registrations per IP per hour. Returns `429` with `Retry-After`. The client IP is the TCP peer: forwarded headers are **not** trusted unless explicitly enabled (see below) | `repeatedFailedLoginsAreRateLimited`, `repeatedWrongPasswordConfirmationsInsideASessionAreRateLimited`, `ForwardedHeaderSpoofingTest` |
| User enumeration at sign-in | identical message for unknown email and wrong password; Spring's DAO provider mitigates timing differences | `loginWithWrongPasswordGivesGenericErrorAndUnknownEmailLooksIdentical` |
| Open redirect | the post-login `next` parameter must be a same-app relative path | `format.test.ts › post-login redirects` |
| Error leakage | problem responses never include stack traces or exception messages (`server.error.include-*=never`) | handler review |
| Actuator | only `health` (no details) and `info` are exposed; everything else is denied | `protectedEndpointsRequireAuthenticationAndReturnJson` |
| Headers | CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: same-origin`, `Permissions-Policy` | checked with curl against the running jar |
| Logging | no request bodies or financial values are logged; the alert service logs ids and exception types only | code review |
| Data export and deletion | authenticated, owner-only exports; CSV export refuses more than `app.export.max-rows` (default 50,000) rows after a COUNT, before loading anything. Deletion needs the password plus a typed `DELETE`, removes every row in one transaction, and ends the session | `AccountApiTest`, `ExportLimitTest` |
| Secrets management | all credentials come from environment variables; `.env` is git-ignored; `prod` refuses to start without them | configuration |
| External data sharing | none: no analytics SDKs or third-party APIs, and fonts are system fonts | code review |

## Findings from the 2026-10-22 engineering review

| Finding | How established | Status |
| --- | --- | --- |
| **Rate-limit bypass via spoofed `X-Forwarded-For`** (P1). `prod` set `server.forward-headers-strategy=native`; Tomcat then trusts any private-network peer as a proxy, which includes every client of the Docker Compose deployment. After the real IP was limited, 12/12 registrations with rotating fake headers succeeded. | Reproduced against the jar, then by `ForwardedHeaderSpoofingTest` (prod profile, real Tomcat port) | Fixed: forwarded headers are opt-in |
| **Vulnerable dependencies** (P1). Trivy 0.76.1 on the Maven dependency set: 5 CRITICAL (Tomcat 10.1.55, spring-webmvc 6.2.19), 6 HIGH (Jackson 2.21.4, pgjdbc 42.7.11), 8 MEDIUM. | Trivy `fs` scan of `backend/pom.xml` | Fixed by patch overrides; 2 remain (below) |
| **Dev-only npm advisories**: Vitest 3.2.7 / tinypool (2 CRITICAL, 1 MODERATE). Production dependencies had 0. | `npm audit` | Fixed: Vitest 4.1.12, `npm audit` reports 0 |
| **CSV export broken from the UI.** Not a vulnerability, but the export endpoint answered the SPA with 400 (see ARCHITECTURE.md). | Reproduced by a Playwright journey | Fixed, regression-tested |
| Child rows (budget lines, goal contributions) carried `user_id` without a database rule tying it to the parent's owner. | Schema review (no exploit; application code always set it correctly) | Hardened in V2 |
| Unbounded queries: CSV export loaded all rows before checking the cap; the largest-expense insight loaded every expense in the range. | Code review | Fixed |

### Accepted dependency findings

`spring-webmvc` 6.2.19 is the last Spring Framework 6.2 release. Two CRITICAL advisories are fixed only in
Framework 7.0.9, which means a Spring Boot 4 migration:

- **CVE-2026-47884** (RCE through path handling in `XsltView`);
- **CVE-2026-47890** (stream corruption for Server-Sent Events rendered through views).

Neither is reachable here: the code has no `XsltView`, `ModelAndView`, `SseEmitter` or `text/event-stream` (checked
by search), and the API renders no server-side views. Both are listed with this justification in `.trivyignore`.
Revisit them on every dependency update; the durable fix is upgrading to Spring Boot 4.

### Running behind a reverse proxy

In `prod`, the app uses the TCP peer address as the client IP. Behind a TLS-terminating proxy you control:

- set `SERVER_FORWARD_HEADERS_STRATEGY=native`;
- restrict `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to the proxy's address (a Java regex);
- make sure the proxy **overwrites** `X-Forwarded-For` and `X-Forwarded-Proto` rather than appending client-supplied
  values.

Without these settings, rate limits apply per proxy address rather than per client, and the app can't detect HTTPS
for the CSRF cookie's `Secure` flag (the session cookie is always `Secure` in `prod`).

## Remaining risks and limitations

1. **Rate limiting and sessions are in-process.** With more than one backend instance, limits are per instance and
   sessions aren't shared. Use Spring Session (JDBC or Redis) and a shared limiter, such as Bucket4j with Redis or a
   gateway, before scaling out.
2. **Registration reveals whether an email is registered** (it returns "already exists"). This is a common usability
   trade-off; email verification would close it.
3. **No email verification, password reset or multi-factor authentication**, because no email provider is
   configured.
4. **Cross-origin deployment** (frontend and API on different sites) isn't supported as-is. The SPA reads the CSRF
   cookie from its own origin, and `SameSite=Lax` cookies aren't sent on cross-site requests. Keep the default
   same-origin deployment.
5. **`style-src 'unsafe-inline'`** stays in the CSP because Recharts and React set inline style attributes. Scripts
   are still restricted to `'self'`.
6. **The API explorer** (`/api/docs`) is public in `dev` and `local` and off in `prod`. It describes endpoints but
   exposes no data.
7. **Dependency scanning** covers npm (`npm audit`: 0), the Maven dependency set (Trivy: 2 accepted, see above) and
   the image's OS packages (Trivy `--pkg-types os`: 0 HIGH/CRITICAL). The CI workflow automates all three, but it
   hasn't run on GitHub yet. Scans reflect the advisory databases on 2026-10-22; new advisories appear continuously.
8. **Account deletion is a hard delete.** Backups taken before deletion still contain the data; define a backup
   retention policy accordingly.
