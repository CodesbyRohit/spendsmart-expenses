import { z } from "zod";
import { currencyDecimals } from "./format";
import { isValidISODate } from "./dates";

/**
 * Client-side validation mirrors the server's rules for fast feedback. The server remains the authority:
 * it re-validates everything and its field errors are shown in the same places.
 */
export function amountSchema(getCurrency: () => string, label = "Amount") {
  return z
    .string()
    .trim()
    .min(1, `Enter ${label.toLowerCase()}.`)
    .refine((v) => /^\d+(\.\d+)?$/.test(v), "Use digits only, e.g. 1250.50.")
    .refine((v) => Number(v) > 0, `${label} must be greater than zero.`)
    .refine((v) => {
      const decimals = currencyDecimals(getCurrency());
      const fraction = v.split(".")[1]?.replace(/0+$/, "") ?? "";
      return fraction.length <= decimals;
    }, {
      error: () => {
        const d = currencyDecimals(getCurrency());
        return d === 0 ? `${getCurrency()} amounts can't have decimals.` : `Use at most ${d} decimal places.`;
      },
    })
    .refine((v) => Number(v) <= 999_999_999_999, `${label} is too large.`);
}

export const isoDateSchema = z.string().refine((v) => isValidISODate(v), "Choose a valid date.");

export const transactionFormSchema = (getCurrency: () => string) =>
  z.object({
    type: z.enum(["EXPENSE", "INCOME"]),
    amount: amountSchema(getCurrency),
    currency: z.string().length(3, "Choose a currency."),
    categoryId: z.string().min(1, "Choose a category."),
    description: z.string().trim().min(1, "Enter a description.").max(140, "Description can be at most 140 characters."),
    transactionDate: isoDateSchema,
    merchant: z.string().trim().max(100, "Merchant can be at most 100 characters."),
    paymentMethod: z.string(),
    notes: z.string().max(1000, "Notes can be at most 1000 characters."),
  });

export type TransactionFormValues = z.infer<ReturnType<typeof transactionFormSchema>>;

export const passwordSchema = z
  .string()
  .min(10, "Use at least 10 characters.")
  .refine((v) => new TextEncoder().encode(v).length <= 72, "Password is too long (maximum 72 bytes).")
  .refine((v) => v.trim().length > 0, "Password can't be only spaces.");
