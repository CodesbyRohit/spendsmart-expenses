import { QueryClient, type QueryClient as QC } from "@tanstack/react-query";
import { ApiError } from "./api";

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        retry: (count, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  });
}

/**
 * Financial data is one connected system: any change to transactions, budgets, goals or categories can move
 * dashboard totals, budget progress, analytics, alerts and search results. Invalidate them together so every
 * screen shows the same persisted truth.
 */
export function invalidateFinancialData(queryClient: QC) {
  for (const key of ["transactions", "transaction", "dashboard", "budgets", "analytics", "notifications", "categories", "goals", "search", "sample-data"]) {
    void queryClient.invalidateQueries({ queryKey: [key] });
  }
}
