import { useEffect, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useCategories } from "@/features/categories/api";
import { useAccountOptions } from "@/features/settings/api";
import { useCurrentUser } from "@/hooks/useAuth";
import { Input, Select, Textarea } from "@/components/ui/Field";
import { Segmented } from "@/components/ui/Segmented";
import { InlineAlert } from "@/components/ui/States";
import { applyServerErrors } from "@/lib/forms";
import { transactionFormSchema, type TransactionFormValues } from "@/lib/schemas";
import { PAYMENT_METHOD_LABELS, currencySymbol } from "@/lib/format";
import { todayISO } from "@/lib/dates";
import type { PaymentMethod, Transaction, TransactionInput, TransactionType } from "@/types/api";

const FIELDS = ["type", "amount", "currency", "categoryId", "description", "transactionDate", "merchant", "paymentMethod", "notes"] as const;

interface Props {
  transaction?: Transaction;
  defaults?: { type?: TransactionType };
  onSubmit: (input: TransactionInput) => Promise<void>;
  formId: string;
}

export function TransactionForm({ transaction, defaults, onSubmit, formId }: Props) {
  const user = useCurrentUser();
  const categories = useCategories(true);
  const options = useAccountOptions();
  const [formError, setFormError] = useState<string | null>(null);

  // The currency is read lazily by the amount rule so decimal limits follow the selected currency.
  const currencyRef = useMemo(() => ({ current: transaction?.currency ?? user.defaultCurrency }), [transaction, user.defaultCurrency]);
  const schema = useMemo(() => transactionFormSchema(() => currencyRef.current), [currencyRef]);

  const { register, handleSubmit, control, watch, setValue, setError, formState: { errors } } = useForm<TransactionFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: transaction?.type ?? defaults?.type ?? "EXPENSE",
      amount: transaction?.amount ?? "",
      currency: transaction?.currency ?? user.defaultCurrency,
      categoryId: transaction?.category.id ?? "",
      description: transaction?.description ?? "",
      transactionDate: transaction?.transactionDate ?? todayISO(),
      merchant: transaction?.merchant ?? "",
      paymentMethod: transaction?.paymentMethod ?? "",
      notes: transaction?.notes ?? "",
    },
  });

  const type = watch("type");
  const currency = watch("currency");
  const categoryId = watch("categoryId");
  currencyRef.current = currency;

  // Only categories of the chosen type; archived ones are hidden unless this transaction already uses it.
  const available = useMemo(
    () => (categories.data ?? []).filter((c) => c.type === type && (!c.archived || c.id === transaction?.category.id)),
    [categories.data, type, transaction],
  );

  useEffect(() => {
    if (categoryId && categories.data && !available.some((c) => c.id === categoryId)) {
      setValue("categoryId", "");
    }
  }, [categoryId, categories.data, available, setValue]);

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({
        type: values.type,
        amount: values.amount.trim(),
        currency: values.currency,
        categoryId: values.categoryId,
        description: values.description.trim(),
        transactionDate: values.transactionDate,
        merchant: values.merchant.trim() || null,
        paymentMethod: (values.paymentMethod || null) as PaymentMethod | null,
        notes: values.notes.trim() || null,
        ...(transaction ? { version: transaction.version } : {}),
      });
    } catch (e) {
      setFormError(applyServerErrors(e, setError, FIELDS));
    }
  });

  const currencies = options.data?.currencies ?? [user.defaultCurrency];

  return (
    <form id={formId} onSubmit={submit} noValidate className="space-y-4">
      {formError && <InlineAlert>{formError}</InlineAlert>}
      <Controller
        control={control}
        name="type"
        render={({ field }) => (
          <Segmented
            label="Transaction type"
            value={field.value}
            onChange={field.onChange}
            className="w-full"
            options={[
              { value: "EXPENSE", label: "Expense" },
              { value: "INCOME", label: "Income" },
            ]}
          />
        )}
      />
      <div className="grid grid-cols-[1fr_7rem] gap-3">
        <Input
          label="Amount"
          inputMode="decimal"
          autoComplete="off"
          placeholder="0.00"
          prefix={currencySymbol(currency, user.locale)}
          className="tabular text-base"
          error={errors.amount?.message}
          autoFocus={!transaction}
          {...register("amount")}
        />
        <Select label="Currency" error={errors.currency?.message} {...register("currency")}>
          {currencies.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </Select>
      </div>
      <Input label="Description" placeholder={type === "EXPENSE" ? "e.g. Weekly groceries" : "e.g. October salary"} maxLength={140} error={errors.description?.message} {...register("description")} />
      <div className="grid gap-3 sm:grid-cols-2">
        <Select label="Category" error={errors.categoryId?.message} disabled={categories.isPending} {...register("categoryId")}>
          <option value="">{categories.isPending ? "Loading…" : "Choose a category"}</option>
          {available.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.archived ? " (archived)" : ""}
            </option>
          ))}
        </Select>
        <Input label="Date" type="date" error={errors.transactionDate?.message} {...register("transactionDate")} />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Input label={type === "EXPENSE" ? "Merchant or payee" : "Payer"} optional maxLength={100} error={errors.merchant?.message} {...register("merchant")} />
        <Select label="Payment method" optional error={errors.paymentMethod?.message} {...register("paymentMethod")}>
          <option value="">Not specified</option>
          {Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </div>
      <Textarea label="Notes" optional rows={2} maxLength={1000} error={errors.notes?.message} {...register("notes")} />
    </form>
  );
}
