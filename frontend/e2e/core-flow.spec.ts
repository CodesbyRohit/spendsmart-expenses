import { expect, test } from "@playwright/test";
import { addTransaction, registerViaUi, today } from "./helpers";

test("register, record expenses, budget reflects them, edit and delete keep everything consistent", async ({ page }) => {
  await registerViaUi(page, "Asha");

  // First-run empty state, not fabricated figures.
  await expect(page.getByRole("heading", { name: "Start by recording what you spend" })).toBeVisible();

  await addTransaction(page, { amount: "1250.50", description: "Weekly groceries", category: "Groceries", merchant: "FreshMart", date: today() });
  await addTransaction(page, { type: "Income", amount: "40000", description: "October salary", category: "Salary", date: today() });

  // Dashboard totals now come from the two persisted transactions.
  await expect(page.getByText("Where your money went")).toBeVisible();
  const income = page.locator("section", { hasText: /^Income/ }).first();
  await expect(income).toContainText("₹40,000.00");
  const expenses = page.locator("section", { hasText: /^Expenses/ }).first();
  await expect(expenses).toContainText("₹1,250.50");
  await expect(page.getByText("+₹38,749.50")).toBeVisible();

  // The list shows it; a refresh doesn't lose it (server-persisted, not local state).
  await page.getByRole("link", { name: "Transactions" }).first().click();
  await expect(page.getByRole("button", { name: "Weekly groceries", exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: "Weekly groceries", exact: true })).toBeVisible();

  // Search by merchant.
  await page.getByLabel("Search by description or merchant").fill("freshmart");
  await expect(page).toHaveURL(/q=freshmart/);
  await expect(page.getByRole("button", { name: "Weekly groceries", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "October salary", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Clear filters" }).click();

  // Create a category budget.
  await page.getByRole("link", { name: "Budgets" }).first().click();
  await page.getByRole("button", { name: "Create budget" }).click();
  const editor = page.getByRole("dialog");
  await editor.getByLabel("Overall monthly limit").fill("20000");
  await editor.getByRole("combobox", { name: "Category", exact: true }).first().selectOption({ label: "Groceries" });
  await editor.getByRole("textbox", { name: "Limit", exact: true }).first().fill("1500");
  await editor.getByRole("button", { name: "Save budget" }).click();
  await expect(editor).toBeHidden();

  const groceriesLine = page.locator("li", { hasText: "Groceries" }).filter({ hasText: "of ₹1,500.00" });
  await expect(groceriesLine).toContainText("₹1,250.50 of ₹1,500.00");
  await expect(groceriesLine).toContainText("83.4%");
  await expect(groceriesLine).toContainText(/Over 80%/);

  // Edit the expense upward: the budget goes over, and an in-app alert appears.
  await page.getByRole("link", { name: "Transactions" }).first().click();
  await page.getByRole("button", { name: "Weekly groceries", exact: true }).click();
  await page.getByRole("dialog", { name: "Transaction details" }).getByRole("button", { name: "Edit" }).click();
  const edit = page.getByRole("dialog", { name: "Edit transaction" });
  await edit.getByLabel("Amount").fill("1800");
  await edit.getByRole("button", { name: "Save changes" }).click();
  await expect(edit).toBeHidden();
  await expect(page.getByText("−₹1,800.00").first()).toBeVisible();

  await page.getByRole("link", { name: "Budgets" }).first().click();
  const over = page.locator("li", { hasText: "Groceries" }).filter({ hasText: "of ₹1,500.00" });
  await expect(over).toContainText("Over by ₹300.00");
  await expect(over).toContainText("120%");
  await expect(page.getByRole("button", { name: /Alerts, \d+ unread/ })).toBeVisible();

  // Delete with confirmation; the budget returns to zero spent.
  await page.getByRole("link", { name: "Transactions" }).first().click();
  await page.getByRole("button", { name: "Delete Weekly groceries" }).click();
  await page.getByRole("dialog", { name: "Delete this transaction?" }).getByRole("button", { name: "Delete transaction" }).click();
  await expect(page.getByRole("button", { name: "Weekly groceries", exact: true })).toHaveCount(0);
  await page.getByRole("link", { name: "Budgets" }).first().click();
  await expect(page.locator("li", { hasText: "Groceries" }).filter({ hasText: "of ₹1,500.00" })).toContainText("₹0.00 of ₹1,500.00");
});

test("validation errors are shown inline and nothing is saved", async ({ page }) => {
  await registerViaUi(page, "Vikram");
  await page.getByRole("button", { name: "Add transaction" }).first().click();
  const dialog = page.getByRole("dialog", { name: "Add transaction" });
  await dialog.getByLabel("Amount").fill("12.345");
  await dialog.getByRole("button", { name: "Add transaction" }).click();
  await expect(dialog.getByText("Use at most 2 decimal places.")).toBeVisible();
  await expect(dialog.getByText("Enter a description.")).toBeVisible();
  await expect(dialog.getByText("Choose a category.")).toBeVisible();
  await dialog.getByRole("button", { name: "Cancel" }).click();
  await page.getByRole("link", { name: "Transactions" }).first().click();
  await expect(page.getByText("No transactions yet")).toBeVisible();
});

test("savings goal progress follows recorded contributions", async ({ page }) => {
  await registerViaUi(page, "Meera");
  await page.getByRole("link", { name: "Savings goals" }).click();
  await page.getByRole("button", { name: "New goal" }).click();
  const form = page.getByRole("dialog", { name: "New savings goal" });
  await form.getByLabel("Goal name").fill("New laptop");
  await form.getByLabel("Target amount").fill("60000");
  await form.getByLabel("Already saved").fill("15000");
  await form.getByRole("button", { name: "Create goal" }).click();
  await expect(form).toBeHidden();

  const card = page.locator("section", { hasText: "New laptop" });
  await expect(card).toContainText("₹15,000.00");
  await expect(card).toContainText("25%");

  await card.getByRole("button", { name: "Add money" }).click();
  const contribute = page.getByRole("dialog", { name: "New laptop" });
  await contribute.getByLabel("Amount").fill("5000");
  await contribute.getByRole("button", { name: "Add money" }).click();
  await expect(contribute).toBeHidden();
  await expect(card).toContainText("₹20,000.00");
  await expect(card).toContainText("33.3%");
});
