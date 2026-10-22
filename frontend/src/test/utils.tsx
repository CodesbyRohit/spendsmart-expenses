import type { ReactNode } from "react";
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
import { AuthProvider, useAuth } from "@/hooks/useAuth";
import { ToastProvider } from "@/components/ui/Toast";
import { TransactionDialogProvider } from "@/features/transactions/TransactionDialogs";
import type { User } from "@/types/api";

export const testUser: User = {
  id: "u1",
  email: "asha@example.test",
  displayName: "Asha",
  defaultCurrency: "INR",
  locale: "en-IN",
  theme: "LIGHT",
  budgetAlertsEnabled: true,
  budgetAlertThreshold: 80,
  createdAt: "2026-01-01T00:00:00Z",
};

type Handler = (url: URL, init: RequestInit) => { status?: number; body?: unknown } | undefined;

/** Stubs fetch with a tiny router. Unmatched requests fail the test loudly instead of silently succeeding. */
export function mockApi(handler: Handler) {
  document.cookie = "XSRF-TOKEN=test-token; path=/";
  const calls: { method: string; url: URL; body: unknown }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), "http://localhost");
    const body = init.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method: init.method ?? "GET", url, body });
    if (url.pathname === "/api/auth/me") return json(200, testUser);
    if (url.pathname === "/api/auth/csrf") return new Response(null, { status: 204 });
    const result = handler(url, init);
    if (!result) throw new Error(`Unmocked request: ${init.method ?? "GET"} ${url.pathname}${url.search}`);
    return result.status === 204 ? new Response(null, { status: 204 }) : json(result.status ?? 200, result.body);
  });
  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, calls };
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** Mirrors RequireAuth: pages only render once the session check has resolved to a signed-in user. */
function SignedIn({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return user ? <>{children}</> : null;
}

export function renderWithProviders(ui: ReactNode, { route = "/" }: { route?: string } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={[route]}>
            <SignedIn>
              <TransactionDialogProvider>{ui}</TransactionDialogProvider>
            </SignedIn>
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>,
  );
}
