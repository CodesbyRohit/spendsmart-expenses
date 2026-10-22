import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { mockApi, renderWithProviders } from "@/test/utils";
import type { Dashboard } from "@/types/api";
import { DashboardPage } from "./DashboardPage";

const totals = (income: string, expenses: string, net: string, ic: number, ec: number) => ({ income, expenses, net, incomeCount: ic, expenseCount: ec });

function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    overview: {
      currency: "INR",
      range: { from: "2026-10-01", to: "2026-10-31", days: 31 },
      previousRange: { from: "2026-09-01", to: "2026-09-30", days: 30 },
      totals: totals("60000.00", "17001.00", "42999.00", 1, 3),
      previousTotals: totals("0.00", "10000.00", "-10000.00", 0, 1),
      comparison: { expenseChangePercent: "70.0", netDelta: "52999.00" },
      savingsRatePercent: "71.7",
      averageDailyExpense: "548.42",
      expenseCategories: [{ categoryId: "c1", name: "Housing", icon: "home", total: "15000.00", count: 1, sharePercent: "88.2", previousTotal: "10000.00", changePercent: "50.0" }],
      incomeCategories: [],
      granularity: "DAY",
      trend: [{ start: "2026-10-01", end: "2026-10-01", income: "60000.00", expenses: "15000.00" }],
      insights: [{ rule: "expense-change", tone: "WARNING", message: "Total expenses are 70% higher than in the previous month." }],
    },
    budget: {
      month: "2026-10", currency: "INR", planExists: true, sample: false,
      total: { limit: "10000.00", spent: "17001.00", remaining: "-7001.00", overBy: "7001.00", percentUsed: "170.0", barPercent: "100.0", status: "EXCEEDED" },
      lines: [], allocated: "0.00", unbudgetedSpent: "17001.00", overAllocated: false, alertThreshold: 80,
    },
    recentTransactions: [],
    goals: [],
    monthly: [],
    hasAnyTransactions: true,
    sampleDataPresent: false,
    currencies: ["INR"],
    ...overrides,
  };
}

describe("dashboard", () => {
  it("shows a loading state, then the first-run empty state for a new account", async () => {
    mockApi((url) => (url.pathname === "/api/dashboard" ? { body: dashboard({ hasAnyTransactions: false }) } : undefined));
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByLabelText("Loading dashboard")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Start by recording what you spend" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Explore with sample data" })).toBeInTheDocument();
    // No invented numbers on an empty account.
    expect(screen.queryByText(/₹/)).toBeNull();
  });

  it("renders server totals verbatim, labels net as not a bank balance, and warns when over budget", async () => {
    mockApi((url) => (url.pathname === "/api/dashboard" ? { body: dashboard() } : undefined));
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByText("₹60,000.00")).toBeInTheDocument();
    expect(screen.getAllByText("₹17,001.00").length).toBeGreaterThan(0);
    expect(screen.getByText("+₹42,999.00")).toBeInTheDocument();
    expect(screen.getByText(/not a bank balance/i)).toBeInTheDocument();
    expect(screen.getByText("Over by ₹7,001.00")).toBeInTheDocument();
    // Income had no baseline: explained, not a fabricated percentage.
    expect(screen.getByText("Nothing recorded last month")).toBeInTheDocument();
    expect(screen.getByText("Total expenses are 70% higher than in the previous month.")).toBeInTheDocument();
  });

  it("shows a retryable error instead of empty numbers when the API fails", async () => {
    mockApi((url) => (url.pathname === "/api/dashboard" ? { status: 500, body: { code: "INTERNAL_ERROR", detail: "Something went wrong on our side. Please try again." } } : undefined));
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByText("Something went wrong on our side. Please try again.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});
