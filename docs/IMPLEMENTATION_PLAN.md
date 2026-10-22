# SpendSmart Expenses — Implementation Plan

## Stage 1 audit (2026-10-22)

`github.com/CodesbyRohit/spendsmart-expenses` was cloned and contained **no commits and no files**. There was no
existing stack to preserve, so the project is initialised with the stack the brief prescribes.

Development machine findings that shape the setup:

| Item | Finding | Consequence |
| --- | --- | --- |
| JDK | 21.0.12 installed, but `JAVA_HOME` points at 17 | README tells developers to point `JAVA_HOME` at a JDK 21 |
| Maven | not installed | Maven Wrapper 3.3.4 (`mvnw`, `mvnw.cmd`) pinned to Maven 3.9.16 with SHA-512 check |
| Node | 24.14.1 / npm 11.19 | satisfies Vite 7 (`^20.19 \|\| >=22.12`) |
| Docker | CLI present, daemon not running | Testcontainers tests auto-skip without Docker; an H2 file profile allows running without Docker |

## Key decisions

1. **Same-origin deployment.** Spring Boot serves the built SPA in production (Docker image). In development the Vite
   dev server proxies `/api` to Spring Boot, so the browser still sees one origin. This lets authentication use an
   HttpOnly, `SameSite=Lax` session cookie plus a CSRF token, rather than tokens in JavaScript-readable storage.
   CORS is disabled unless `APP_CORS_ALLOWED_ORIGINS` is set explicitly.
2. **Money** is `BigDecimal` / `NUMERIC(19,4)`. Each amount is validated against its currency's ISO-4217 minor units
   (e.g. INR 2, JPY 0, KWD 3) and rejected — not silently rounded — when it has too many decimals. Every aggregate is
   scoped to exactly one currency; there is no FX conversion.
3. **Budgets** are a monthly plan per currency: an optional total limit plus optional per-category limits.
   * Category utilisation = Σ EXPENSE in that category, currency and calendar month ÷ category limit.
   * Total utilisation = Σ of *all* EXPENSE in that currency and month ÷ total limit. Category limits are
     sub-allocations of the total, so each expense is counted once in the total and once in its own category line —
     never added on top of the total.
   * Progress-bar width is clamped to 0–100 %; the percentage itself is shown uncapped (e.g. "132 %, over by ₹640").
4. **Savings goals** store contributions (deposits / withdrawals); the saved amount is always the computed sum, never
   a separately mutable number. They are labelled as user-recorded, not bank-verified.
5. **Derived figures are never stored.** Dashboards, budget progress and analytics are computed from transactions.
6. **Insights** come from deterministic rules with minimum-data thresholds; a zero baseline yields an explanatory
   sentence, never an invented percentage. Recurring patterns need ≥ 3 occurrences with regular spacing and similar
   amounts.
7. **Sample data** is opt-in from the empty dashboard. Every sample row is flagged `is_sample`, the UI shows a
   persistent "sample data" banner, and one action removes all of it.

## Acceptance criteria (verified in Stage 8)

| # | Criterion | Verified by |
| - | --- | --- |
| A1 | `mvnw verify` passes; `npm run build` and `npm test` pass | command output recorded in README |
| A2 | Flyway migrations apply to an empty database (H2 always; PostgreSQL via Testcontainers or a local server) | `MigrationTest`, `PostgresMigrationIT` |
| A3 | Register / login / logout / password change / account delete work; passwords are hashed with BCrypt | `AuthIntegrationTest` |
| A4 | User B gets 404 for every read/update/delete of user A's transactions, categories, budgets and goals | `CrossUserIsolationTest`, Playwright `isolation.spec` |
| A5 | Transaction CRUD + search + filters + sorting + server-side pagination + CSV export | `TransactionApiTest`, `CsvExportTest`, Playwright |
| A6 | Budget figures equal the sum of persisted expenses for the month, with no double counting; threshold alerts are deduplicated | `BudgetServiceTest`, `BudgetApiTest` |
| A7 | Goal progress equals the sum of contributions; withdrawals can't exceed the saved amount | `GoalServiceTest` |
| A8 | Dashboard and analytics totals equal the SQL sums for the selected range; date boundaries are inclusive | `AnalyticsServiceTest`, `DashboardApiTest` |
| A9 | CSV output escapes quotes and newlines and neutralises formula prefixes (`= + - @ \t \r`) | `CsvWriterTest` |
| A10 | Layout usable at 375 px and 1280 px; keyboard navigable; reduced-motion respected | Playwright viewport runs + manual review notes |
