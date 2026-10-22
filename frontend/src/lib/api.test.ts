import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, onUnauthorized } from "./api";

function respond(status: number, body?: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => onUnauthorized(null));

describe("api client", () => {
  it("sends the CSRF cookie value as X-XSRF-TOKEN on state-changing requests", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    const fetchMock = vi.fn(async () => respond(201, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    await api("/api/transactions", { method: "POST", body: { a: 1 } });
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("abc123");
    expect(init.credentials).toBe("include");
  });

  it("turns problem responses into ApiError with field errors", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => respond(400, { code: "VALIDATION_FAILED", detail: "Some fields need attention.", fieldErrors: [{ field: "amount", message: "Amount must be greater than zero." }] })));
    const error = (await api("/api/transactions", { method: "POST", body: {} }).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.fieldError("amount")).toBe("Amount must be greater than zero.");
  });

  it("refreshes an expired CSRF token once and retries", async () => {
    document.cookie = "XSRF-TOKEN=old; path=/";
    let posts = 0;
    vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit = {}) => {
      if (String(url).endsWith("/api/auth/csrf")) {
        document.cookie = "XSRF-TOKEN=new; path=/";
        return respond(204);
      }
      posts++;
      const token = (init.headers as Record<string, string>)["X-XSRF-TOKEN"];
      return token === "new" ? respond(200, { saved: true }) : respond(403, { code: "CSRF_INVALID", detail: "expired" });
    }));
    await expect(api("/api/goals", { method: "POST", body: {} })).resolves.toEqual({ saved: true });
    expect(posts).toBe(2);
  });

  it("reports a 401 from a data endpoint so the app can return to sign-in", async () => {
    const handler = vi.fn();
    onUnauthorized(handler);
    vi.stubGlobal("fetch", vi.fn(async () => respond(401, { code: "UNAUTHENTICATED", detail: "Please sign in to continue." })));
    await expect(api("/api/dashboard")).rejects.toMatchObject({ status: 401 });
    expect(handler).toHaveBeenCalledOnce();
  });

  it("maps network failures to a friendly error", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("Failed to fetch"); }));
    await expect(api("/api/dashboard")).rejects.toMatchObject({ code: "NETWORK" });
  });
});
