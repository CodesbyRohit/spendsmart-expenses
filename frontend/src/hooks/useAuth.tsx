import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, onUnauthorized } from "@/lib/api";
import type { User } from "@/types/api";

export interface RegisterInput {
  email: string;
  password: string;
  displayName: string;
  defaultCurrency: string;
  locale: string;
}

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (input: RegisterInput) => Promise<User>;
  logout: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);
export const ME_KEY = ["auth", "me"] as const;

/**
 * The session lives in an HttpOnly cookie the JavaScript can't read; "who am I" is always asked of the server.
 * Nothing about the user or their finances is persisted in localStorage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const me = useQuery({
    queryKey: ME_KEY,
    queryFn: async () => {
      try {
        return await api<User>("/api/auth/me");
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: 5 * 60_000,
    retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
  });

  // Drop every cached record from the previous session, but keep (and update) the "me" query itself:
  // removing it would detach the observer above and leave the app showing a stale auth state.
  const resetSession = useCallback(
    (user: User | null) => {
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== ME_KEY[0] });
      queryClient.setQueryData(ME_KEY, user);
    },
    [queryClient],
  );
  const signedOut = useCallback(() => resetSession(null), [resetSession]);

  useEffect(() => {
    onUnauthorized(signedOut);
    return () => onUnauthorized(null);
  }, [signedOut]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: me.data ?? null,
      loading: me.isPending,
      login: async (email, password) => {
        const user = await api<User>("/api/auth/login", { method: "POST", body: { email, password } });
        resetSession(user);
        return user;
      },
      register: async (input) => {
        const user = await api<User>("/api/auth/register", { method: "POST", body: input });
        resetSession(user);
        return user;
      },
      logout: async () => {
        try {
          await api<void>("/api/auth/logout", { method: "POST" });
        } finally {
          signedOut();
        }
      },
      setUser: (user) => queryClient.setQueryData(ME_KEY, user),
    }),
    [me.data, me.isPending, queryClient, resetSession, signedOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

export function useCurrentUser(): User {
  const { user } = useAuth();
  if (!user) throw new Error("useCurrentUser requires a signed-in user");
  return user;
}
