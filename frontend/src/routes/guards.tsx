import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { PageLoader } from "@/components/ui/Spinner";

/** Client-side guard for navigation only — every API call is independently authorised by the server. */
export function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <PageLoader label="Checking your session" />;
  if (!user) {
    const next = location.pathname + location.search;
    return <Navigate to={`/login${next !== "/" ? `?next=${encodeURIComponent(next)}` : ""}`} replace />;
  }
  return <Outlet />;
}

export function RedirectIfAuthenticated() {
  const { user, loading } = useAuth();
  if (loading) return <PageLoader label="Checking your session" />;
  if (user) return <Navigate to="/" replace />;
  return <Outlet />;
}

/** Only same-app relative paths are honoured as post-login destinations (no open redirects). */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return "/";
  return next;
}
