import { expect, test } from "@playwright/test";
import { registerViaUi } from "./helpers";

/** Runs in the "mobile" project (Pixel 7 viewport, 412px wide) and at 375px. */
test("mobile layout: bottom navigation, no horizontal scroll, forms fit", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 760 });
  await registerViaUi(page, "Riya");

  const nav = page.getByRole("navigation", { name: "Main" }).last();
  await expect(nav).toBeVisible();
  await expect(nav.getByRole("button", { name: "Add transaction" })).toBeVisible();

  // Load sample data so pages have real content to lay out.
  await page.getByRole("button", { name: "Explore with sample data" }).click();
  await expect(page.getByText("Sample data is loaded.")).toBeVisible();

  for (const path of ["/", "/transactions", "/budgets", "/goals", "/analytics", "/categories", "/settings"]) {
    await page.goto(path);
    await page.waitForLoadState("networkidle");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, `horizontal overflow on ${path}`).toBeLessThanOrEqual(1);
  }

  await page.goto("/transactions");
  // Cards, not the wide table, on narrow screens.
  await expect(page.locator("table")).toBeHidden();
  await nav.getByRole("button", { name: "Add transaction" }).click();
  const dialog = page.getByRole("dialog", { name: "Add transaction" });
  const box = await dialog.boundingBox();
  expect(box!.width).toBeLessThanOrEqual(375);
  expect(box!.x).toBeGreaterThanOrEqual(0);

  // Sample data is removable in one step and leaves no sample rows behind.
  await dialog.getByRole("button", { name: "Cancel" }).click();
  await page.getByRole("button", { name: "Remove sample data" }).click();
  await expect(page.getByText("Sample data is loaded.")).toBeHidden();
  await page.goto("/transactions");
  await expect(page.getByText("No transactions yet")).toBeVisible();
});
