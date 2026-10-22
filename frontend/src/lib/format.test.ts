import { describe, expect, it } from "vitest";
import { currencyDecimals, formatDate, formatMoney, formatPercent, parseISODate, toISODate } from "./format";
import { comparisonLabel, detectPreset, isValidISODate, presetRange, shiftMonth } from "./dates";
import { safeNext } from "@/routes/guards";

describe("money and number formatting", () => {
  it("formats server decimal strings in the user's locale and currency", () => {
    expect(formatMoney("123456.5", "INR", "en-IN")).toBe("₹1,23,456.50");
    expect(formatMoney("1234.5", "USD", "en-US")).toBe("$1,234.50");
    expect(formatMoney("1500", "JPY", "ja-JP")).toMatch(/1,500/);
  });

  it("uses a real minus sign for negatives and optional plus for positives", () => {
    expect(formatMoney("-200", "INR", "en-IN")).toBe("−₹200.00");
    expect(formatMoney("200", "INR", "en-IN", { signed: true })).toBe("+₹200.00");
  });

  it("shows a dash rather than a fake zero for missing values", () => {
    expect(formatMoney(undefined, "INR", "en-IN")).toBe("—");
    expect(formatPercent(undefined, "en-IN")).toBe("—");
  });

  it("formats percentages from decimal strings", () => {
    expect(formatPercent("83.3", "en-IN")).toBe("83.3%");
    expect(formatPercent("-12.0", "en-IN", { signed: true })).toBe("−12%");
  });

  it("knows each currency's minor units", () => {
    expect(currencyDecimals("INR")).toBe(2);
    expect(currencyDecimals("JPY")).toBe(0);
    expect(currencyDecimals("KWD")).toBe(3);
  });
});

describe("dates", () => {
  it("parses yyyy-MM-dd as a local date (no UTC day shift)", () => {
    const d = parseISODate("2026-10-01");
    expect(d.getDate()).toBe(1);
    expect(d.getMonth()).toBe(9);
    expect(toISODate(d)).toBe("2026-10-01");
    expect(formatDate("2026-10-01", "en-GB")).toBe("1 Oct 2026");
  });

  it("validates real calendar dates", () => {
    expect(isValidISODate("2026-02-28")).toBe(true);
    expect(isValidISODate("2026-02-30")).toBe(false);
    expect(isValidISODate("2026-2-3")).toBe(false);
  });

  it("builds presets and recognises them", () => {
    const today = new Date(2026, 9, 15);
    expect(presetRange("this-month", today)).toEqual({ from: "2026-10-01", to: "2026-10-31" });
    expect(presetRange("last-month", today)).toEqual({ from: "2026-09-01", to: "2026-09-30" });
    expect(presetRange("last-30", today)).toEqual({ from: "2026-09-16", to: "2026-10-15" });
    expect(detectPreset({ from: "2026-09-01", to: "2026-09-30" }, today)).toBe("last-month");
    expect(detectPreset({ from: "2026-09-02", to: "2026-09-30" }, today)).toBe("custom");
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
  });
});

describe("comparison labels match what the server compares against", () => {
  it("names whole-month ranges by month count and anything else as the previous period", () => {
    expect(comparisonLabel({ from: "2026-10-01", to: "2026-10-31" })).toBe("last month");
    expect(comparisonLabel({ from: "2024-02-01", to: "2024-02-29" })).toBe("last month");
    expect(comparisonLabel({ from: "2026-07-01", to: "2026-09-30" })).toBe("the previous 3 months");
    expect(comparisonLabel({ from: "2026-01-01", to: "2026-12-31" })).toBe("the previous 12 months");
    // Starts on the 1st but doesn't end on a month end: the server uses the same number of days before it.
    expect(comparisonLabel({ from: "2026-07-01", to: "2026-09-28" })).toBe("the previous period");
    expect(comparisonLabel({ from: "2026-09-16", to: "2026-10-15" })).toBe("the previous period");
  });
});

describe("post-login redirects", () => {
  it("only allows same-app relative paths", () => {
    expect(safeNext("/budgets?month=2026-10")).toBe("/budgets?month=2026-10");
    expect(safeNext("https://evil.example")).toBe("/");
    expect(safeNext("//evil.example")).toBe("/");
    expect(safeNext("/\\evil.example")).toBe("/");
    expect(safeNext(null)).toBe("/");
  });
});
