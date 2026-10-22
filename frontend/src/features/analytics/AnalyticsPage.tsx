import { useSearchParams } from "react-router";
import { Lightbulb, Repeat, TrendingUp, TriangleAlert } from "lucide-react";
import { useCurrentUser } from "@/hooks/useAuth";
import { useFormatters } from "@/hooks/useFormatters";
import { Card, CardBody, CardHeader, PageHeader } from "@/components/ui/Card";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { PageLoader } from "@/components/ui/Spinner";
import { ProgressBar, StatusPill } from "@/components/ui/Progress";
import { CategoryIcon } from "@/components/CategoryIcon";
import { CategoryBarChart, MonthlyIncomeExpenseChart, SpendingTrendChart } from "@/components/charts/Charts";
import { CurrencyControl, DateRangeControl, useUrlDateRange } from "@/components/filters";
import { useTransactionCurrencies } from "@/features/transactions/api";
import { useBudgetMonth } from "@/features/budgets/api";
import { cn } from "@/lib/cn";
import type { CategorySlice, Decimal, Overview } from "@/types/api";
import { useMonthly, useOverview, useRecurring } from "./api";

export function AnalyticsPage() {
  const user = useCurrentUser();
  const { range, setRange } = useUrlDateRange();
  const [params, setParams] = useSearchParams();
  const currency = params.get("currency") ?? user.defaultCurrency;
  const overview = useOverview({ ...range, currency });
  const monthly = useMonthly(currency, 12);
  const recurring = useRecurring(currency);
  const currencies = useTransactionCurrencies();
  const budget = useBudgetMonth(range.to.slice(0, 7), currency);
  const f = useFormatters();

  return (
    <>
      <PageHeader
        title="Analytics"
        description={`Spending patterns for ${f.range(range.from, range.to)} in ${currency}, compared with the previous period.`}
        actions={
          <>
            <DateRangeControl value={range} onChange={setRange} />
            <CurrencyControl
              value={currency}
              options={Array.from(new Set([user.defaultCurrency, ...(currencies.data ?? [])]))}
              onChange={(c) => {
                const p = new URLSearchParams(params);
                p.set("currency", c);
                setParams(p, { replace: true });
              }}
            />
          </>
        }
      />
      {overview.isPending ? (
        <PageLoader />
      ) : overview.isError ? (
        <Card>
          <ErrorState error={overview.error} onRetry={() => overview.refetch()} />
        </Card>
      ) : (
        <div className="space-y-5">
          <Comparison o={overview.data} />
          <div className="grid gap-5 lg:grid-cols-2">
            <Card>
              <CardHeader title="Income vs expenses" subtitle="Last 12 calendar months" />
              <CardBody>{monthly.data ? <MonthlyIncomeExpenseChart months={monthly.data} currency={currency} height={260} /> : <PageLoader />}</CardBody>
            </Card>
            <Card>
              <CardHeader title="Spending trend" subtitle={`Selected range, by ${overview.data.granularity.toLowerCase()}`} />
              <CardBody>
                {overview.data.totals.expenseCount ? (
                  <SpendingTrendChart points={overview.data.trend} currency={currency} granularity={overview.data.granularity} />
                ) : (
                  <EmptyState compact title="No expenses in this range" />
                )}
              </CardBody>
            </Card>
          </div>
          <div className="grid gap-5 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader title="Highest-spending categories" subtitle={`Compared with ${f.range(overview.data.previousRange.from, overview.data.previousRange.to)}`} />
              <CardBody>
                {overview.data.expenseCategories.length ? (
                  <>
                    <CategoryBarChart slices={overview.data.expenseCategories} currency={currency} />
                    <CategoryTable slices={overview.data.expenseCategories} currency={currency} />
                  </>
                ) : (
                  <EmptyState compact title="No expenses in this range" />
                )}
              </CardBody>
            </Card>
            <div className="min-w-0 space-y-5">
              <Card>
                <CardHeader title="Insights" subtitle="Deterministic rules, shown only when your data supports them" />
                <CardBody>
                  {overview.data.insights.length === 0 ? (
                    <p className="text-sm text-muted">Not enough data in this range yet.</p>
                  ) : (
                    <ul className="space-y-3">
                      {overview.data.insights.map((i) => {
                        const Icon = i.tone === "WARNING" ? TriangleAlert : i.tone === "POSITIVE" ? TrendingUp : Lightbulb;
                        return (
                          <li key={i.rule + i.message} className="flex gap-2.5 text-sm">
                            <Icon className={cn("mt-0.5 size-4 shrink-0", i.tone === "WARNING" ? "text-warning" : i.tone === "POSITIVE" ? "text-primary" : "text-muted")} aria-hidden="true" />
                            <span className="text-body">{i.message}</span>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </CardBody>
              </Card>
              <Card>
                <CardHeader title={`Budget utilisation · ${f.month(range.to.slice(0, 7))}`} />
                <CardBody>
                  {!budget.data?.planExists ? (
                    <p className="text-sm text-muted">No budget set for this month.</p>
                  ) : (
                    <ul className="space-y-3">
                      {budget.data.total.limit && (
                        <li>
                          <div className="mb-1 flex justify-between text-sm">
                            <span className="font-medium text-ink">Overall</span>
                            <span className="tabular text-muted">{f.percent(budget.data.total.percentUsed)}</span>
                          </div>
                          <ProgressBar barPercent={budget.data.total.barPercent} percentUsed={budget.data.total.percentUsed} status={budget.data.total.status} label="Overall" />
                        </li>
                      )}
                      {budget.data.lines.map((l) => (
                        <li key={l.category.id}>
                          <div className="mb-1 flex items-center justify-between gap-2 text-sm">
                            <span className="truncate text-body">{l.category.name}</span>
                            <span className="flex items-center gap-2">
                              <span className="tabular text-muted">{f.percent(l.measure.percentUsed)}</span>
                              <StatusPill status={l.measure.status} />
                            </span>
                          </div>
                          <ProgressBar barPercent={l.measure.barPercent} percentUsed={l.measure.percentUsed} status={l.measure.status} label={l.category.name} />
                        </li>
                      ))}
                    </ul>
                  )}
                </CardBody>
              </Card>
            </div>
          </div>
          <Card>
            <CardHeader title="Regular payments" subtitle="Expenses with the same merchant at a steady interval (3+ times, similar amounts) in the last six months" />
            {recurring.data && recurring.data.length > 0 ? (
              <ul className="divide-y divide-line border-t border-line">
                {recurring.data.map((r) => (
                  <li key={r.label + r.cadence} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 text-sm">
                    <Repeat className="size-4 text-muted" aria-hidden="true" />
                    <span className="min-w-0 flex-1">
                      <span className="block font-medium text-ink">{r.label}</span>
                      <span className="block text-xs text-muted">
                        {r.categoryName} · {r.cadence.toLowerCase()} · {r.occurrences} times since {f.date(r.firstDate)} · next around {f.date(r.nextExpectedDate)}
                      </span>
                    </span>
                    <span className="tabular text-right">
                      <span className="block font-medium text-body">{f.money(r.typicalAmount, currency)}</span>
                      <span className="block text-xs text-muted">≈ {f.money(r.monthlyEquivalent, currency)}/month</span>
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <CardBody>
                <p className="text-sm text-muted">No regular payments detected. A payment shows up here only after it repeats at least three times at a consistent interval.</p>
              </CardBody>
            )}
          </Card>
        </div>
      )}
    </>
  );
}

function ChangeText({ value, previousZero, inverse }: { value?: Decimal; previousZero: boolean; inverse?: boolean }) {
  const f = useFormatters();
  if (value === undefined) return <span className="text-xs text-subtle">{previousZero ? "No baseline: nothing recorded in the previous period" : "—"}</span>;
  const n = Number(value);
  const good = inverse ? n < 0 : n > 0;
  return <span className={cn("text-xs font-medium", n === 0 ? "text-muted" : good ? "text-primary-text" : "text-danger-text")}>{f.percent(value, { signed: true })} vs previous period</span>;
}

function Comparison({ o }: { o: Overview }) {
  const f = useFormatters();
  const c = o.currency;
  const tiles = [
    { label: "Income", now: o.totals.income, before: o.previousTotals.income, change: <ChangeText value={o.comparison.incomeChangePercent} previousZero={o.previousTotals.incomeCount === 0} /> },
    { label: "Expenses", now: o.totals.expenses, before: o.previousTotals.expenses, change: <ChangeText value={o.comparison.expenseChangePercent} previousZero={o.previousTotals.expenseCount === 0} inverse /> },
    { label: "Net (income − expenses)", now: o.totals.net, before: o.previousTotals.net, change: <span className="text-xs text-muted">Not a bank balance</span> },
    { label: "Kept from income", now: null, before: null, change: <span className="text-xs text-muted">{o.savingsRatePercent ? "Net ÷ income" : "Needs recorded income"}</span>, value: o.savingsRatePercent ? f.percent(o.savingsRatePercent) : "—" },
  ];
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {tiles.map((t) => (
        <Card key={t.label} className="p-4">
          <p className="text-xs font-medium text-muted">{t.label}</p>
          <p className="tabular mt-1.5 text-xl font-semibold tracking-tight text-ink">{t.value ?? f.money(t.now, c)}</p>
          {t.before !== null && <p className="tabular text-xs text-muted">Previous: {f.money(t.before, c)}</p>}
          <div className="mt-1">{t.change}</div>
        </Card>
      ))}
    </div>
  );
}

function CategoryTable({ slices, currency }: { slices: CategorySlice[]; currency: string }) {
  const f = useFormatters();
  return (
    <div className="mt-4 overflow-x-auto rounded-lg border border-line">
      <table className="w-full text-sm">
        <thead className="bg-surface-2 text-left text-xs text-muted">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">Category</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Spent</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Share</th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium sm:table-cell">Previous</th>
            <th scope="col" className="px-3 py-2 text-right font-medium">Change</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {slices.map((s) => (
            <tr key={s.categoryId}>
              <td className="px-3 py-2">
                <span className="flex items-center gap-2">
                  <CategoryIcon icon={s.icon} className="size-6 rounded-md" />
                  <span className="truncate">{s.name}</span>
                </span>
              </td>
              <td className="tabular px-3 py-2 text-right">{f.money(s.total, currency)}</td>
              <td className="tabular px-3 py-2 text-right text-muted">{f.percent(s.sharePercent)}</td>
              <td className="tabular hidden px-3 py-2 text-right text-muted sm:table-cell">{f.money(s.previousTotal, currency)}</td>
              <td className="tabular px-3 py-2 text-right">
                {s.changePercent === undefined ? <span className="text-xs text-subtle" title="Nothing was spent in this category in the previous period">new</span> : <span className={Number(s.changePercent) > 0 ? "text-danger-text" : Number(s.changePercent) < 0 ? "text-primary-text" : "text-muted"}>{f.percent(s.changePercent, { signed: true })}</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
