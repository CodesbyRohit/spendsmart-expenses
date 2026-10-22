import { expect, type Page } from "@playwright/test";

export const PASSWORD = "e2e-correct-horse-battery";

export function uniqueEmail(tag: string): string {
  return `${tag}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`;
}

/** Registers through the real UI and lands on the dashboard. */
export async function registerViaUi(page: Page, name: string, email = uniqueEmail(name.toLowerCase())): Promise<string> {
  await page.goto("/register");
  await page.getByLabel("Your name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByLabel("Main currency").selectOption("INR");
  await page.getByLabel("Number format").selectOption("en-IN");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  return email;
}

export async function addTransaction(page: Page, opts: { type?: "Expense" | "Income"; amount: string; description: string; category: string; date?: string; merchant?: string }) {
  await page.getByRole("button", { name: "Add transaction" }).first().click();
  const dialog = page.getByRole("dialog", { name: "Add transaction" });
  await expect(dialog).toBeVisible();
  if (opts.type === "Income") await dialog.getByText("Income", { exact: true }).click();
  await dialog.getByLabel("Amount").fill(opts.amount);
  await dialog.getByLabel("Description").fill(opts.description);
  await dialog.getByLabel("Category").selectOption({ label: opts.category });
  if (opts.date) await dialog.getByLabel("Date").fill(opts.date);
  if (opts.merchant) await dialog.getByLabel(/Merchant|Payer/).fill(opts.merchant);
  await dialog.getByRole("button", { name: "Add transaction" }).click();
  await expect(dialog).toBeHidden();
}

export function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
