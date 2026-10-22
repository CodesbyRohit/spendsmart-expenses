# Deployment guide

SpendSmart deploys as two Docker-free services plus one managed database:

```
Browser ──HTTPS──> Vercel (static React build, frontend/)
                     │  vercel.ts rewrites:
                     │    /api/*  ──proxy──> Backend origin (browser keeps one origin, no CORS)
                     │    anything ────────> /index.html (React Router deep links/refresh)
                     ▼
Backend origin ──HTTPS──> Java host, e.g. Railway web service (Spring Boot jar, backend/)
                              │  DATABASE_URL (postgres://…) → converted at startup
                              ▼
                        Managed PostgreSQL (Neon or Railway Postgres)
```

Why this shape:

- **The browser only ever talks to the Vercel origin.** `/api/**` is proxied server-side to the backend, so the
  `SPENDSMART_SESSION` cookie (HttpOnly, `SameSite=Lax`), the `XSRF-TOKEN` cookie, the CSRF double-submit flow and
  the `connect-src 'self'` CSP all keep working exactly as they do locally with the Vite dev proxy. No CORS
  configuration, no `SameSite=None`, no second origin to secure.
- **No Docker anywhere.** Vercel builds the frontend with Vite; the Java host builds the jar with the Maven
  Wrapper; the database is managed. `Dockerfile`/`docker-compose.yml` remain in the repo only as an optional
  local convenience and are not part of this deployment.
- **The backend URL is never invented in the repository.** `frontend/vercel.ts` reads it from the
  `SPENDSMART_API_ORIGIN` environment variable at deploy time. Until that variable is set, the SPA still builds
  and serves; API calls fail loudly with the same 404s as before (and a build-log warning names the fix).

## 1. Backend: web service + managed Postgres

Any Java-capable host works as long as it (a) runs a long-lived JVM process, (b) provides a `PORT` environment
variable or lets you configure the port, and (c) gives you a PostgreSQL `postgres://` URL. The repository is
pre-configured for that contract:

- `server.port = ${PORT:8080}` — the platform picks the port.
- `application-prod.yml` reads `DB_URL`, falling back to the platform-injected `DATABASE_URL`
  (Railway/Neon/Heroku/Render style: `postgres://user:pass@host:5432/db?sslmode=require`).
  `PostgresUrlEnvironmentPostProcessor` converts that scheme to `jdbc:postgresql://` and lifts the embedded
  credentials at startup — no copying connection strings between dashboard fields. A URL without credentials and
  without `DB_USERNAME`/`DB_PASSWORD` aborts startup with an actionable message (never printing the URL).
- Flyway applies migrations (`V1`, `V2`) automatically on first start and on every upgrade; Hibernate only
  validates the schema.
- `/actuator/health` is public (for platform health checks); every other `/api/**` route requires a session.

### Railway (recommended, ≈ $5/month)

1. Create a [Neon](https://neon.tech) Postgres (free tier persists; recommended over Railway's Postgres, which
   needs a paid plan) **or** a Railway Postgres. Copy nothing yet.
2. In Railway: *New Project → Deploy from GitHub repo → spendsmart-expenses*.
3. Service settings:
   - **Root Directory:** `backend`
   - Railway detects `pom.xml` (Nixpacks). Environment variables:
     - `NIXPACKS_JDK_VERSION` = `21`
     - `SPRING_PROFILES_ACTIVE` = `prod`
     - `COOKIE_SECURE` = `true`
     - `DATABASE_URL` = *(Railway: "Connect → Generate JWT" / Neon: paste the pooled connection string; the app
       converts `postgres://` automatically — `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` are not needed)*
   - **Start Command** (if Railway does not auto-detect a jar): `java -jar target/spendsmart.jar`
4. Deploy. First build runs `mvn -DskipTests package`; first start runs Flyway (≈ 30–60 s on a small instance).
5. Health check: `curl https://<service>.up.railway.app/actuator/health` → `{"status":"UP"}`.

### Heroku (alternative)

1. `heroku create <app>`.
2. The Maven project lives in `backend/`, so chain the Monorepo buildpack before the Java one:
   ```bash
   heroku buildpacks:add heroku/buildpack-monorepo
   heroku buildpacks:add heroku/java
   heroku config:set APP_BASE=backend
   ```
   The Java buildpack then builds `backend/pom.xml` on the JDK pinned in `backend/system.properties` (21).
3. `heroku addons:create heroku-postgresql:essential-0` — injects `DATABASE_URL` (`postgres://`), which the app
   accepts directly.
4. `heroku config:set SPRING_PROFILES_ACTIVE=prod COOKIE_SECURE=true`
5. `git push heroku fix/render-database-url:main` (or merge first), then `heroku ps:scale web=1`.
   Health: `curl https://<app>.herokuapp.com/actuator/health`.

### Any always-free VM (Oracle Cloud etc.)

Install a JDK 21 (`apt install openjdk-21-jre-headless`), build once with `./mvnw -DskipTests package`, run the
jar under systemd with `Environment=DB_URL=postgres://…` and `Environment=SPRING_PROFILES_ACTIVE=prod`, and put
Caddy/nginx in front for HTTPS. Set `SERVER_FORWARD_HEADERS_STRATEGY=native` **only** behind that proxy and
restrict Tomcat's trusted proxies to its address (see `docs/SECURITY.md`). Manage backups yourself.

### Backend environment variables (complete reference)

