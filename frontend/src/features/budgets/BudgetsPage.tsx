import { useEffect, useId, useState } from "react";
import { useSearchParams } from "react-router";
import { ChevronLeft, ChevronRight, Copy, Gauge, Pencil, Plus, Trash2, X } from "lucide-react";
import { useCurrentUser } from "@/hooks/useAuth";
import { useFormatters } from "@/hooks/useFormatters";
import { Card, CardBody, CardHeader, PageHeader, Badge } from "@/components/ui/Card";
import { Button, IconButton } from "@/components/ui/Button";
import { ConfirmDialog, Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Field";
import { EmptyState, ErrorState, InlineAlert } from "@/components/ui/States";
import { PageLoader } from "@/components/ui/Spinner";
import { ProgressBar, StatusPill } from "@/components/ui/Progress";
import { useToast } from "@/components/ui/Toast";
import { CategoryIcon } from "@/components/CategoryIcon";
import { CurrencyControl } from "@/components/filters";
import { useCategories } from "@/features/categories/api";
import { ApiError, errorMessage } from "@/lib/api";
import { currentMonth, shiftMonth } from "@/lib/dates";
import { amountSchema } from "@/lib/schemas";
import { currencySymbol } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { BudgetMonth } from "@/types/api";
import { useBudgetCurrencies, useBudgetHistory, useBudgetMonth, useCopyPreviousBudget, useDeleteBudget, useSaveBudget } from "./api";

export function BudgetsPage() {
  const user = useCurrentUser();
  const [params, setParams] = useSearchParams();
  const month = /^\d{4}-\d{2}$/.test(params.get("month") ?? "") ? params.get("month")! : currentMonth();
  const currency = params.get("currency") ?? user.defaultCurrency;
  const query = useBudgetMonth(month, currency);
  const currencies = useBudgetCurrencies();
  const history = useBudgetHistory(currency, 12);
  const copy = useCopyPreviousBudget();
  const del = useDeleteBudget();
  const toast = useToast();
  const f = useFormatters();
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const set = (patch: Record<string, string>) => {
    const p = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => p.set(k, v));
    setParams(p, { replace: true });
  };

  const b = query.data;
  return (
    <>
      <PageHeader
        title="Budgets"
        description="Monthly spending limits, measured against the expenses you've recorded in that calendar month."
        actions={<CurrencyControl value={currency} options={currencies.data ?? [currency]} onChange={(c) => set({ currency: c })} />}
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <IconButton label="Previous month" onClick={() => set({ month: shiftMonth(month, -1) })}>
          <ChevronLeft className="size-4" />
        </IconButton>
        <h2 className="min-w-40 text-center text-base font-semibold text-ink" aria-live="polite">
          {f.month(month)}
        </h2>
        <IconButton label="Next month" onClick={() => set({ month: shiftMonth(month, 1) })}>
          <ChevronRight className="size-4" />
        </IconButton>
        {month !== currentMonth() && (
          <Button variant="ghost" size="sm" onClick={() => set({ month: currentMonth() })}>
            This month
          </Button>
        )}
        <div className="ml-auto flex gap-2">
          {b?.planExists && (
            <>
              <Button variant="ghost" icon={<Trash2 className="size-4" />} onClick={() => setConfirmDelete(true)} className="hidden sm:inline-flex">
                Delete
              </Button>
              <Button icon={<Pencil className="size-4" />} onClick={() => setEditing(true)}>
                Edit budget
              </Button>
            </>
          )}
        </div>
      </div>

      {query.isPending ? (
        <PageLoader />
      ) : query.isError ? (
        <Card>
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        </Card>
      ) : !b!.planExists ? (
        <Card>
          <EmptyState
            icon={<Gauge className="size-5" />}
            title={`No budget for ${f.month(month)}`}
            description={
              <>
                You've recorded <span className="tabular font-medium text-body">{f.money(b!.total.spent, currency)}</span> of expenses in {currency} this month. Set an overall limit, category limits, or both.
              </>
            }
            action={
              <>
                <Button icon={<Plus className="size-4" />} onClick={() => setEditing(true)}>
                  Create budget
                </Button>
                <Button
                  variant="secondary"
                  icon={<Copy className="size-4" />}
                  loading={copy.isPending}
                  onClick={async () => {
                    try {
                      await copy.mutateAsync({ month, currency });
                      toast.success(`Copied limits from ${f.month(shiftMonth(month, -1))}.`);
                    } catch (e) {
                      toast.error(e instanceof ApiError && e.status === 404 ? `There's no ${currency} budget for ${f.month(shiftMonth(month, -1))} to copy.` : errorMessage(e));
                    }
                  }}
                >
                  Copy last month
                </Button>
              </>
            }
          />
        </Card>
      ) : (
        <BudgetDetail budget={b!} />
      )}

      <Card className="mt-5">
        <CardHeader title="Budget history" subtitle={`Last 12 months in ${currency}`} />
        {history.data && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted">
                <tr>
                  <th scope="col" className="px-5 py-2 font-medium">Month</th>
                  <th scope="col" className="px-5 py-2 text-right font-medium">Spent</th>
                  <th scope="col" className="px-5 py-2 text-right font-medium">Overall limit</th>
                  <th scope="col" className="px-5 py-2 text-right font-medium">Used</th>
                  <th scope="col" className="px-5 py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {history.data.map((h) => (
                  <tr key={h.month} className={cn(h.month === month && "bg-primary-soft/40")}>
                    <td className="px-5 py-2.5">
                      <button type="button" className="font-medium text-ink hover:underline" onClick={() => set({ month: h.month })}>
                        {f.month(h.month)}
                      </button>
                    </td>
                    <td className="tabular px-5 py-2.5 text-right">{f.money(h.spent, currency)}</td>
                    <td className="tabular px-5 py-2.5 text-right text-muted">{h.totalLimit ? f.money(h.totalLimit, currency) : "—"}</td>
                    <td className="tabular px-5 py-2.5 text-right">{h.percentUsed ? f.percent(h.percentUsed) : "—"}</td>
                    <td className="px-5 py-2.5">
                      {h.lineCount === 0 && h.status === "NO_LIMIT" ? (
                        <span className="text-xs text-subtle">No budget</span>
                      ) : (
                        <span className="inline-flex items-center gap-2">
                          <StatusPill status={h.status} />
                          {h.exceededLines > 0 && <span className="text-xs text-muted">{h.exceededLines} categor{h.exceededLines === 1 ? "y" : "ies"} over</span>}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {editing && <BudgetEditor month={month} currency={currency} existing={b?.planExists ? b : undefined} onClose={() => setEditing(false)} />}
      <ConfirmDialog
        open={confirmDelete}
        title={`Delete the ${f.month(month)} budget?`}
        message={<p>Your transactions are not affected — only the limits for this month are removed.</p>}
        confirmLabel="Delete budget"
        loading={del.isPending}
        onClose={() => setConfirmDelete(false)}
        onConfirm={async () => {
          try {
            await del.mutateAsync({ month, currency });
            toast.success("Budget deleted.");
            setConfirmDelete(false);
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </>
  );
}

function BudgetDetail({ budget: b }: { budget: BudgetMonth }) {
  const f = useFormatters();
  const c = b.currency;
  return (
    <div className="grid gap-5 lg:grid-cols-3">
      <Card>
        <CardHeader title="Overall" subtitle="All expenses this month, counted once" action={b.sample ? <Badge tone="info">Sample</Badge> : undefined} />
        <CardBody className="space-y-4">
          {b.total.limit ? (
            <>
              <div>
                <p className="tabular text-3xl font-semibold tracking-tight text-ink">{f.percent(b.total.percentUsed)}</p>
                <p className="text-sm text-muted">
                  {f.money(b.total.spent, c)} of {f.money(b.total.limit, c)}
                </p>
              </div>
              <ProgressBar barPercent={b.total.barPercent} percentUsed={b.total.percentUsed} status={b.total.status} label="Overall budget used" className="h-2.5" />
              <StatusPill status={b.total.status} threshold={b.alertThreshold} />
              <dl className="space-y-1.5 text-sm">
                {b.total.status === "EXCEEDED" ? (
                  <Row k="Over by" v={<span className="font-medium text-danger-text">{f.money(b.total.overBy, c)}</span>} />
                ) : (
                  <Row k="Remaining" v={f.money(b.total.remaining, c)} />
                )}
                {b.dailyAllowance && b.daysLeft ? <Row k={`Per day for ${b.daysLeft} days`} v={f.money(b.dailyAllowance, c)} /> : null}
              </dl>
            </>
          ) : (
            <div className="text-sm text-muted">
              <p>No overall limit — only category limits apply.</p>
              <p className="mt-2">
                Total spent this month: <span className="tabular font-medium text-body">{f.money(b.total.spent, c)}</span>
              </p>
            </div>
          )}
          <dl className="space-y-1.5 border-t border-line pt-3 text-sm">
            <Row k="Allocated to categories" v={f.money(b.allocated, c)} />
            <Row k="Spent outside budgeted categories" v={f.money(b.unbudgetedSpent, c)} />
          </dl>
          {b.overAllocated && <InlineAlert tone="warning">Category limits add up to more than the overall limit.</InlineAlert>}
          <p className="text-xs text-subtle">
            Utilisation = spent ÷ limit. Category limits are a breakdown of the overall limit; an expense is never counted twice. Alerts trigger at {b.alertThreshold}% (change in Settings).
          </p>
        </CardBody>
      </Card>

      <Card className="lg:col-span-2">
        <CardHeader title="Category limits" subtitle="Most at-risk first" />
        {b.lines.length === 0 ? (
          <EmptyState compact title="No category limits" description="Edit the budget to add limits for specific categories." />
        ) : (
          <ul className="divide-y divide-line border-t border-line">
            {b.lines.map((l) => (
              <li key={l.category.id} className="px-5 py-3.5">
                <div className="flex items-center gap-3">
                  <CategoryIcon icon={l.category.icon} type="EXPENSE" />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                      <p className="truncate text-sm font-medium text-ink">{l.category.name}</p>
                      <StatusPill status={l.measure.status} threshold={b.alertThreshold} />
                    </div>
                    <ProgressBar barPercent={l.measure.barPercent} percentUsed={l.measure.percentUsed} status={l.measure.status} label={`${l.category.name} budget used`} className="mt-2" />
                    <div className="tabular mt-1.5 flex flex-wrap justify-between gap-x-3 text-xs text-muted">
                      <span>
                        {f.money(l.measure.spent, c)} of {f.money(l.measure.limit, c)} · {f.percent(l.measure.percentUsed)}
                      </span>
                      <span className={cn(l.measure.status === "EXCEEDED" && "font-medium text-danger-text")}>
                        {l.measure.status === "EXCEEDED" ? `Over by ${f.money(l.measure.overBy, c)}` : `${f.money(l.measure.remaining, c)} left`}
                      </span>
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{k}</dt>
      <dd className="tabular text-right text-body">{v}</dd>
    </div>
  );
}

interface LineDraft {
  key: number;
  categoryId: string;
  limit: string;
}

function BudgetEditor({ month, currency, existing, onClose }: { month: string; currency: string; existing?: BudgetMonth; onClose: () => void }) {
  const f = useFormatters();
  const user = useCurrentUser();
  const categories = useCategories();
  const save = useSaveBudget();
  const toast = useToast();
  const formId = useId();
  const [total, setTotal] = useState(existing?.total.limit ?? "");
  const [lines, setLines] = useState<LineDraft[]>(() => (existing?.lines ?? []).map((l, i) => ({ key: i, categoryId: l.category.id, limit: l.measure.limit ?? "" })));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [nextKey, setNextKey] = useState(1000);
  const amount = amountSchema(() => currency, "A limit");
  const expenseCategories = (categories.data ?? []).filter((c) => c.type === "EXPENSE");

  useEffect(() => {
    if (!existing && lines.length === 0 && expenseCategories.length) {
      setLines([{ key: 0, categoryId: "", limit: "" }]);
    }
  }, [existing, lines.length, expenseCategories.length]);

  const submit = async () => {
    const errs: Record<string, string> = {};
    if (total.trim()) {
      const r = amount.safeParse(total);
      if (!r.success) errs.totalLimit = r.error.issues[0]?.message ?? "Invalid amount.";
    }
    const filled = lines.filter((l) => l.categoryId || l.limit.trim());
    const seen = new Set<string>();
    filled.forEach((l, i) => {
      if (!l.categoryId) errs[`lines[${i}].categoryId`] = "Choose a category.";
      else if (seen.has(l.categoryId)) errs[`lines[${i}].categoryId`] = "This category already has a limit.";
      seen.add(l.categoryId);
      const r = amount.safeParse(l.limit);
      if (!r.success) errs[`lines[${i}].limit`] = r.error.issues[0]?.message ?? "Invalid amount.";
    });
    if (!total.trim() && filled.length === 0) errs.totalLimit = "Set an overall limit or at least one category limit.";
    setErrors(errs);
    setFormError(null);
    if (Object.keys(errs).length) return;
    try {
      await save.mutateAsync({
        month,
        input: { currency, totalLimit: total.trim() || null, lines: filled.map((l) => ({ categoryId: l.categoryId, limit: l.limit.trim() })) },
      });
      toast.success(`Budget for ${f.month(month)} saved.`);
      onClose();
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length) {
        setErrors(Object.fromEntries(e.fieldErrors.map((fe) => [fe.field, fe.message])));
      } else {
        setFormError(errorMessage(e));
      }
    }
  };

  // Map the visible row index to the index used in the request (blank rows are dropped).
  const filledIndex = (key: number) => lines.filter((l) => l.categoryId || l.limit.trim()).findIndex((l) => l.key === key);

  return (
    <Dialog
      open
      onClose={onClose}
      size="lg"
      title={`${existing ? "Edit" : "Create"} budget · ${f.month(month)}`}
      description={`Limits in ${currency}. Spending is measured from your recorded expenses in this month.`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={save.isPending}>
            Save budget
          </Button>
        </>
      }
    >
      <form
        id={formId}
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
        className="space-y-5"
      >
        {formError && <InlineAlert>{formError}</InlineAlert>}
        <Input
          label="Overall monthly limit"
          optional
          inputMode="decimal"
          prefix={currencySymbol(currency, user.locale)}
          value={total}
          onChange={(e) => setTotal(e.target.value)}
          error={errors.totalLimit}
          hint="Caps all expenses this month, including categories without their own limit."
          className="tabular"
        />
        <fieldset>
          <legend className="text-sm font-medium text-body">Category limits</legend>
          <p className="mb-3 text-xs text-muted">Optional. Each limit is part of the overall budget, not added on top of it.</p>
          <div className="space-y-2">
            {lines.map((l) => {
              const idx = filledIndex(l.key);
              return (
                <div key={l.key} className="grid grid-cols-[1fr_8rem_auto] items-start gap-2">
                  <Select
                    label="Category"
                    hideLabel
                    value={l.categoryId}
                    error={idx >= 0 ? errors[`lines[${idx}].categoryId`] : undefined}
                    onChange={(e) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, categoryId: e.target.value } : x)))}
                  >
                    <option value="">Choose a category</option>
                    {expenseCategories.map((c) => (
                      <option key={c.id} value={c.id} disabled={c.id !== l.categoryId && lines.some((x) => x.categoryId === c.id)}>
                        {c.name}
                      </option>
                    ))}
                  </Select>
                  <Input
                    label="Limit"
                    hideLabel
                    inputMode="decimal"
                    placeholder="Limit"
                    className="tabular"
                    value={l.limit}
                    error={idx >= 0 ? errors[`lines[${idx}].limit`] : undefined}
                    onChange={(e) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, limit: e.target.value } : x)))}
                  />
                  <IconButton label="Remove this limit" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} className="mt-0.5">
                    <X className="size-4" />
                  </IconButton>
                </div>
              );
            })}
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="mt-2"
            icon={<Plus className="size-4" />}
            disabled={lines.length >= expenseCategories.length}
            onClick={() => {
              setLines((ls) => [...ls, { key: nextKey, categoryId: "", limit: "" }]);
              setNextKey((k) => k + 1);
            }}
          >
            Add category limit
          </Button>
        </fieldset>
      </form>
    </Dialog>
  );
}
