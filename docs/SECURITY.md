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
| Broken access control | every query is scoped by the session's user id; foreign ids return 404; composite foreign keys stop cross-user category references at the database level | `CrossUserIsolationTest` (6 tests), `TransactionRepositoryTest`, Playwright `isolation.spec.ts` |
| Mass assignment | request records list only bindable fields; unknown JSON properties are rejected (400) | `requiredFieldsDatesAndUnknownPropertiesAreValidated` |
| SQL injection | JPA criteria / JPQL with bound parameters only; sort keys whitelisted; `LIKE` wildcards escaped | `listSupportsSearchFiltersSortingAndServerSidePagination` |
| XSS | React escapes all output; no `dangerouslySetInnerHTML`; strict CSP (`script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`); category icons come from a whitelist | code review (grep), header check |
| CSV formula injection | text cells starting with `= + - @ \t \r` are prefixed with `'`; RFC 4180 quoting | `CsvWriterTest`, `csvExportHonoursFiltersAndNeutralisesFormulas` |
| Brute force | in-memory sliding window: 5 failed sign-ins per email and 25 per IP in 15 minutes; 5 failed password re-confirmations per account; 10 registrations per IP per hour. Returns `429` with `Retry-After` | `repeatedFailedLoginsAreRateLimited`, `repeatedWrongPasswordConfirmationsInsideASessionAreRateLimited` |
| User enumeration at sign-in | identical message for unknown email and wrong password; Spring's DAO provider mitigates timing differences | `loginWithWrongPasswordGivesGenericErrorAndUnknownEmailLooksIdentical` |
| Open redirect | the post-login `next` parameter must be a same-app relative path | `format.test.ts › post-login redirects` |
| Error leakage | problem responses never include stack traces or exception messages (`server.error.include-*=never`) | handler review |
| Actuator | only `health` (no details) and `info` are exposed; everything else is denied | `protectedEndpointsRequireAuthenticationAndReturnJson` |
| Headers | CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: same-origin`, `Permissions-Policy` | checked with curl against the running jar |
| Logging | no request bodies or financial values are logged; the alert service logs ids and exception types only | code review |
| Data export and deletion | authenticated, owner-only exports. Deletion needs the password plus a typed `DELETE`, removes every row in one transaction, and ends the session | `AccountApiTest` |
| Secrets management | all credentials come from environment variables; `.env` is git-ignored; `prod` refuses to start without them | configuration |
| External data sharing | none: no analytics SDKs or third-party APIs, and fonts are system fonts | code review |

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
7. **Dependency vulnerability scanning** (OWASP Dependency-Check, `npm audit`) was not run during development. Add it
   to CI.
8. **Account deletion is a hard delete.** Backups taken before deletion still contain the data; define a backup
   retention policy accordingly.
