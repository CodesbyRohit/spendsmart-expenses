# API reference

The base path is `/api`. A live OpenAPI 3 description is at `/api/docs/openapi.json` and the Swagger UI is at
`/api/docs` (in the `dev` and `local` profiles).

**Conventions**

- Authenticate with `POST /api/auth/login`. The browser then sends the `SPENDSMART_SESSION` cookie automatically.
- Every `POST`, `PUT` and `DELETE` must echo the `XSRF-TOKEN` cookie value in an `X-XSRF-TOKEN` header. Call
  `GET /api/auth/csrf` once to obtain the cookie.
- Money and percentages are JSON **strings** (`"1250.50"`), and dates are `yyyy-MM-dd`. Months are `yyyy-MM`.
- Errors are `application/problem+json`:
  `{ "status": 400, "code": "VALIDATION_FAILED", "detail": "...", "fieldErrors": [{ "field": "amount", "message": "..." }] }`.
  Common codes: `UNAUTHENTICATED` (401), `FORBIDDEN` / `CSRF_INVALID` (403), `NOT_FOUND` (404, also used for other
  users' records), `CONCURRENT_MODIFICATION` / `CATEGORY_IN_USE` / `DATA_CONFLICT` (409), `RATE_LIMITED` (429).

## Auth — `/api/auth`

| Method | Path | Body / params | Notes |
| --- | --- | --- | --- |
| GET | `/csrf` | — | sets the `XSRF-TOKEN` cookie (204) |
| POST | `/register` | `email, password, displayName, defaultCurrency, locale` | 201 `User`, signs in, seeds default categories |
| POST | `/login` | `email, password` | 200 `User`; 401 `INVALID_CREDENTIALS`; 429 when rate-limited |
| POST | `/logout` | — | 204 |
| GET | `/me` | — | 200 `User` or 401 |

## Account — `/api/account`

| Method | Path | Body | Notes |
| --- | --- | --- | --- |
| GET | `/` | — | current `User` |
| GET | `/options` | — | supported `currencies` and `locales` |
| PUT | `/profile` | `displayName, email, currentPassword?` | a password is required to change the email |
| PUT | `/preferences` | `defaultCurrency, locale, theme, budgetAlertsEnabled, budgetAlertThreshold (50–100)` | |
| POST | `/password` | `currentPassword, newPassword` | 204; signs out other sessions |
| GET | `/export` | — | JSON download of all account data |
| POST | `/delete` | `password, confirmation: "DELETE"` | 204; permanently deletes everything |

## Transactions — `/api/transactions`

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/` | `q, type, categoryId, currency, from, to, page (0-based), size (1–100, default 20), sort` → `Page<Transaction>`. `sort` is one of `date|amount|description|merchant|createdAt` plus `,asc` or `,desc` |
| GET | `/export` | same filters → `text/csv` (up to 50,000 rows) |
| GET | `/currencies` | currencies the user has transactions in |
| GET | `/{id}` | one transaction |
| POST | `/` | `type, amount, currency, categoryId, description, transactionDate, merchant?, paymentMethod?, notes?` → 201 |
| PUT | `/{id}` | same body plus `version` (from the last read) |
| DELETE | `/{id}` | 204 |

## Categories — `/api/categories`

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/?includeArchived=` | includes `transactionCount` |
| GET | `/icons` | allowed icon keys |
| POST | `/` | `name, type, icon` |
| PUT | `/{id}` | `name, icon, archived` (the type is immutable) |
| GET | `/{id}/usage` | `transactionCount, budgetLineCount` |
| DELETE | `/{id}?reassignTo=` | `reassignTo` is required if transactions use the category; it must be the same type |

## Budgets — `/api/budgets`

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/{yyyy-MM}?currency=` | status for the month (`planExists=false` still reports `total.spent`) |
| PUT | `/{yyyy-MM}` | `currency, totalLimit?, lines: [{categoryId, limit}]`. Lines are updated in place |
| POST | `/{yyyy-MM}/copy-previous?currency=` | copies the previous month's plan |
| DELETE | `/{yyyy-MM}?currency=` | removes the plan only |
| GET | `/history?currency=&months=12` | newest first |
| GET | `/currencies` | |

## Savings goals — `/api/goals`

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/` | goals with computed `savedAmount`, `progressPercent`, `remainingAmount`, `monthlyNeeded` |
| POST | `/` | `name, targetAmount, currency, targetDate?, notes?, initialAmount?` |
| PUT | `/{id}` | `name, targetAmount, targetDate?, notes?, version` |
| DELETE | `/{id}` | deletes the goal and its contributions |
| GET | `/{id}/contributions` | history, newest first |
| POST | `/{id}/contributions` | `type: DEPOSIT or WITHDRAWAL, amount, date (≤ today), note?` |
| DELETE | `/{id}/contributions/{contributionId}` | refused if the saved amount would go negative |

## Dashboard, analytics and search

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/dashboard?from=&to=&currency=` | defaults to the current month and the user's currency |
| GET | `/api/analytics/overview?from=&to=&currency=` | totals, comparison, categories, trend, insights |
| GET | `/api/analytics/monthly?currency=&months=12` | calendar-month series with month-over-month change |
| GET | `/api/analytics/recurring?currency=` | regular payments detected over the last six months |
| GET | `/api/search?q=` | at least 2 characters: transactions (8), categories (5), goals (5) |

## Notifications and sample data

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/notifications` | latest 30 items plus `unreadCount` |
| POST | `/api/notifications/{id}/read` | |
| POST | `/api/notifications/read-all` | |
| GET | `/api/sample-data` | `{ present }` |
| POST | `/api/sample-data` | loads flagged sample data (409 if already loaded) |
| DELETE | `/api/sample-data` | removes only flagged rows |
