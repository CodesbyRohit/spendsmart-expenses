import type { Decimal, ISODate, YearMonthString } from "@/types/api";

/**
 * Display formatting only. Every figure is computed by the server; these helpers never do arithmetic on money
 * beyond converting the server's decimal string for Intl formatting.
 */
const moneyFormatters = new Map<string, Intl.NumberFormat>();

function moneyFormatter(currency: string, locale: string, compact: boolean): Intl.NumberFormat {
  const key = `${locale}|${currency}|${compact}`;
  let f = moneyFormatters.get(key);
  if (!f) {
    f = new Intl.NumberFormat(locale, {
      style: "currency",
      currency,
      ...(compact ? { notation: "compact", maximumFractionDigits: 1 } : {}),
    });
    moneyFormatters.set(key, f);
  }
  return f;
}

export function formatMoney(amount: Decimal | number | null | undefined, currency: string, locale: string, opts?: { compact?: boolean; signed?: boolean }): string {
  if (amount === null || amount === undefined || amount === "") return "—";
  const value = typeof amount === "number" ? amount : Number(amount);
  if (!Number.isFinite(value)) return "—";
  const formatted = moneyFormatter(currency, locale, opts?.compact ?? false).format(Math.abs(value));
  if (value < 0) return `−${formatted}`;
  if (opts?.signed && value > 0) return `+${formatted}`;
  return formatted;
}

export function formatPercent(value: Decimal | null | undefined, locale: string, opts?: { signed?: boolean }): string {
  if (value === null || value === undefined) return "—";
  const n = Number(value);
  const f = new Intl.NumberFormat(locale, { maximumFractionDigits: 1, minimumFractionDigits: 0 });
  const body = `${f.format(Math.abs(n))}%`;
  if (n < 0) return `−${body}`;
  if (opts?.signed && n > 0) return `+${body}`;
  return body;
}

/** Parses yyyy-MM-dd as a LOCAL calendar date (new Date("2026-10-01") would be UTC midnight and can shift a day). */
export function parseISODate(date: ISODate): Date {
  const [y, m, d] = date.split("-").map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
}

export function toISODate(date: Date): ISODate {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function formatDate(date: ISODate | null | undefined, locale: string, style: "short" | "medium" | "long" = "medium"): string {
  if (!date) return "—";
  const options: Intl.DateTimeFormatOptions =
    style === "short" ? { day: "numeric", month: "short" }
      : style === "long" ? { weekday: "short", day: "numeric", month: "long", year: "numeric" }
        : { day: "numeric", month: "short", year: "numeric" };
  return new Intl.DateTimeFormat(locale, options).format(parseISODate(date));
}

export function formatMonth(month: YearMonthString, locale: string, style: "short" | "long" = "long"): string {
  const [y, m] = month.split("-").map(Number);
  return new Intl.DateTimeFormat(locale, { month: style === "short" ? "short" : "long", year: "numeric" })
    .format(new Date(y ?? 1970, (m ?? 1) - 1, 1));
}

export function formatDateTime(instant: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(instant));
}

export function formatRange(from: ISODate, to: ISODate, locale: string): string {
  if (from === to) return formatDate(from, locale);
  const a = parseISODate(from);
  const b = parseISODate(to);
  const sameYear = a.getFullYear() === b.getFullYear();
  const left = new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", ...(sameYear ? {} : { year: "numeric" }) }).format(a);
  return `${left} – ${formatDate(to, locale)}`;
}

export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  CASH: "Cash",
  DEBIT_CARD: "Debit card",
  CREDIT_CARD: "Credit card",
  UPI: "UPI",
  BANK_TRANSFER: "Bank transfer",
  WALLET: "Wallet",
  OTHER: "Other",
};

/** Number of minor units (decimal places) for a currency, as Intl reports it. */
export function currencyDecimals(currency: string): number {
  try {
    return new Intl.NumberFormat("en", { style: "currency", currency }).resolvedOptions().maximumFractionDigits ?? 2;
  } catch {
    return 2;
  }
}

export function currencySymbol(currency: string, locale: string): string {
  try {
    const part = new Intl.NumberFormat(locale, { style: "currency", currency, currencyDisplay: "narrowSymbol" })
      .formatToParts(0)
      .find((p) => p.type === "currency");
    return part?.value ?? currency;
  } catch {
    return currency;
  }
}
