import { useState } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, Link, RouterProvider } from "react-router";
import { AuthProvider, useAuth } from "@/hooks/useAuth";
import { useApplyTheme } from "@/hooks/useTheme";
import { createQueryClient } from "@/lib/queryClient";
import { ToastProvider } from "@/components/ui/Toast";
import { AppLayout } from "@/layouts/AppLayout";
import { RedirectIfAuthenticated, RequireAuth } from "@/routes/guards";
import { TransactionDialogProvider } from "@/features/transactions/TransactionDialogs";
import { LoginPage, RegisterPage } from "@/features/auth/AuthPages";
import { DashboardPage } from "@/features/dashboard/DashboardPage";
import { TransactionsPage } from "@/features/transactions/TransactionsPage";
import { BudgetsPage } from "@/features/budgets/BudgetsPage";
import { GoalsPage } from "@/features/goals/GoalsPage";
import { AnalyticsPage } from "@/features/analytics/AnalyticsPage";
import { CategoriesPage } from "@/features/categories/CategoriesPage";
import { SettingsPage } from "@/features/settings/SettingsPage";
import { SearchPage } from "@/features/search/SearchPage";

function NotFound() {
  return (
    <div className="mx-auto max-w-md py-24 text-center">
      <p className="text-sm font-medium text-muted">404</p>
      <h1 className="mt-1 text-xl font-semibold text-ink">This page doesn't exist</h1>
      <Link to="/" className="mt-4 inline-block text-sm font-medium text-primary-text hover:underline">
        Back to the dashboard
      </Link>
    </div>
  );
}

function SignedInShell() {
  return (
    <TransactionDialogProvider>
      <AppLayout />
    </TransactionDialogProvider>
  );
}

export const routes = [
  {
    element: <RedirectIfAuthenticated />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <SignedInShell />,
        children: [
          { path: "/", element: <DashboardPage /> },
          { path: "/transactions", element: <TransactionsPage /> },
          { path: "/budgets", element: <BudgetsPage /> },
          { path: "/goals", element: <GoalsPage /> },
          { path: "/analytics", element: <AnalyticsPage /> },
          { path: "/categories", element: <CategoriesPage /> },
          { path: "/settings", element: <SettingsPage /> },
          { path: "/search", element: <SearchPage /> },
          { path: "*", element: <NotFound /> },
        ],
      },
    ],
  },
];

function ThemeForSignedOut() {
  const { user } = useAuth();
  useApplyTheme(user ? user.theme : "SYSTEM");
  return null;
}

export function App() {
  const [queryClient] = useState(createQueryClient);
  const [router] = useState(() => createBrowserRouter(routes));
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AuthProvider>
          <ThemeForSignedOut />
          <RouterProvider router={router} />
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  );
}
