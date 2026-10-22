# Architecture

## Overview

```
Browser ── same origin ──► Vite dev server (dev)  ─┐
                           or Spring Boot (prod)   ├─► /api/**  Spring MVC controllers ─► services ─► JPA repositories ─► PostgreSQL / H2
                                                   └─► /**      built React app (index.html fallback)
```

The application is a single deployable unit. In development the Vite server proxies `/api` to Spring Boot. In
production Spring Boot serves the built React app from `APP_STATIC_DIR`. Either way the browser talks to one origin,
which lets authentication use an HttpOnly session cookie and a CSRF token, with no CORS.

## Backend (`backend/`, package `com.spendsmart`)

| Package | Responsibility |
| --- | --- |
| `security` | filter chain, CSRF handling for the SPA, session principal, session integrity filter, auth rate limiting |
| `auth` | register / login / logout / me, password policy |
| `user` | `User` entity, profile and preferences, password change, account deletion |
| `category` | categories, defaults seeded at registration, safe deletion with reassignment |
| `transaction` | transaction CRUD, filtering (JPA Specifications), sorting whitelist, pagination, CSV export endpoint |
| `budget` | monthly plans, category limits, `BudgetCalculator` (pure), history, copy-previous |
| `goal` | savings goals, contributions, progress |
| `analytics` | overview, monthly series, `InsightEngine`, `RecurringDetector`, dashboard composition |
| `notification` | in-app budget alerts (`BudgetAlertService`), read state |
| `export` | `CsvWriter` (RFC 4180 + formula neutralisation), account JSON export |
| `search` | global search over the user's transactions, categories and goals |
| `sample` | opt-in, flagged sample data |
| `common` | money and percentage rules, date ranges, problem-detail error handling, pagination DTO |
| `config` | typed `app.*` properties, clock, Jackson, OpenAPI, SPA serving |

Conventions used throughout:

- **Controllers are thin.** They bind and validate input, take the user id from the session principal, and delegate.
- **DTOs only.** Request and response records are separate from entities. Unknown JSON properties are rejected
  (`fail-on-unknown-properties`), so clients can't mass-assign server-controlled fields such as the owner, timestamps
  or the sample flag.
- **Ownership is enforced in queries.** Every repository lookup includes `user_id` (`findByIdAndUserId`, a
  specification with `userId = :me`, and so on). A record owned by someone else is indistinguishable from a missing
  one: both return 404.
- **Constructor injection, `@Transactional` services**, and optimistic locking (`@Version`) on mutable aggregates.
  Updates must send the version they read; a stale version returns 409.
- **Errors** are RFC 9457 problem documents with a stable `code` and, for validation failures, `fieldErrors`. Stack
  traces and internal messages are never returned.
- **N+1 avoided**: entity graphs fetch transactions together with their categories, budget plans fetch-join their
  lines, and goal progress uses one grouped query for the whole list.

### Data model (Flyway `V1__initial_schema.sql`, `V2__user_time_zone_and_ownership_keys.sql`)

```
users ─┬─< categories ─┬─< transactions
       │               └─< category_budgets >── monthly_budgets
       ├─< savings_goals ─< goal_contributions
       └─< notifications
```

Integrity rules enforced by the database, not just the code:

- `transactions (category_id, user_id, type)` → `categories (id, user_id, type)` is a composite foreign key. A
  transaction cannot reference another user's category, or an income category for an expense.
- `category_budgets` uses the same composite key with `category_type = 'EXPENSE'`, so budgets can only use your own
  expense categories.
- Since V2, `category_budgets (monthly_budget_id, user_id)` → `monthly_budgets (id, user_id)` and
  `goal_contributions (goal_id, user_id)` → `savings_goals (id, user_id)`, so a child row can never belong to a
  different user than its parent, even through direct SQL.
- Applied migrations are never edited. Schema changes ship as new versions, and `MigrationUpgradeTest` upgrades a V1
  database that already holds data to the latest version on PostgreSQL 16.
- `CHECK` constraints: positive amounts and limits; first-of-month budget periods; enumerated types, payment methods
  and themes.
- Unique constraints: e-mail; category name per user and type; one plan per user, month and currency; one line per
  category per plan; and one alert per `dedupe_key`.

Derived values are **never stored**: dashboard totals, budget spend and goal savings are all computed from rows. That
leaves no second source of truth that could drift.

### Dates and time zones

Transaction and contribution dates are calendar dates (`DATE`) with no time component. Whenever the server needs
"today" or "the current month", it uses `UserClock`, which evaluates the injected `Clock` in the **user's own** IANA time
zone (`users.time_zone`, captured from the browser at registration and editable in Settings). That covers:

- the "no future contributions" rule and the latest allowed transaction date;
- budget days-left and the daily allowance;
- which month raises alerts;
- the dashboard's default range, the monthly series and recurring detection.

Using the server's zone instead was a real defect: in the UTC Docker image, a user in UTC+05:30 couldn't record a
contribution dated today before 05:30 local time, and on the 1st still saw last month's budget.
`TimeZoneBoundaryTest` pins the clock to 2026-10-31T20:00Z, which is 1 November in India, to cover this.

