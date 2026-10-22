/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath, URL } from "node:url";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  // Dev server proxies /api to Spring Boot so the browser sees a single origin
  // (HttpOnly session cookie + CSRF cookie work without CORS).
  const backend = env.SPENDSMART_BACKEND_URL || "http://localhost:8080";
  const proxy = { "/api": { target: backend, changeOrigin: false } };
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, strictPort: true, proxy },
    build: {
      sourcemap: false,
      rollupOptions: {
        output: {
          // Long-lived vendor chunks cache well across app releases.
          manualChunks: (id: string) => {
            if (!id.includes("node_modules")) return undefined;
            if (/recharts|d3-|victory-vendor|decimal\.js|es-toolkit|immer|reselect|redux/.test(id)) return "charts";
            if (/zod|react-hook-form|@hookform/.test(id)) return "forms";
            if (/lucide-react/.test(id)) return "icons";
            return "vendor";
          },
        },
      },
    },
    test: {
      environment: "jsdom",
      globals: true,
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.test.{ts,tsx}"],
      css: false,
    },
  };
});
