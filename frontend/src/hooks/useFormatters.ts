import { useMemo } from "react";
import { useAuth } from "./useAuth";
import { formatDate, formatDateTime, formatMoney, formatMonth, formatPercent, formatRange } from "@/lib/format";
import type { Decimal, ISODate } from "@/types/api";

/** Binds the formatting helpers to the signed-in user's locale preference. */
export function useFormatters() {
  const { user } = useAuth();
  const locale = user?.locale ?? (typeof navigator !== "undefined" ? navigator.language : "en-IN");
  return useMemo(
    () => ({
      locale,
      money: (amount: Decimal | number | null | undefined, currency: string, opts?: { compact?: boolean; signed?: boolean }) =>
        formatMoney(amount, currency, locale, opts),
      percent: (value: Decimal | null | undefined, opts?: { signed?: boolean }) => formatPercent(value, locale, opts),
      date: (date: ISODate | null | undefined, style?: "short" | "medium" | "long") => formatDate(date, locale, style),
      month: (month: string, style?: "short" | "long") => formatMonth(month, locale, style),
      dateTime: (instant: string) => formatDateTime(instant, locale),
      range: (from: ISODate, to: ISODate) => formatRange(from, to, locale),
    }),
    [locale],
  );
}
