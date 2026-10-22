import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { invalidateFinancialData } from "@/lib/queryClient";
import type { Contribution, ContributionType, Goal } from "@/types/api";

export interface GoalInput {
  name: string;
  targetAmount: string;
  currency?: string;
  targetDate?: string | null;
  notes?: string | null;
  initialAmount?: string | null;
  version?: number;
}

export function useGoals() {
  return useQuery({ queryKey: ["goals"], queryFn: ({ signal }) => api<Goal[]>("/api/goals", { signal }) });
}

export function useContributions(goalId: string | null) {
  return useQuery({
    queryKey: ["goals", goalId, "contributions"],
    queryFn: () => api<Contribution[]>(`/api/goals/${goalId}/contributions`),
    enabled: Boolean(goalId),
  });
}

export function useSaveGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: GoalInput }) =>
      id
        ? api<Goal>(`/api/goals/${id}`, {
            method: "PUT",
            body: { name: input.name, targetAmount: input.targetAmount, targetDate: input.targetDate || null, notes: input.notes || null, version: input.version },
          })
        : api<Goal>("/api/goals", {
            method: "POST",
            body: {
              name: input.name,
              targetAmount: input.targetAmount,
              currency: input.currency,
              targetDate: input.targetDate || null,
              notes: input.notes || null,
              initialAmount: input.initialAmount || null,
            },
          }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useDeleteGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/goals/${id}`, { method: "DELETE" }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useAddContribution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ goalId, input }: { goalId: string; input: { type: ContributionType; amount: string; date: string; note?: string } }) =>
      api<Goal>(`/api/goals/${goalId}/contributions`, { method: "POST", body: { ...input, note: input.note || null } }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}

export function useDeleteContribution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ goalId, contributionId }: { goalId: string; contributionId: string }) =>
      api<Goal>(`/api/goals/${goalId}/contributions/${contributionId}`, { method: "DELETE" }),
    onSuccess: () => invalidateFinancialData(qc),
  });
}
