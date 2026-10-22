import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { invalidateFinancialData } from "@/lib/queryClient";
import type { BudgetHistoryEntry, BudgetMonth, SaveBudgetInput } from "@/types/api";

export function useBudgetMonth(month: string, currency: string) {
  return useQuery({
    queryKey: ["budgets", "month", month, currency],
    queryFn: ({ signal }) => api<BudgetMonth>(`/api/budgets/${month}`, { query: { currency }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useBudgetHistory(currency: string, months = 12) {
  return useQuery({
    queryKey: ["budgets", "history", currency, months],
    queryFn: ({ signal }) => api<BudgetHistoryEntry[]>("/api/budgets/history", { query: { currency, months }, signal }),
  });
}

export function useBudgetCurrencies() {
  return useQuery({ queryKey: ["budgets", "currencies"], queryFn: () => api<string[]>("/api/budgets/currencies") });
}

export function useSaveBudget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ month, input }: { month: string; input: SaveBudgetInput }) =>
      api<BudgetMonth>(`/api/budgets/${month}`, { method: "PUT", body: input }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useCopyPreviousBudget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ month, currency }: { month: string; currency: string }) =>
      api<BudgetMonth>(`/api/budgets/${month}/copy-previous`, { method: "POST", query: { currency } }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useDeleteBudget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ month, currency }: { month: string; currency: string }) =>
      api<void>(`/api/budgets/${month}`, { method: "DELETE", query: { currency } }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}
