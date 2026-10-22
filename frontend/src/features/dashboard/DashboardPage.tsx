import { Link, useSearchParams } from "react-router";
import { ArrowDownRight, ArrowUpRight, FlaskConical, Gauge, Lightbulb, Minus, PiggyBank, Plus, Receipt, TriangleAlert, TrendingUp } from "lucide-react";
import { useCurrentUser } from "@/hooks/useAuth";
import { useFormatters } from "@/hooks/useFormatters";
import { useDashboard, useSampleData } from "@/features/analytics/api";
import { useTransactionDialogs } from "@/features/transactions/TransactionDialogs";
import { Card, CardBody, CardHeader, PageHeader, Badge } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { Skeleton } from "@/components/ui/Spinner";
import { GoalBar, ProgressBar, StatusPill } from "@/components/ui/Progress";
import { useToast } from "@/components/ui/Toast";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Money } from "@/components/Money";
import { CategoryBarChart, MonthlyIncomeExpenseChart, SpendingTrendChart } from "@/components/charts/Charts";
import { CurrencyControl, DateRangeControl, useUrlDateRange } from "@/components/filters";
import { errorMessage } from "@/lib/api";
import { cn } from "@/lib/cn";
import type { Dashboard, Decimal, Insight } from "@/types/api";

export function DashboardPage() {
  const user = useCurrentUser();
  const { range, setRange } = useUrlDateRange();
  const [params, setParams] = useSearchParams();
  const currency = params.get("currency") ?? user.defaultCurrency;
  const query = useDashboard({ from: range.from, to: range.to, currency });
  const f = useFormatters();

  const setCurrency = (c: string) => {
    const p = new URLSearchParams(params);
    p.set("currency", c);
    setParams(p, { replace: true });
  };

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={<>Figures for {f.range(range.from, range.to)} in {currency}, calculated from your recorded transactions.</>}
        actions={
          <>
            <DateRangeControl value={range} onChange={setRange} />
            <CurrencyControl value={currency} options={query.data?.currencies ?? [currency]} onChange={setCurrency} />
          </>
        }
      />
      {query.isPending ? (
        <DashboardSkeleton />
      ) : query.isError ? (
        <Card>
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        </Card>
      ) : !query.data.hasAnyTransactions ? (
        <FirstRun />
      ) : (
        <DashboardContent data={query.data} />
      )}
    </>
  );
}

function FirstRun() {
  const dialogs = useTransactionDialogs();
  const { load } = useSampleData();
  const toast = useToast();
  return (
    <Card className="overflow-hidden">
      <div className="grid gap-0 md:grid-cols-[1.2fr_1fr]">
        <div className="p-6 sm:p-8">
          <Badge tone="positive">Getting started</Badge>
          <h2 className="mt-3 text-xl font-semibold tracking-tight text-ink">Start by recording what you spend</h2>
          <p className="mt-2 max-w-md text-sm text-muted">
            Your dashboard fills in from real transactions: totals, category breakdowns, budget progress and insights all update the moment you add one.
          </p>
          <ol className="mt-6 space-y-3 text-sm">
            {[
              ["Add a recent expense or your income", Receipt],
              ["Set a monthly budget for the categories you want to control", Gauge],
              ["Create a savings goal and record what you put aside", PiggyBank],
            ].map(([text, Icon], i) => {
              const I = Icon as typeof Receipt;
              return (
                <li key={i} className="flex items-center gap-3">
                  <span className="flex size-7 items-center justify-center rounded-full bg-sunken text-muted">
                    <I className="size-3.5" aria-hidden="true" />
                  </span>
                  {text as string}
                </li>
              );
            })}
          </ol>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button icon={<Plus className="size-4" />} onClick={() => dialogs.create()}>
              Add your first transaction
            </Button>
            <Link to="/budgets" className="inline-flex h-10 items-center rounded-lg border border-line-strong px-4 text-sm font-medium hover:bg-surface-2">
              Set a budget
            </Link>
          </div>
        </div>
        <div className="border-t border-line bg-surface-2 p-6 sm:p-8 md:border-l md:border-t-0">
          <FlaskConical className="size-5 text-muted" aria-hidden="true" />
          <h3 className="mt-3 text-sm font-semibold text-ink">Prefer to look around first?</h3>
          <p className="mt-1 text-sm text-muted">
            Load three months of <strong className="font-medium text-body">clearly labelled sample data</strong> to see how every screen works. Sample rows are tagged everywhere, and one click removes all of them.
          </p>
          <Button
            variant="secondary"
            className="mt-4"
            loading={load.isPending}
            onClick={async () => {
              try {
                const r = await load.mutateAsync();
                toast.success(`Loaded ${r.transactions} sample transactions.`);
              } catch (e) {
                toast.error(errorMessage(e));
              }
            }}
          >
            Explore with sample data
          </Button>
        </div>
      </div>
    </Card>
  );
}

