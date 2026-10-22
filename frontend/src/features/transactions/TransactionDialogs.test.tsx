import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { mockApi, renderWithProviders } from "@/test/utils";
import type { Category, Transaction } from "@/types/api";
import { useTransactionDialogs } from "./TransactionDialogs";

const categories: Category[] = [
  { id: "c-groc", name: "Groceries", type: "EXPENSE", icon: "shopping-cart", isDefault: true, archived: false, transactionCount: 0 },
  { id: "c-old", name: "Old stuff", type: "EXPENSE", icon: "circle-dot", isDefault: false, archived: true, transactionCount: 1 },
  { id: "c-sal", name: "Salary", type: "INCOME", icon: "briefcase", isDefault: true, archived: false, transactionCount: 0 },
];

const existing: Transaction = {
  id: "t1", type: "EXPENSE", amount: "250.00", currency: "INR",
  category: { id: "c-groc", name: "Groceries", type: "EXPENSE", icon: "shopping-cart" },
  description: "Vegetables", transactionDate: "2026-10-10", merchant: "FreshMart", sample: false,
  createdAt: "2026-10-10T08:00:00Z", updatedAt: "2026-10-10T08:00:00Z", version: 3,
};

function Launcher() {
  const d = useTransactionDialogs();
  return (
    <>
      <button onClick={() => d.create()}>open create</button>
      <button onClick={() => d.edit(existing)}>open edit</button>
    </>
  );
}

function commonHandlers(onWrite: (method: string, path: string, body: unknown) => { status?: number; body?: unknown }) {
  return mockApi((url, init) => {
    if (url.pathname === "/api/categories") return { body: categories };
    if (url.pathname === "/api/account/options") return { body: { currencies: ["INR", "USD", "JPY"], locales: ["en-IN"] } };
    if (init.method && init.method !== "GET") return onWrite(init.method, url.pathname, JSON.parse(String(init.body)));
    return { body: {} };
  });
}

describe("transaction form", () => {
  it("validates on the client before sending anything", async () => {
    const { calls } = commonHandlers(() => ({ status: 500 }));
    const user = userEvent.setup();
    renderWithProviders(<Launcher />);
    await user.click(await screen.findByText("open create"));
    const dialog = await screen.findByRole("dialog", { name: "Add transaction" });
    await user.type(within(dialog).getByLabelText("Amount"), "10.999");
    await user.click(within(dialog).getByRole("button", { name: "Add transaction" }));
    expect(await within(dialog).findByText("Use at most 2 decimal places.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter a description.")).toBeInTheDocument();
    expect(within(dialog).getByText("Choose a category.")).toBeInTheDocument();
    expect(calls.filter((c) => c.method === "POST")).toHaveLength(0);
  });

  it("offers only active categories of the selected type and posts a typed payload", async () => {
    const posted: unknown[] = [];
    commonHandlers((_method, _path, body) => {
      posted.push(body);
      return { status: 201, body: { ...existing, ...(body as object), id: "t2", type: "INCOME" } };
    });
    const user = userEvent.setup();
    renderWithProviders(<Launcher />);
    await user.click(await screen.findByText("open create"));
    const dialog = await screen.findByRole("dialog", { name: "Add transaction" });
    const category = within(dialog).getByLabelText("Category");
    await waitFor(() => expect(within(category).getByRole("option", { name: "Groceries" })).toBeInTheDocument());
    expect(within(category).queryByRole("option", { name: /Old stuff/ })).toBeNull();
    expect(within(category).queryByRole("option", { name: "Salary" })).toBeNull();

    await user.click(within(dialog).getByText("Income"));
    expect(within(category).getByRole("option", { name: "Salary" })).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText("Amount"), "52000");
    await user.type(within(dialog).getByLabelText("Description"), "October salary");
    await user.selectOptions(category, "c-sal");
    await user.click(within(dialog).getByRole("button", { name: "Add transaction" }));

    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toMatchObject({ type: "INCOME", amount: "52000", currency: "INR", categoryId: "c-sal", description: "October salary", merchant: null, paymentMethod: null });
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Add transaction" })).toBeNull());
    expect(await screen.findByText("Income added.")).toBeInTheDocument();
  });

  it("edits with the record version and shows server field errors inline", async () => {
    const puts: { path: string; body: unknown }[] = [];
    commonHandlers((_m, path, body) => {
      puts.push({ path, body });
      return { status: 400, body: { code: "VALIDATION_FAILED", detail: "Some fields need attention.", fieldErrors: [{ field: "transactionDate", message: "Choose a date between 1970 and one year from today." }] } };
    });
    const user = userEvent.setup();
    renderWithProviders(<Launcher />);
    await user.click(await screen.findByText("open edit"));
    const dialog = await screen.findByRole("dialog", { name: "Edit transaction" });
    expect(within(dialog).getByLabelText("Amount")).toHaveValue("250.00");
    await user.clear(within(dialog).getByLabelText("Amount"));
    await user.type(within(dialog).getByLabelText("Amount"), "300");
    await user.click(within(dialog).getByRole("button", { name: "Save changes" }));
    expect(await within(dialog).findByText("Choose a date between 1970 and one year from today.")).toBeInTheDocument();
    expect(puts[0]).toMatchObject({ path: "/api/transactions/t1", body: { amount: "300", version: 3, merchant: "FreshMart" } });
    expect(screen.getByRole("dialog", { name: "Edit transaction" })).toBeInTheDocument();
  });
});
