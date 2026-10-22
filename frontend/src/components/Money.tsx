import { useFormatters } from "@/hooks/useFormatters";
import { cn } from "@/lib/cn";
import type { Decimal, TransactionType } from "@/types/api";

/** Formats a server decimal in the user's locale. Income is green with "+", expenses use neutral ink with "−". */
export function Money({ amount, currency, type, className, compact }: { amount: Decimal; currency: string; type?: TransactionType; className?: string; compact?: boolean }) {
  const { money } = useFormatters();
  const text = money(amount, currency, { compact });
  return (
    <span className={cn("tabular whitespace-nowrap", type === "INCOME" && "text-primary-text", className)}>
      {type === "INCOME" ? "+" : type === "EXPENSE" ? "−" : ""}
      {text}
    </span>
  );
}
