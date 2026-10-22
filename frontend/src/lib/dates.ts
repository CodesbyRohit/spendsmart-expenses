import type { ISODate } from "@/types/api";
import { parseISODate, toISODate } from "./format";

export type RangePreset = "this-month" | "last-month" | "last-30" | "last-90" | "this-year" | "custom";

export interface DateRangeValue {
  from: ISODate;
  to: ISODate;
}

export const PRESET_LABELS: Record<RangePreset, string> = {
  "this-month": "This month",
  "last-month": "Last month",
  "last-30": "Last 30 days",
  "last-90": "Last 90 days",
  "this-year": "This year",
  custom: "Custom range",
};

export function presetRange(preset: Exclude<RangePreset, "custom">, today = new Date()): DateRangeValue {
  const y = today.getFullYear();
  const m = today.getMonth();
  switch (preset) {
    case "this-month":
      return { from: toISODate(new Date(y, m, 1)), to: toISODate(new Date(y, m + 1, 0)) };
    case "last-month":
      return { from: toISODate(new Date(y, m - 1, 1)), to: toISODate(new Date(y, m, 0)) };
    case "last-30":
      return { from: toISODate(addDays(today, -29)), to: toISODate(today) };
    case "last-90":
      return { from: toISODate(addDays(today, -89)), to: toISODate(today) };
    case "this-year":
      return { from: toISODate(new Date(y, 0, 1)), to: toISODate(new Date(y, 11, 31)) };
  }
}

export function detectPreset(range: DateRangeValue, today = new Date()): RangePreset {
  for (const preset of ["this-month", "last-month", "last-30", "last-90", "this-year"] as const) {
    const r = presetRange(preset, today);
    if (r.from === range.from && r.to === range.to) return preset;
  }
  return "custom";
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function todayISO(): ISODate {
  return toISODate(new Date());
}

export function currentMonth(): string {
  return todayISO().slice(0, 7);
}

export function shiftMonth(month: string, delta: number): string {
  const d = parseISODate(`${month}-01`);
  d.setMonth(d.getMonth() + delta);
  return toISODate(d).slice(0, 7);
}

/**
 * Words for the period the server compares against (it mirrors DateRange.previous() on the backend):
 * whole calendar months compare with the same number of preceding months; anything else with the same
 * number of days immediately before.
 */
export function comparisonLabel(range: DateRangeValue): string {
  const from = parseISODate(range.from);
  const to = parseISODate(range.to);
  const endOfToMonth = new Date(to.getFullYear(), to.getMonth() + 1, 0).getDate();
  if (from.getDate() === 1 && to.getDate() === endOfToMonth) {
    const months = (to.getFullYear() - from.getFullYear()) * 12 + (to.getMonth() - from.getMonth()) + 1;
    return months === 1 ? "last month" : `the previous ${months} months`;
  }
  return "the previous period";
}

export function isValidISODate(value: string | null | undefined): value is ISODate {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  return toISODate(parseISODate(value)) === value;
}