### Money

- `BigDecimal` in Java and `NUMERIC(19,4)` in SQL. JSON carries decimals as **strings**, so JavaScript never parses
  money into floating point.
- Each amount is validated against its currency's ISO-4217 minor units (INR 2, JPY 0, KWD 3). An amount with too
  many decimals is **rejected**, never silently rounded. Display values are scaled `HALF_UP` to the currency's minor
  units; percentages use one decimal, also `HALF_UP`.
- Every aggregate is filtered to a single currency. There is no foreign-exchange conversion, so nothing is mixed.

### Calculation rules

| Figure | Definition |
| --- | --- |
| Period totals | Σ amounts by type for `currency` with `from ≤ date ≤ to` (inclusive) |
| Net for the period | income − expenses for the period. Labelled as **not** a bank balance. |
| Comparison period | Whole calendar months compare with the same number of preceding months; any other range compares with the same number of days immediately before it |
| Change % | (current − previous) ÷ previous × 100. **Omitted** when previous is 0; the UI says "no baseline" instead of inventing a number |
| Savings rate | net ÷ income × 100, only when income > 0 |
| Budget spent (total) | Σ of **all** expenses in the currency and calendar month |
| Budget spent (category) | Σ of expenses in that category, currency and month. Category limits are a breakdown of the total, so no expense counts twice. |
| Utilisation | spent ÷ limit × 100, uncapped (e.g. 132.5 %). The bar width is clamped to 0–100 %. |
| Budget status | EXCEEDED if spent > limit; WARNING if spent × 100 ≥ limit × threshold (exact, unrounded); else ON_TRACK |
| Daily allowance | remaining ÷ days left including today (current month only), rounded down |
| Goal saved | Σ deposits − Σ withdrawals. Withdrawals can never exceed the saved amount; the goal row is locked during the check. |
| Goal monthly need | remaining ÷ (whole months to the target date + 1), rounded up |

### Insights and recurring detection

`InsightEngine` is a set of deterministic rules, each with its own preconditions. It reports a category change only
when the category is at least 5 % of spending and moved at least 10 %. The top-three share appears only when there are
more than three categories. Budget warnings come from the current month's statuses. Zero baselines produce an
explanatory sentence.

`RecurringDetector` reports a pattern only when a merchant (or, without one, a description) appears **at least
three times**, at intervals that all fit one cadence (weekly 6–8 days, biweekly 13–15, monthly 27–33), and every
amount is within ±15 % of the median.

### Budget alerts

After a transaction or budget change commits, `BudgetAlertService` re-evaluates the affected budgets for the
user's **current month** (in their time zone) in a separate transaction, so an alert failure can never fail or roll back the user's change. Each alert has
a `dedupe_key` of (line, month, level) under a unique constraint, so each line alerts at most once at the threshold
and once when exceeded, per month.

## Frontend (`frontend/src`)

| Folder | Contents |
| --- | --- |
| `app/` | providers (QueryClient, Toast, Auth) and the route table |
| `routes/` | `RequireAuth`, `RedirectIfAuthenticated`, safe post-login redirect |
| `layouts/` | app shell: sidebar on desktop, bottom navigation with a central Add button on mobile, global search, alerts, sample-data banner |
| `features/*` | one folder per area, each with an `api.ts` of typed TanStack Query hooks plus its pages |
| `components/ui` | accessible primitives: native-`<dialog>` modal, labelled fields with `aria-describedby`, segmented radio group, progress meters, toasts |
| `components/charts` | Recharts wrappers; every chart has a table-view toggle |
| `lib/` | `api.ts` (typed fetch client, CSRF, `ApiError`, 401 handling), `format.ts` (Intl formatting only), `schemas.ts` (Zod rules mirroring the server), `dates.ts` |

- **Server state lives in TanStack Query.** Every mutation calls `invalidateFinancialData`, so the dashboard, budgets,
  analytics, alerts and search refresh together after any change.
- **No business logic is duplicated client-side.** The UI formats server values; Zod rules exist only for fast
  feedback, and server field errors are mapped back onto the same fields.
- **Filters live in the URL** (`?from=&to=&currency=&q=&type=&categoryId=&sort=&page=`), so they survive refresh and can
  be shared.
- **Theming**: semantic CSS variables (`--surface`, `--text-muted`, `--chart-expense`, …) with a `.dark` override.
  Chart colours were checked with a colour-vision-deficiency palette validator. The income/expense pair passes in
  light mode (ΔE 9.6) and sits in the 6–8 band in dark mode (ΔE 7.4), so charts carry a legend, bar gaps, tooltips and
  a table view as secondary encodings. Category charts use a single hue, not a rainbow.
- **Accessibility**: skip link, visible focus ring, labelled controls, `aria-invalid` and `aria-describedby`, meters
  with `aria-valuetext`, status shown as icon plus text (never colour alone), and `prefers-reduced-motion` honoured.
