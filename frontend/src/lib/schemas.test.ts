import { describe, expect, it } from "vitest";
import { amountSchema, passwordSchema } from "./schemas";

const msg = (r: { success: boolean; error?: { issues: { message: string }[] } }) => r.error?.issues[0]?.message;

describe("amount validation", () => {
  const inr = amountSchema(() => "INR");
  const jpy = amountSchema(() => "JPY");

  it("accepts positive amounts within the currency's decimals", () => {
    expect(inr.safeParse("1250.50").success).toBe(true);
    expect(inr.safeParse("1250.500").success).toBe(true); // trailing zeros are fine
    expect(jpy.safeParse("1500").success).toBe(true);
  });

  it("rejects zero, negatives, text and excess precision", () => {
    expect(msg(inr.safeParse("0"))).toBe("Amount must be greater than zero.");
    expect(msg(inr.safeParse("-5"))).toBe("Use digits only, e.g. 1250.50.");
    expect(msg(inr.safeParse("12,50"))).toBe("Use digits only, e.g. 1250.50.");
    expect(msg(inr.safeParse("1.234"))).toBe("Use at most 2 decimal places.");
    expect(msg(jpy.safeParse("10.5"))).toBe("JPY amounts can't have decimals.");
    expect(msg(inr.safeParse(""))).toBe("Enter amount.");
  });
});

describe("password policy", () => {
  it("matches the server: at least 10 characters, at most 72 bytes", () => {
    expect(passwordSchema.safeParse("short").success).toBe(false);
    expect(passwordSchema.safeParse("long enough pass").success).toBe(true);
    expect(passwordSchema.safeParse("é".repeat(40)).success).toBe(false); // 80 bytes
  });
});
