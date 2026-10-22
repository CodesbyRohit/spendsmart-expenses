import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi, renderWithProviders } from "@/test/utils";
import type { BudgetMonth, Category } from "@/types/api";
import { BudgetsPage } from "./BudgetsPage";

const month: BudgetMonth = {
  month: "2026-10",
  currency: "INR",
  planExists: true,
  sample: false,
  total: { limit: "5000.00", spent: "4300.00", remaining: "700.00", overBy: "0.00", percentUsed: "86.0", barPercent: "86.0", status: "WARNING" },
  lines: [
    { category: { id: "c1", name: "Transportation", type: "EXPENSE", icon: "bus" }, measure: { limit: "1000.00", spent: "1200.00", remaining: "-200.00", overBy: "200.00", percentUsed: "120.0", barPercent: "100.0", status: "EXCEEDED" } },
    { category: { id: "c2", name: "Groceries", type: "EXPENSE", icon: "shopping-cart" }, measure: { limit: "3000.00", spent: "2500.00", remaining: "500.00", overBy: "0.00", percentUsed: "83.3", barPercent: "83.3", status: "WARNING" } },
  ],
  allocated: "6000.00",
  unbudgetedSpent: "600.00",
  overAllocated: true,
  alertThreshold: 80,
  dailyAllowance: "41.17",
  daysLeft: 17,
};

const categories: Category[] = [
  { id: "c1", name: "Transportation", type: "EXPENSE", icon: "bus", isDefault: true, archived: false, transactionCount: 2 },
  { id: "c2", name: "Groceries", type: "EXPENSE", icon: "shopping-cart", isDefault: true, archived: false, transactionCount: 3 },
];

function handlers(saved: unknown[]) {
  return mockApi((url, init) => {
    if (url.pathname === "/api/budgets/currencies") return { body: ["INR"] };
    if (url.pathname === "/api/budgets/history") return { body: [] };
    if (url.pathname === "/api/categories") return { body: categories };
    if (url.pathname.startsWith("/api/budgets/") && init.method === "PUT") {
      saved.push(JSON.parse(String(init.body)));
      return { body: month };
    }
    if (url.pathname.startsWith("/api/budgets/")) return { body: month };
    return undefined;
  });
}

describe("budgets page", () => {
  it("shows overspending honestly: true percentage, clamped bar, amount over, and an over-allocation warning", async () => {
    handlers([]);
    renderWithProviders(<BudgetsPage />, { route: "/budgets?month=2026-10&currency=INR" });
    const transport = (await screen.findByText("Transportation")).closest("li")!;
    expect(within(transport).getByText("Over budget")).toBeInTheDocument();
    expect(within(transport).getByText("Over by ₹200.00")).toBeInTheDocument();
    expect(within(transport).getByText(/120%/)).toBeInTheDocument();
    expect(within(transport).getByRole("meter")).toHaveAttribute("aria-valuetext", "120% used");

    const groceries = screen.getByText("Groceries").closest("li")!;
    expect(within(groceries).getByText("Over 80%")).toBeInTheDocument();
    expect(within(groceries).getByText("₹500.00 left")).toBeInTheDocument();

    expect(screen.getByText("Category limits add up to more than the overall limit.")).toBeInTheDocument();
    expect(screen.getByText("₹41.17")).toBeInTheDocument();
  });

  it("editor validates limits before saving and sends only filled lines", async () => {
    const saved: unknown[] = [];
    handlers(saved);
    const user = userEvent.setup();
    renderWithProviders(<BudgetsPage />, { route: "/budgets?month=2026-10&currency=INR" });
    await user.click(await screen.findByRole("button", { name: "Edit budget" }));
    const dialog = await screen.findByRole("dialog");
    const overall = within(dialog).getByLabelText(/Overall monthly limit/);
    await user.clear(overall);
    await user.type(overall, "-10");
    await user.click(within(dialog).getByRole("button", { name: "Save budget" }));
    expect(await within(dialog).findByText("Use digits only, e.g. 1250.50.")).toBeInTheDocument();
    expect(saved).toHaveLength(0);

    await user.clear(overall);
    await user.type(overall, "6000");
    await user.click(within(dialog).getByRole("button", { name: "Save budget" }));
    await screen.findByText(/Budget for October 2026 saved/);
    expect(saved[0]).toEqual({ currency: "INR", totalLimit: "6000", lines: [{ categoryId: "c1", limit: "1000.00" }, { categoryId: "c2", limit: "3000.00" }] });
  });
});
