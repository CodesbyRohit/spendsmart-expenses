import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, download } from "@/lib/api";
import { invalidateFinancialData } from "@/lib/queryClient";
import type { Page, Transaction, TransactionInput, TransactionType } from "@/types/api";

export interface TransactionFilters {
  q?: string;
  type?: TransactionType | "";
  categoryId?: string;
  currency?: string;
  from?: string;
  to?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: ["transactions", filters],
    queryFn: ({ signal }) => api<Page<Transaction>>("/api/transactions", { query: { ...filters }, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useTransaction(id: string | null) {
  return useQuery({
    queryKey: ["transaction", id],
    queryFn: ({ signal }) => api<Transaction>(`/api/transactions/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

export function useSaveTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: TransactionInput }) =>
      id
        ? api<Transaction>(`/api/transactions/${id}`, { method: "PUT", body: input })
        : api<Transaction>("/api/transactions", { method: "POST", body: input }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useDeleteTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/transactions/${id}`, { method: "DELETE" }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useTransactionCurrencies() {
  return useQuery({
    queryKey: ["transactions", "currencies"],
    queryFn: () => api<string[]>("/api/transactions/currencies"),
  });
}

export function exportTransactionsCsv(filters: TransactionFilters) {
  const { page: _page, size: _size, sort: _sort, ...rest } = filters;
  return download("/api/transactions/export", { ...rest }, "spendsmart-transactions.csv");
}
