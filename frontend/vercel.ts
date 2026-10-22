/**
 * Vercel project configuration (executed at deployment build time — see docs/DEPLOYMENT.md).
 *
 * Why this file exists: the deployed site at spendsmart-expenses-tau.vercel.app used to serve only the built
 * React bundle. There is no Node server and no Spring Boot process behind a static Vercel deployment, so every
 * /api/** request answered 404 with Vercel's own "The page could not be found" text — registration, sign-in and
 * the session check all failed ("The request could not be completed."). Client-side routes such as /register
 * also 404'd on direct navigation because nothing rewrote them to index.html.
 *
 * Two rewrites fix that:
 *   1. /api/** is proxied to the real backend (an external HTTPS origin) so the browser keeps talking to a
 *      single origin. The HttpOnly session cookie (SameSite=Lax), the CSRF cookie and the CSP "connect-src 'self'"
 *      keep working unchanged, and no CORS configuration is needed on either side.
 *   2. Everything else falls back to /index.html so React Router routes survive refresh and deep links.
 *
 * The backend origin comes from the SPENDSMART_API_ORIGIN environment variable (Vercel → Project → Settings →
 * Environment Variables, e.g. https://spendsmart-api.up.railway.app). vercel.json cannot read environment
 * variables, which is why the dynamic vercel.ts form is used: no hostname is invented in the repository, and
 * until the variable is set the site keeps its current behaviour (SPA works, /api 404s) with a loud build-log
 * warning instead of a rewrite pointing at a server that does not exist.
 */

const rawOrigin = process.env.SPENDSMART_API_ORIGIN?.trim();

if (rawOrigin && !/^https:\/\/[^\s/]+$/i.test(rawOrigin.replace(/\/+$/, ""))) {
  throw new Error(
    "SPENDSMART_API_ORIGIN must be an https:// origin without a trailing path " +
      "(e.g. https://spendsmart-api.up.railway.app); got: " +
      JSON.stringify(rawOrigin)
  );
}

const apiOrigin = rawOrigin ? rawOrigin.replace(/\/+$/, "") : "";

if (!apiOrigin) {
  // Not fatal: the deploy still serves the SPA, but API calls will 404 exactly as before this file existed.
  console.warn(
    "[spendsmart] SPENDSMART_API_ORIGIN is not set — /api/* will NOT be proxied and sign-in/registration " +
      "will fail with 404. Set it in Vercel project settings to your backend's https:// origin " +
      "(see docs/DEPLOYMENT.md), then redeploy."
  );
}

export const config = {
  rewrites: [
    ...(apiOrigin
      ? [{ source: "/api/(.*)", destination: `${apiOrigin}/api/$1` }]
      : []),
    // React Router: direct navigation and refresh resolve to the SPA shell.
    { source: "/(.*)", destination: "/index.html" },
  ],
};
