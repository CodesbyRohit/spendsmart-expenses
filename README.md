# SpendSmart Expenses

**Know where your money goes. Decide where it should go.**

SpendSmart is a personal expense manager. Record income and expenses, set monthly budgets, track savings goals and
see where your money goes. Every figure on every screen is computed from the transactions you have recorded. Nothing
is estimated or invented, and nothing is sent to third-party services.

| | |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Spring Security, Spring Data JPA, Flyway, PostgreSQL 16 / H2 |
| Frontend | React 19, TypeScript 5.9, Vite 7, Tailwind CSS 4, TanStack Query, React Hook Form + Zod, Recharts |
| Tests | JUnit 5, MockMvc, Testcontainers, Vitest + Testing Library, Playwright |

## Features

- **Accounts**: registration, sign-in and sign-out; profile, preferences and password change; JSON export of all
  your data; account deletion that requires your password plus typing `DELETE`.
- **Transactions**: add, edit, view and delete (with confirmation). Search by description or merchant. Filter by
  type, category, currency and date range. Sort, paginate on the server, and export to CSV. Filters live in the URL.
- **Categories**: 15 defaults are created with each account. You can create, rename and re-icon categories,
  archive them, or delete them by moving their transactions into another category of the same type.
- **Budgets**: a monthly overall limit plus category limits, with progress, remaining amount, a safe per-day
  amount, overspending warnings, history, copy-from-last-month, and in-app alerts at your threshold
  (sent only once).
- **Savings goals**: target amount and date, deposits and withdrawals, contribution history, progress and the
  amount needed per month.
- **Dashboard and analytics**: income, expenses and net for any date range; budget usage; category breakdown;
  spending trend; month-over-month comparison; regular-payment detection; and plain-language insights.
- **Everywhere**: global search, a light/dark/system theme, locale-aware number and date formats, a per-user time
  zone (so "today" and "this month" match your calendar), mobile layouts, keyboard navigation and reduced-motion
  support.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| JDK | **21** | `java -version` |
| Node.js | 20.19+ or 22.12+ (tested with 24.14) | `node -v` |
| Docker Desktop | optional: PostgreSQL and the Testcontainers test | `docker info` |

You don't need to install Maven: the repository includes the Maven Wrapper (`mvnw` / `mvnw.cmd`), which downloads
Maven 3.9.16 and verifies its checksum.

> **Windows note:** if `java -version` reports 17 or older, point `JAVA_HOME` at a JDK 21 for the current shell
> before using `mvnw`:
>
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"   # adjust to your install
> ```
> ```bash
> export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12"    # Git Bash
> ```

## Quick start (no Docker, two terminals)

This uses the `local` profile: an H2 database stored in `backend/data/`, built by the same Flyway migrations that
production uses.

**Terminal 1 — backend (http://localhost:8080)**

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 2 — frontend (http://localhost:5173)**

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173 and create an account. The Vite dev server proxies `/api` to the backend, so the browser
sees a single origin.

## Running with PostgreSQL

1. Create your local environment file and choose a password:
   ```powershell
   Copy-Item .env.example .env      # PowerShell
   ```
   ```bash
   cp .env.example .env             # bash
   ```
2. Start PostgreSQL 16:
   ```bash
   docker compose up -d db
   ```
   If another PostgreSQL already uses port 5432, set `DB_PORT=5433` in `.env` and use the same port in `DB_URL`.
   `APP_PORT` does the same for the application container. (Docker is optional: any locally installed PostgreSQL 16
   works too — create a database and user, point `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at it, and skip compose.)
3. Run the backend with the `dev` profile (the default). It reads `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` from the
   environment:
   ```powershell
   $env:DB_PASSWORD = "<the value you put in .env>"
   cd backend; .\mvnw.cmd spring-boot:run
   ```
   ```bash
   export DB_PASSWORD="<the value you put in .env>"
   cd backend && ./mvnw spring-boot:run
   ```
   Flyway creates the schema on first start. Hibernate only *validates* the schema; it never generates it.
4. Start the frontend as in the quick start.

## Deployment

