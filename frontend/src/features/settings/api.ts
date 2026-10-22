import { useMutation, useQuery } from "@tanstack/react-query";
import { api, download } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import type { AccountOptions, Theme, User } from "@/types/api";

/** Server-provided option lists, so forms never offer a value the API would reject. */
export function useAccountOptions() {
  return useQuery({
    queryKey: ["account", "options"],
    queryFn: () => api<AccountOptions>("/api/account/options"),
    staleTime: Infinity,
  });
}

export function useUpdateProfile() {
  const { setUser } = useAuth();
  return useMutation({
    mutationFn: (input: { displayName: string; email: string; currentPassword?: string }) =>
      api<User>("/api/account/profile", { method: "PUT", body: { ...input, currentPassword: input.currentPassword || null } }),
    onSuccess: setUser,
  });
}

export function useUpdatePreferences() {
  const { setUser } = useAuth();
  return useMutation({
    mutationFn: (input: { defaultCurrency: string; locale: string; theme: Theme; budgetAlertsEnabled: boolean; budgetAlertThreshold: number }) =>
      api<User>("/api/account/preferences", { method: "PUT", body: input }),
    onSuccess: setUser,
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      api<void>("/api/account/password", { method: "POST", body: input }),
  });
}

export function useDeleteAccount() {
  return useMutation({
    mutationFn: (input: { password: string; confirmation: string }) =>
      api<void>("/api/account/delete", { method: "POST", body: input }),
  });
}

export function exportAccountData() {
  return download("/api/account/export", undefined, "spendsmart-account.json");
}