function Delta({ value, inverse, previousIsZero, label }: { value?: Decimal; inverse?: boolean; previousIsZero: boolean; label: string }) {
  const f = useFormatters();
  if (value === undefined || value === null) {
    return <span className="text-xs text-subtle">{previousIsZero ? `Nothing recorded ${label}` : "No comparison"}</span>;
  }
  const n = Number(value);
  const up = n > 0;
  const good = inverse ? !up : up;
  const Icon = n === 0 ? Minus : up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={cn("inline-flex items-center gap-0.5 text-xs font-medium", n === 0 ? "text-muted" : good ? "text-primary-text" : "text-danger-text")}>
      <Icon className="size-3.5" aria-hidden="true" />
      {f.percent(String(Math.abs(n)))} <span className="font-normal text-muted">{up ? "more" : n < 0 ? "less" : "change"} than {label}</span>
    </span>
  );
}

function Kpi({ label, children, foot, hint }: { label: string; children: React.ReactNode; foot?: React.ReactNode; hint?: string }) {
  return (
    <Card className="p-4">
      <p className="text-xs font-medium text-muted" title={hint}>
        {label}
      </p>
      <div className="mt-1.5 text-2xl font-semibold tracking-tight text-ink">{children}</div>
      {foot && <div className="mt-1.5">{foot}</div>}
    </Card>
  );
}

function DashboardContent({ data }: { data: Dashboard }) {
  const f = useFormatters();
  const dialogs = useTransactionDialogs();
  const o = data.overview;
  const c = o.currency;
  const b = data.budget;
  const prevLabel = o.range.days >= 28 && o.range.from.endsWith("-01") ? "last month" : "previous period";

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi label="Income" foot={<Delta value={o.comparison.incomeChangePercent} previousIsZero={o.previousTotals.incomeCount === 0} label={prevLabel} />}>
          <Money amount={o.totals.income} currency={c} className="text-primary-text" />
        </Kpi>
        <Kpi label="Expenses" foot={<Delta value={o.comparison.expenseChangePercent} inverse previousIsZero={o.previousTotals.expenseCount === 0} label={prevLabel} />}>
          <Money amount={o.totals.expenses} currency={c} />
        </Kpi>
        <Kpi
          label="Net for this period"
          hint="Income minus expenses recorded in this date range. This is not your bank balance."
          foot={<span className="text-xs text-muted">Income − expenses · not a bank balance</span>}
        >
          <span className={cn("tabular", Number(o.totals.net) < 0 && "text-danger-text")}>{f.money(o.totals.net, c, { signed: true })}</span>
        </Kpi>
        <Kpi
          label={`Budget used · ${f.month(b.month, "short")}`}
          foot={b.planExists && b.total.limit ? <ProgressBar barPercent={b.total.barPercent} percentUsed={b.total.percentUsed} status={b.total.status} label="Overall budget used" /> : <Link to="/budgets" className="text-xs font-medium text-primary-text hover:underline">Set a monthly budget</Link>}
        >
          {b.planExists && b.total.limit ? <span className="tabular">{f.percent(b.total.percentUsed)}</span> : <span className="text-base font-medium text-muted">No overall limit</span>}
        </Kpi>
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Spending over time" subtitle={`${o.granularity === "DAY" ? "Daily" : o.granularity === "WEEK" ? "Weekly" : "Monthly"} expenses · average ${f.money(o.averageDailyExpense, c)} per day`} />
          <CardBody>
            {o.totals.expenseCount ? <SpendingTrendChart points={o.trend} currency={c} granularity={o.granularity} /> : <EmptyState compact title="No expenses in this range" description="Pick a different range, or add an expense." />}
          </CardBody>
        </Card>
        <BudgetCard budget={b} />
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Where your money went" subtitle="Expenses by category in this range" action={<Link to={`/analytics?from=${o.range.from}&to=${o.range.to}&currency=${c}`} className="text-xs font-medium text-primary-text hover:underline">Full analysis</Link>} />
          <CardBody>
            {o.expenseCategories.length ? (
              <>
                <CategoryBarChart slices={o.expenseCategories} currency={c} limit={6} />
                <TopCategories data={data} />
              </>
            ) : (
              <EmptyState compact title="No spending to break down yet" />
            )}
          </CardBody>
        </Card>
        <InsightsCard insights={o.insights} />
      </div>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Recent transactions" action={<Link to="/transactions" className="text-xs font-medium text-primary-text hover:underline">View all</Link>} />
          <ul className="divide-y divide-line border-t border-line">
            {data.recentTransactions.length === 0 && <li className="px-5 py-6 text-sm text-muted">No transactions in {c} yet.</li>}
            {data.recentTransactions.map((t) => (
              <li key={t.id}>
                <button type="button" onClick={() => dialogs.view(t)} className="flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-surface-2">
                  <CategoryIcon icon={t.category.icon} type={t.type} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-ink">
                      {t.description}
                      {t.sample && <Badge tone="info" className="ml-2 align-middle">Sample</Badge>}
                    </span>
                    <span className="block truncate text-xs text-muted">
                      {t.category.name} · {f.date(t.transactionDate)}
                    </span>
                  </span>
                  <Money amount={t.amount} currency={t.currency} type={t.type} className="text-sm font-medium" />
                </button>
              </li>
            ))}
          </ul>
        </Card>
        <GoalsCard data={data} />
      </div>

      <Card>
        <CardHeader title="Month over month" subtitle={`Income and expenses for the last ${data.monthly.length} calendar months in ${c}`} />
        <CardBody>
          <MonthlyIncomeExpenseChart months={data.monthly} currency={c} />
        </CardBody>
      </Card>
    </div>
  );
}