Production is two Docker-free services plus one managed database: the React build on **Vercel**, the Spring Boot
jar on a **Java host** (exact Railway / Heroku / free-VM instructions included), and PostgreSQL from **Neon** or
the host's add-on. The browser only ever talks to the Vercel origin — `frontend/vercel.ts` proxies `/api/*` to the
backend, so session cookies, CSRF and CSP behave exactly as they do on localhost, with no CORS. Docker is not
required for local development, testing or deployment.

- Setup, environment-variable reference, deployment checklist, smoke test and recovery runbook:
  **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.
- Why the previous production deployment failed (every `/api/*` returned Vercel's own 404 because no backend was
  ever deployed): **[docs/PRODUCTION_AUDIT.md](docs/PRODUCTION_AUDIT.md)**.

Production frontend: https://spendsmart-expenses-tau.vercel.app. The API becomes reachable once the backend host
from `docs/DEPLOYMENT.md` §1 is deployed and the `SPENDSMART_API_ORIGIN` variable is set on Vercel; verify the whole
path with:

```bash
./scripts/smoke-test.sh https://spendsmart-expenses-tau.vercel.app
```

### Optional: single container (a local convenience, not the deployment path)

```bash
docker compose --profile app up --build      # http://localhost:8080
```

The image builds the React app and serves it from Spring Boot on the same origin, using the `prod` profile (secure
cookies, API explorer off). Verified on 2026-10-22 (Docker Engine 29.8.2): the image builds, reports `healthy`,
Flyway applies V1 and V2 to an empty PostgreSQL 16.14, and the Playwright suite passes against it. Skip this
entirely if you don't have Docker — nothing else needs it.

## Profiles

| Profile | Database | Use |
| --- | --- | --- |
| `dev` (default) | PostgreSQL from `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | day-to-day development |
| `local` | H2 file in `backend/data/` | zero-dependency local runs and demos |
| `prod` | PostgreSQL; credentials **required** from the environment | deployment: secure cookies, docs off |
| `test` | in-memory H2 with a fixed clock (2026-10-15) | automated tests only |

Configuration reference: `.env.example`, `frontend/.env.example`, `backend/src/main/resources/application*.yml`.

## Demo data

On a new account, the dashboard offers **Explore with sample data**. This loads about three months of example
income, expenses, a budget and a savings goal, in your default currency. Sample data is explicitly marked:

- every sample row is flagged `is_sample` in the database;
- a banner reads "Sample data is loaded" on every page until you remove it;
- rows show a **Sample** badge, and the CSV export has a `Sample data` column;
- **Remove sample data** deletes only flagged rows. Your own records are never touched.

Sample data is never created automatically.

## Tests

| Suite | Command | Result |
| --- | --- | --- |
| Backend on H2 (unit, MockMvc API, repository, auth, isolation, migration tests) | `cd backend && ./mvnw test` (`.\mvnw.cmd test`) | **112 run, 0 failed** (2026-10-22). No Docker needed: Docker-backed tests skip themselves when the daemon is absent |
| Backend, **whole suite on PostgreSQL 16** | `cd backend && ./mvnw test -Ppostgres` | last local run 2026-10-22: 99/99 before this branch's additions; CI re-runs the current suite on PostgreSQL 16 on every push |
| Frontend unit/component | `cd frontend && npm test` | **29 passed** (7 files) |
| Frontend typecheck + production build | `cd frontend && npm run build` | passed |
| End-to-end, dev stack (jar + H2 + Vite) | `cd frontend && npm run e2e` | **7 passed** (desktop + mobile projects) |
| npm dependency audit | `cd frontend && npm audit` | **0 vulnerabilities** |
| Maven dependency scan | Trivy 0.76.1 `fs` on `backend/` (CI job `dependencies`) | 2 accepted findings (see `docs/SECURITY.md`) |
| Production smoke test | `./scripts/smoke-test.sh <frontend-url>` | deployed environments: frontend, SPA rewrite, `/api` proxy, auth lifecycle, persistence, cleanup |

The Docker-backed tests are `PostgresMigrationTest` (every migration on a fresh PostgreSQL 16),
`MigrationUpgradeTest` (upgrade a V1 database that already holds data) and, with `-Ppostgres`, the whole suite on
PostgreSQL instead of H2 — `DatabaseEngineTest` fails that run if it silently fell back to H2. They **skip
themselves** (they don't fail) without a Docker daemon, so check the summary says `Skipped: 0` when you rely on
them. Nothing else in development, testing or deployment needs Docker.

### Continuous integration

`.github/workflows/ci.yml` runs on every pull request and every push to `main`: the backend suite on PostgreSQL, the
frontend audit, tests and build, the Playwright suite, and a Trivy filesystem scan of `backend/` gated at
HIGH/CRITICAL findings that have a fix available (minus the justified entries in `.trivyignore`). No Docker image
is built: production runs the jar on a Java host. The workflow was added on 2026-10-22 and runs on GitHub's own
runners, where Docker is provided by the platform — it is never required on your machine.

**End-to-end tests**

```bash
cd backend && ./mvnw -DskipTests package && cd ..   # builds target/spendsmart.jar
cd frontend
npx playwright install chromium                     # first time only (~150 MB)
npm run e2e                                         # starts the jar (in-memory H2) and Vite itself
```

With `JAVA_HOME` set to JDK 21, Playwright starts both servers. If servers are already running on ports 8080 and
5173, it reuses them.

The suites cover: registration and sign-in, expense creation, list and dashboard updates, category budgets
reflecting spending, editing and deleting with budget recalculation, threshold alerts, savings-goal progress,
validation, cross-user isolation (UI and direct API calls), creating a category and filtering by it, filtered CSV
download, sign-out locking protected pages and the API, and mobile layout at 375 px with no horizontal overflow.

To run the same suite against an already-running deployment (for example the Docker image), set `E2E_BASE_URL`:
`E2E_BASE_URL=http://localhost:8080 npx playwright test`.

