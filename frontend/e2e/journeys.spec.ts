import fs from "node:fs";
import { expect, test } from "@playwright/test";
import { addTransaction, registerViaUi, today } from "./helpers";

test("custom category, filtering, filtered CSV export, then sign-out locks the app", async ({ page }) => {
  await registerViaUi(page, "Kabir");

  // Create a category through the UI.
  await page.getByRole("link", { name: "Categories" }).click();
  await page.getByRole("button", { name: "New category" }).click();
  const form = page.getByRole("dialog", { name: "New category" });
  await form.getByLabel("Name").fill("Pet care");
  await form.getByRole("radio", { name: "paw print" }).check({ force: true });
  await form.getByRole("button", { name: "Create" }).click();
  await expect(form).toBeHidden();
  await expect(page.getByText("Pet care", { exact: true })).toBeVisible();

  // Use it, alongside an unrelated expense.
  await addTransaction(page, { amount: "1800", description: "Vet visit", category: "Pet care", merchant: "City Vets", date: today() });
  await addTransaction(page, { amount: "450", description: "Groceries run", category: "Groceries", date: today() });

  // Filter by the new category: only its transaction remains, and the filter lives in the URL.
  await page.getByRole("link", { name: "Transactions" }).first().click();
  await page.getByLabel("Category").selectOption({ label: "Pet care" });
  await expect(page).toHaveURL(/categoryId=/);
  await expect(page.getByRole("button", { name: "Vet visit", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Groceries run", exact: true })).toHaveCount(0);

  // Export honours the same filter.
  const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Export CSV" }).click()]);
  expect(download.suggestedFilename()).toMatch(/^spendsmart-transactions-.*\.csv$/);
  const csv = fs.readFileSync((await download.path())!, "utf8").replace(/^﻿/, "");
  const lines = csv.trim().split("\r\n");
  expect(lines[0]).toContain("Date,Type,Amount,Currency,Category,Description");
  expect(lines).toHaveLength(2);
  expect(lines[1]).toContain("1800.00,INR,Pet care,Vet visit,City Vets");

  // Sign out: protected pages redirect to sign-in and the API refuses the old browser session.
  await page.getByRole("button", { name: "Sign out" }).first().click();
  await expect(page).toHaveURL(/\/login/);
  await page.goto("/transactions");
  await expect(page).toHaveURL(/\/login\?next=%2Ftransactions/);
  expect((await page.request.get("/api/transactions")).status()).toBe(401);
});
