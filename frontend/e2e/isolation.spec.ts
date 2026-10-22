import { expect, test } from "@playwright/test";
import { addTransaction, registerViaUi, today } from "./helpers";

test("one user cannot see or reach another user's records", async ({ browser }) => {
  const aliceCtx = await browser.newContext();
  const alice = await aliceCtx.newPage();
  await registerViaUi(alice, "Alice");
  await addTransaction(alice, { amount: "999", description: "Alice private rent", category: "Housing", date: today() });

  // Grab Alice's transaction id through her own API session.
  const list = await alice.request.get("/api/transactions");
  const aliceTx = (await list.json()).items[0];
  expect(aliceTx.description).toBe("Alice private rent");

  const bobCtx = await browser.newContext();
  const bob = await bobCtx.newPage();
  await registerViaUi(bob, "Bob");

  // Direct object reference attempts are refused by the server…
  const direct = await bob.request.get(`/api/transactions/${aliceTx.id}`);
  expect(direct.status()).toBe(404);
  const csrf = (await bobCtx.cookies()).find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
  const del = await bob.request.delete(`/api/transactions/${aliceTx.id}`, { headers: { "X-XSRF-TOKEN": csrf } });
  expect(del.status()).toBe(404);

  // …and nothing of Alice's appears anywhere in Bob's UI.
  await bob.goto("/transactions");
  await expect(bob.getByText("No transactions yet")).toBeVisible();
  await bob.goto("/search?q=Alice");
  await expect(bob.getByText("Nothing matched")).toBeVisible();

  // Alice's record is untouched.
  await alice.goto("/transactions");
  await expect(alice.getByRole("button", { name: "Alice private rent", exact: true })).toBeVisible();

  // Signed-out users are sent to sign-in, and the API refuses them.
  const anon = await browser.newContext();
  const anonPage = await anon.newPage();
  await anonPage.goto("/transactions");
  await expect(anonPage).toHaveURL(/\/login\?next=%2Ftransactions/);
  expect((await anonPage.request.get("/api/transactions")).status()).toBe(401);

  await Promise.all([aliceCtx.close(), bobCtx.close(), anon.close()]);
});