## Project layout

```
backend/                Spring Boot app (com.spendsmart.*)
  src/main/java/...     auth, user, category, transaction, budget, goal, analytics, notification,
                        export, search, sample, security, config, common
  src/main/resources/   application*.yml, db/migration (Flyway)
frontend/
  src/app               providers and routes
  src/features/*        dashboard, transactions, categories, budgets, goals, analytics, settings, auth, search
  src/components        UI kit (Button, Field, Dialog, Card, Progress, Toast, …) and charts
  src/lib               typed HTTP client, formatting, dates, validation schemas
  e2e/                  Playwright specs
docs/                   ARCHITECTURE.md, SECURITY.md, API.md, DEPLOYMENT.md, PRODUCTION_AUDIT.md,
                        IMPLEMENTATION_PLAN.md
```

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): design, data model, calculation rules, and frontend structure
- [docs/SECURITY.md](docs/SECURITY.md): security controls, review results and known limitations
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md): production architecture, provider setup, environment variables,
  deployment checklist, smoke test and recovery runbook
- [docs/PRODUCTION_AUDIT.md](docs/PRODUCTION_AUDIT.md): production failure audit — evidence, root cause, fixes
- [docs/API.md](docs/API.md): endpoint reference. A live OpenAPI explorer runs at
  http://localhost:8080/api/docs in the `dev` and `local` profiles.

## Known limitations

- **No currency conversion.** Each currency is aggregated separately, and every screen shows one currency at a
  time.
- **Sessions are in memory.** Restarting the backend signs everyone out, and running more than one instance needs
  shared sessions (Spring Session) and a shared rate limiter.
- **No bank connection.** Balances and savings are what you record; SpendSmart never claims they are verified.
- **In-app notifications only.** Budget alerts appear under the bell icon, and only for the current calendar month.
  No email, SMS or push provider is configured.
- **No recurring-transaction scheduler.** Regular payments are *detected* in your history, not created
  automatically.
- **No password reset by email**, because no email provider is configured.
- **No refund or transfer type.** A transaction is either income or an expense. Recording a refund as income inflates
  income and leaves the original category's spending unchanged; editing or deleting the original expense is the
  accurate option today. Moving money between your own accounts isn't modelled.
- **Behind a reverse proxy**, forwarded headers are ignored unless you enable them. See "Running behind a reverse
  proxy" in `docs/SECURITY.md`.
- **Behind the Vercel `/api` proxy**, per-IP rate limits see Vercel's edge IPs (forwarded headers stay untrusted on
  purpose), so they are coarser than in a single-origin deployment.
- **Free-tier hosts sleep.** The first request after a cold start can take tens of seconds, and a backend restart
  signs everyone out (sessions are in memory).