| Variable | Required | Meaning |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | yes | `prod` in deployment |
| `DATABASE_URL` or `DB_URL` | yes | `postgres://user:pass@host:5432/db?sslmode=require` (either name) |
| `DB_USERNAME` / `DB_PASSWORD` | no | only if the URL carries no credentials; wins over URL userinfo |
| `COOKIE_SECURE` | no (default `true` in prod) | keep `true` behind HTTPS |
| `PORT` | set by host | HTTP port (Render/Railway/Heroku all inject it) |
| `SESSION_TIMEOUT` | no (default `8h`) | session idle window |
| `APP_CORS_ALLOWED_ORIGINS` | no (leave empty) | CORS is not needed with the same-origin proxy |
| `APP_API_DOCS_ENABLED` | no (default `false` in prod) | API explorer at `/api/docs` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | no | `native` only behind a proxy **you** control |

Never commit real values; `.env` is git-ignored.

## 2. Frontend: Vercel

1. Import the GitHub repository. **Root Directory: `frontend`** (this is what the current deployment already
   uses), Framework Preset: Vite — build command `npm run build`, output `dist` are auto-detected.
2. Environment variable (Settings → Environment Variables):
   - `SPENDSMART_API_ORIGIN` = your backend's https origin, e.g. `https://spendsmart-api.up.railway.app`
     (no trailing slash, no path). `frontend/vercel.ts` turns this into the `/api/*` proxy rewrite at deploy
     time and also writes the SPA fallback for `/register`, `/dashboard`, etc.
3. Deploy. The build log warns loudly if `SPENDSMART_API_ORIGIN` is missing.
4. Verify with the smoke test below.

Notes:

- No `VITE_*` variable is needed: the app calls same-origin `/api` and Vercel proxies it. (`VITE_API_BASE_URL`
  still exists for exotic setups, but a cross-origin deployment would need CORS + `SameSite=None` cookies —
  don't.)
- No secrets live in the frontend bundle; everything `VITE_`-prefixed is public by definition.

## 3. Production smoke test

```bash
./scripts/smoke-test.sh https://spendsmart-expenses-tau.vercel.app
```

One command verifies: static frontend loads, SPA fallback works on `/register`, `/api/auth/csrf` reaches the
backend through the proxy, registration issues a real session cookie, a transaction persists and is readable
after "reload", logout kills the session, unauthenticated API calls get 401, login restores the session, and the
throwaway account is deleted again. Exit code 0 = production path verified end to end.

## 4. Deployment checklist

- [ ] Backend deployed; `GET <backend>/actuator/health` returns `{"status":"UP"}` (also visible in host logs:
      Flyway applied `V1`, `V2`; no stack traces).
- [ ] Backend env: `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL` (or `DB_URL`) set, `COOKIE_SECURE=true`.
- [ ] Startup must **not** contain `'url' must start with "jdbc"` (that only happens on builds older than
      `PostgresUrlEnvironmentPostProcessor`) or `carries no credentials` (the connection string lost its
      `user:pass@`). With a reachable database, Flyway logs `Migrating schema` on first start and the health
      endpoint turns `UP`; that is the behavioural proof that the `postgres://` URL was converted.
- [ ] Vercel env: `SPENDSMART_API_ORIGIN` set to the backend origin; latest deploy shows no
      `SPENDSMART_API_ORIGIN is not set` warning.
- [ ] `curl -s https://<vercel>/register` returns the SPA shell (not Vercel's `NOT_FOUND` text).
- [ ] `./scripts/smoke-test.sh https://<vercel>` passes every check.
- [ ] Browser check: register, add an expense, refresh the page, expense still there; DevTools network shows
      `/api/**` requests going to the Vercel origin with 2xx/4xx JSON — never Vercel HTML 404s.

## 5. Recovery runbook

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Frontend loads, every `/api` call is Vercel's `NOT_FOUND` text | `SPENDSMART_API_ORIGIN` unset/typo, or backend down | Set/fix the variable, redeploy frontend; check backend health URL directly |
| `/register` refresh 404s | `vercel.ts` not picked up (wrong Root Directory) | Set Vercel Root Directory to `frontend`, redeploy |
| Backend log: `'url' must start with "jdbc"` | an old build without `PostgresUrlEnvironmentPostProcessor` | Redeploy from the current branch |
| Backend log: `carries no credentials` | `DATABASE_URL` lost its userinfo, or `DB_USERNAME/DB_PASSWORD` unset | Re-paste the full connection string or set the explicit variables |
| Backend exits on `Connection refused`/`ssl` errors | wrong host/params, IP allowlist, or `sslmode` | Re-copy the platform's pooled/internal URL; check allowlists |
| 502s on `/api` right after deploy | backend still booting (JVM + Flyway) or cold start on free tier | Wait for `/actuator/health`; consider a paid instance for always-on |
| Users signed out on deploy | sessions are in-memory (documented limitation) | expected; users sign in again |
| Registration/login returns 429 | per-IP rate limit; shared proxy IPs are not trusted by default | see "Running behind a reverse proxy" in `docs/SECURITY.md`; limits are per-IP, 10 reg/h, 5 login fails/15 min |

## 6. Persistence, backups, honest limitations

- **Where data lives:** the managed PostgreSQL database. It survives redeployments and restarts of either
  service. Neither Vercel nor the Java host stores user data on disk.
- **Backups:** Neon manages daily backups on paid plans; free tiers rely on the provider's baseline. For real
  money data, enable point-in-time restore and test `GET /api/account/export` (JSON export per user).
- **Sessions are in memory:** a backend restart signs everyone out; horizontal scaling would need Spring Session.
- **Free-tier cold starts:** an idle free web service sleeps; the first request after idle can take tens of
  seconds (Vercel's proxy waits, but very slow first bytes may surface as a timeout in the browser).
- **Rate limiting sees the proxy's IPs** for `/api` traffic (forwarded headers are deliberately not trusted);
  per-IP limits are coarser than in a single-origin deployment.
- **No email provider**, so no password reset; **no bank connection**, so balances are only what users record.
