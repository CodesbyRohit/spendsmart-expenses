import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests drive the real stack: the Spring Boot jar (in-memory H2, same Flyway migrations) behind the
 * Vite dev server's /api proxy. Build the jar first: `cd backend && ./mvnw -DskipTests package`.
 * Set JAVA_HOME (or E2E_JAVA) to a JDK 21 installation.
 *
 * To test an already-running deployment instead (e.g. the Docker image), set E2E_BASE_URL:
 *   E2E_BASE_URL=http://localhost:8080 npx playwright test   — no servers are started.
 */
const externalBaseUrl = process.env.E2E_BASE_URL;
const java = process.env.E2E_JAVA ?? (process.env.JAVA_HOME ? `"${process.env.JAVA_HOME}/bin/java"` : "java");

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  timeout: 45_000,
  use: {
    baseURL: externalBaseUrl ?? "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 860 } } },
    { name: "mobile", use: { ...devices["Pixel 7"] }, testMatch: /responsive\.spec\.ts/ },
  ],
  webServer: externalBaseUrl ? undefined : [
    {
      command:
        `${java} -jar ../backend/target/spendsmart.jar --spring.profiles.active=local ` +
        // The JDBC URL must stay quoted: its semicolons would otherwise be read as command separators by the
        // POSIX shell Playwright uses on Linux (CI), splitting the command.
        `--spring.datasource.url="jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1" ` +
        `--app.security.registration-per-ip-per-hour=200`,
      url: "http://localhost:8080/actuator/health",
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: "npx vite --port 5173 --strictPort",
      url: "http://localhost:5173",
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
});