function TopCategories({ data }: { data: Dashboard }) {
  const f = useFormatters();
  const top = data.overview.expenseCategories.slice(0, 3);
  return (
    <ul className="mt-4 grid gap-2 sm:grid-cols-3">
      {top.map((s, i) => (
        <li key={s.categoryId} className="rounded-lg border border-line px-3 py-2.5">
          <p className="text-xs text-muted">#{i + 1} · {f.percent(s.sharePercent)} of spending</p>
          <p className="mt-0.5 truncate text-sm font-medium text-ink">{s.name}</p>
          <p className="tabular text-sm text-body">{f.money(s.total, data.overview.currency)}</p>
        </li>
      ))}
    </ul>
  );
}

function BudgetCard({ budget: b }: { budget: Dashboard["budget"] }) {
  const f = useFormatters();
  return (
    <Card>
      <CardHeader title={`Budget · ${f.month(b.month)}`} subtitle="Monthly budgets use calendar months" action={<Link to={`/budgets?month=${b.month}&currency=${b.currency}`} className="text-xs font-medium text-primary-text hover:underline">{b.planExists ? "Manage" : "Create"}</Link>} />
      <CardBody>
        {!b.planExists ? (
          <div className="text-sm text-muted">
            <p>
              You've spent <span className="tabular font-medium text-body">{f.money(b.total.spent, b.currency)}</span> this month with no budget set.
            </p>
            <Link to={`/budgets?month=${b.month}&currency=${b.currency}`} className="mt-3 inline-flex h-9 items-center rounded-lg border border-line-strong px-3 text-sm font-medium text-body hover:bg-surface-2">
              Set a monthly budget
            </Link>
          </div>
        ) : (
          <div className="space-y-4">
            {b.total.limit && (
              <div>
                <div className="mb-1.5 flex items-baseline justify-between text-sm">
                  <span className="font-medium text-ink">Overall</span>
                  <span className="tabular text-muted">
                    {f.money(b.total.spent, b.currency)} of {f.money(b.total.limit, b.currency)}
                  </span>
                </div>
                <ProgressBar barPercent={b.total.barPercent} percentUsed={b.total.percentUsed} status={b.total.status} label="Overall budget" />
                <p className="mt-1.5 text-xs text-muted">
                  {b.total.status === "EXCEEDED" ? (
                    <span className="font-medium text-danger-text">Over by {f.money(b.total.overBy, b.currency)}</span>
                  ) : (
                    <>
                      {f.money(b.total.remaining, b.currency)} left
                      {b.dailyAllowance && b.daysLeft ? <> · about <span className="font-medium text-body">{f.money(b.dailyAllowance, b.currency)}/day</span> for {b.daysLeft} days</> : null}
                    </>
                  )}
                </p>
              </div>
            )}
            <ul className="space-y-3">
              {b.lines.slice(0, 4).map((l) => (
                <li key={l.category.id}>
                  <div className="mb-1 flex items-center justify-between gap-2 text-sm">
                    <span className="truncate text-body">{l.category.name}</span>
                    <StatusPill status={l.measure.status} threshold={b.alertThreshold} />
                  </div>
                  <ProgressBar barPercent={l.measure.barPercent} percentUsed={l.measure.percentUsed} status={l.measure.status} label={`${l.category.name} budget`} />
                  <p className="tabular mt-1 text-xs text-muted">
                    {f.percent(l.measure.percentUsed)} · {f.money(l.measure.spent, b.currency)} of {f.money(l.measure.limit, b.currency)}
                  </p>
                </li>
              ))}
            </ul>
            {b.lines.length > 4 && <p className="text-xs text-muted">+ {b.lines.length - 4} more category limits</p>}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function InsightsCard({ insights }: { insights: Insight[] }) {
  const icon = { POSITIVE: TrendingUp, NEUTRAL: Lightbulb, WARNING: TriangleAlert };
  return (
    <Card>
      <CardHeader title="Insights" subtitle="Plain rules applied to your own records" />
      <CardBody>
        {insights.length === 0 ? (
          <p className="text-sm text-muted">Not enough data in this range for insights yet.</p>
        ) : (
          <ul className="space-y-3">
            {insights.slice(0, 6).map((i) => {
              const Icon = icon[i.tone];
              return (
                <li key={i.rule + i.message} className="flex gap-2.5 text-sm">
                  <Icon className={cn("mt-0.5 size-4 shrink-0", i.tone === "WARNING" ? "text-warning" : i.tone === "POSITIVE" ? "text-primary" : "text-muted")} aria-hidden="true" />
                  <p className="text-body">{i.message}</p>
                </li>
              );
            })}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

function GoalsCard({ data }: { data: Dashboard }) {
  const f = useFormatters();
  return (
    <Card>
      <CardHeader title="Savings goals" subtitle="Amounts you've recorded, not bank-verified" action={<Link to="/goals" className="text-xs font-medium text-primary-text hover:underline">All goals</Link>} />
      <CardBody>
        {data.goals.length === 0 ? (
          <div className="text-sm text-muted">
            <p>No savings goals in {data.overview.currency} yet.</p>
            <Link to="/goals" className="mt-2 inline-block font-medium text-primary-text hover:underline">
              Create a goal
            </Link>
          </div>
        ) : (
          <ul className="space-y-4">
            {data.goals.map((g) => (
              <li key={g.id}>
                <div className="mb-1 flex items-baseline justify-between gap-2 text-sm">
                  <span className="truncate font-medium text-ink">{g.name}</span>
                  <span className="tabular text-xs text-muted">{f.percent(g.progressPercent)}</span>
                </div>
                <GoalBar barPercent={g.barPercent} completed={g.completed} label={`${g.name} progress`} />
                <p className="tabular mt-1 text-xs text-muted">
                  {f.money(g.savedAmount, g.currency)} of {f.money(g.targetAmount, g.currency)}
                  {g.completed ? " · reached" : ` · ${f.money(g.remainingAmount, g.currency)} to go`}
                </p>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-5" aria-busy="true" aria-label="Loading dashboard">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <Card key={i} className="space-y-3 p-4">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-7 w-28" />
            <Skeleton className="h-3 w-24" />
          </Card>
        ))}
      </div>
      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="p-5 lg:col-span-2">
          <Skeleton className="h-56 w-full" />
        </Card>
        <Card className="p-5">
          <Skeleton className="h-56 w-full" />
        </Card>
      </div>
    </div>
  );
}
