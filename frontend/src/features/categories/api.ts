import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { invalidateFinancialData } from "@/lib/queryClient";
import type { Category, CategoryUsage, TransactionType } from "@/types/api";

export function useCategories(includeArchived = false) {
  return useQuery({
    queryKey: ["categories", { includeArchived }],
    queryFn: ({ signal }) => api<Category[]>("/api/categories", { query: { includeArchived }, signal }),
    staleTime: 60_000,
  });
}

export function useCategoryIcons() {
  return useQuery({
    queryKey: ["categories", "icons"],
    queryFn: () => api<string[]>("/api/categories/icons"),
    staleTime: Infinity,
  });
}

export function useCategoryUsage(id: string | null) {
  return useQuery({
    queryKey: ["categories", "usage", id],
    queryFn: () => api<CategoryUsage>(`/api/categories/${id}/usage`),
    enabled: Boolean(id),
  });
}

export function useSaveCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: { name: string; icon: string; type?: TransactionType; archived?: boolean } }) =>
      id
        ? api<Category>(`/api/categories/${id}`, { method: "PUT", body: { name: input.name, icon: input.icon, archived: input.archived ?? false } })
        : api<Category>("/api/categories", { method: "POST", body: { name: input.name, icon: input.icon, type: input.type } }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useDeleteCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reassignTo }: { id: string; reassignTo?: string }) =>
      api<void>(`/api/categories/${id}`, { method: "DELETE", query: { reassignTo } }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}
