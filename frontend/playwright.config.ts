import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests drive the real stack: the Spring Boot jar (in-memory H2, same Flyway migrations) behind the
 * Vite dev server's /api proxy. Build the jar first: `cd backend && ./mvnw -DskipTests package`.
 * Set JAVA_HOME (or E2E_JAVA) to a JDK 21 installation.
 */
const java = process.env.E2E_JAVA ?? (process.env.JAVA_HOME ? `"${process.env.JAVA_HOME}/bin/java"` : "java");

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  timeout: 45_000,
  use: {
    baseURL: "http://localhost:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1280, height: 860 } } },
    { name: "mobile", use: { ...devices["Pixel 7"] }, testMatch: /responsive\.spec\.ts/ },
  ],
  webServer: [
    {
      command:
        `${java} -jar ../backend/target/spendsmart.jar --spring.profiles.active=local ` +
        `--spring.datasource.url=jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1 ` +
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
