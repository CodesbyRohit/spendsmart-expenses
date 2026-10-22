import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { invalidateFinancialData } from "@/lib/queryClient";
import type { AppNotification, Dashboard, MonthPoint, Overview, RecurringPattern, SearchResults } from "@/types/api";

export function useDashboard(params: { from: string; to: string; currency?: string }) {
  return useQuery({
    queryKey: ["dashboard", params],
    queryFn: ({ signal }) => api<Dashboard>("/api/dashboard", { query: params, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useOverview(params: { from: string; to: string; currency: string }) {
  return useQuery({
    queryKey: ["analytics", "overview", params],
    queryFn: ({ signal }) => api<Overview>("/api/analytics/overview", { query: params, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useMonthly(currency: string, months = 12) {
  return useQuery({
    queryKey: ["analytics", "monthly", currency, months],
    queryFn: ({ signal }) => api<MonthPoint[]>("/api/analytics/monthly", { query: { currency, months }, signal }),
  });
}

export function useRecurring(currency: string) {
  return useQuery({
    queryKey: ["analytics", "recurring", currency],
    queryFn: ({ signal }) => api<RecurringPattern[]>("/api/analytics/recurring", { query: { currency }, signal }),
  });
}

export function useSearch(q: string) {
  return useQuery({
    queryKey: ["search", q],
    queryFn: ({ signal }) => api<SearchResults>("/api/search", { query: { q }, signal }),
    enabled: q.trim().length >= 2,
  });
}

export function useNotifications() {
  return useQuery({
    queryKey: ["notifications"],
    queryFn: () => api<{ items: AppNotification[]; unreadCount: number }>("/api/notifications"),
    refetchInterval: 60_000,
  });
}

export function useMarkNotificationsRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id?: string) =>
      id ? api<void>(`/api/notifications/${id}/read`, { method: "POST" }) : api<void>("/api/notifications/read-all", { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

export function useSampleData() {
  const qc = useQueryClient();
  const load = useMutation({
    mutationFn: () => api<{ transactions: number }>("/api/sample-data", { method: "POST" }),
    onSuccess: () => invalidateFinancialData(qc),
  });
  const remove = useMutation({
    mutationFn: () => api<void>("/api/sample-data", { method: "DELETE" }),
    onSuccess: () => invalidateFinancialData(qc),
  });
  return { load, remove };
}
