import type { FieldError } from "@/types/api";

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "";
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

/** Every failed request becomes an ApiError with the server's stable code and user-facing message. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.message;
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  query?: Query;
  signal?: AbortSignal;
  /** Accept header. Downloads accept any type, so endpoints that return CSV are matched. */
  accept?: string;
}

let unauthorizedHandler: (() => void) | null = null;

/** Registered by the auth provider: a 401 anywhere means the session ended, so the app returns to sign-in. */
export function onUnauthorized(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

function readCookie(name: string): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith(name + "="));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

async function ensureCsrfToken(force = false): Promise<string | null> {
  if (force || !readCookie(CSRF_COOKIE)) {
    await fetch(BASE_URL + "/api/auth/csrf", { credentials: "include" });
  }
  return readCookie(CSRF_COOKIE);
}

export function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") {
        params.set(key, String(value));
      }
    }
  }
  const qs = params.toString();
  return BASE_URL + path + (qs ? `?${qs}` : "");
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: { code?: string; detail?: string; fieldErrors?: FieldError[] } = {};
  try {
    body = await response.json();
  } catch {
    // Non-JSON error (e.g. a proxy page): fall through to the generic message.
  }
  const fallback =
    response.status >= 500 ? "Something went wrong on our side. Please try again." : "The request could not be completed.";
  return new ApiError(response.status, body.code ?? `HTTP_${response.status}`, body.detail ?? fallback, body.fieldErrors ?? []);
}

async function send(path: string, options: RequestOptions, retried: boolean): Promise<Response> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: options.accept ?? "application/json" };
  if (method !== "GET") {
    const token = await ensureCsrfToken();
    if (token) headers[CSRF_HEADER] = token;
  }
  if (options.body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      credentials: "include",
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "NETWORK", "Can't reach SpendSmart. Check your connection and try again.");
  }

  // An expired CSRF token is refreshed once, transparently.
  if (response.status === 403 && method !== "GET" && !retried) {
    const peek = await response.clone().json().catch(() => ({}));
    if (peek?.code === "CSRF_INVALID") {
      await ensureCsrfToken(true);
      return send(path, options, true);
    }
  }
  return response;
}

/** Typed JSON request. Resolves with the parsed body (or undefined for 204), rejects with ApiError. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options, false);
  if (!response.ok) {
    const error = await toApiError(response);
    if (response.status === 401 && !path.startsWith("/api/auth/")) unauthorizedHandler?.();
    throw error;
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Downloads an authenticated file (CSV/JSON export) without exposing the session to another origin. */
export async function download(path: string, query: Query | undefined, fallbackName: string): Promise<void> {
  const response = await send(path, { query, accept: "*/*" }, false);
  if (!response.ok) throw await toApiError(response);
  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const name = /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "Something went wrong. Please try again.";
}
