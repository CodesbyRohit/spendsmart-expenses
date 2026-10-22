import { useState, type ReactNode } from "react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Table2, ChartColumn } from "lucide-react";
import { useFormatters } from "@/hooks/useFormatters";
import { IconButton } from "@/components/ui/Button";
import type { CategorySlice, MonthPoint, TrendPoint } from "@/types/api";

/*
 * Chart colours come from CSS variables (validated pair: income vs expense; single navy hue for categories).
 * Identity never relies on colour alone: two-series charts have a legend, a 2px gap between bars and a table view.
 */
const INCOME = "var(--chart-income)";
const EXPENSE = "var(--chart-expense)";
const CATEGORY = "var(--chart-category)";
const GRID = "var(--chart-grid)";
const AXIS = "var(--chart-axis)";

const axisProps = {
  stroke: AXIS,
  tick: { fill: AXIS, fontSize: 11 },
  tickLine: false,
  axisLine: false,
} as const;

function TooltipBox({ title, rows }: { title: string; rows: { label: string; value: string; color?: string }[] }) {
  return (
    <div className="rounded-lg border border-line bg-surface px-3 py-2 text-xs shadow-pop">
      <p className="mb-1 font-medium text-ink">{title}</p>
      {rows.map((r) => (
        <p key={r.label} className="flex items-center gap-2 text-body">
          {r.color && <span className="size-2 rounded-sm" style={{ background: r.color }} aria-hidden="true" />}
          <span className="text-muted">{r.label}</span>
          <span className="tabular ml-auto pl-4 font-medium">{r.value}</span>
        </p>
      ))}
    </div>
  );
}

/** Wraps a chart with a chart/table toggle so every chart has an accessible, exact-number alternative. */
export function ChartFrame({ label, legend, table, children, height = 240 }: { label: string; legend?: ReactNode; table: ReactNode; children: ReactNode; height?: number }) {
  const [asTable, setAsTable] = useState(false);
  return (
    <figure aria-label={label} className="m-0">
      <div className="mb-2 flex min-h-8 items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-3 text-xs text-muted">{legend}</div>
        <IconButton label={asTable ? "Show chart" : "Show as table"} onClick={() => setAsTable((v) => !v)} className="size-8">
          {asTable ? <ChartColumn className="size-4" /> : <Table2 className="size-4" />}
        </IconButton>
      </div>
      {asTable ? (
        <div className="max-h-80 overflow-auto rounded-lg border border-line">{table}</div>
      ) : (
        <div style={{ height }} aria-hidden="true">
          {children}
        </div>
      )}
    </figure>
  );
}

export function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="size-2.5 rounded-sm" style={{ background: color }} aria-hidden="true" />
      {label}
    </span>
  );
}

function SimpleTable({ head, rows }: { head: string[]; rows: (string | number)[][] }) {
  return (
    <table className="w-full text-sm">
      <thead className="sticky top-0 bg-surface-2 text-left text-xs text-muted">
        <tr>
          {head.map((h, i) => (
            <th key={h} scope="col" className={`px-3 py-2 font-medium ${i > 0 ? "text-right" : ""}`}>
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="divide-y divide-line">
        {rows.map((r, i) => (
          <tr key={i}>
            {r.map((c, j) => (
              <td key={j} className={`px-3 py-1.5 ${j > 0 ? "tabular text-right" : "text-body"}`}>
                {c}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Spending over time — a single series, so no legend is needed; the card title names it. */
export function SpendingTrendChart({ points, currency, granularity }: { points: TrendPoint[]; currency: string; granularity: "DAY" | "WEEK" | "MONTH" }) {
  const f = useFormatters();
  const data = points.map((p) => ({ ...p, value: Number(p.expenses) }));
  const label = (p: TrendPoint) => (granularity === "DAY" ? f.date(p.start, "short") : granularity === "MONTH" ? f.month(p.start.slice(0, 7), "short") : f.range(p.start, p.end));
  return (
    <ChartFrame
      label="Spending over time"
      table={<SimpleTable head={[granularity === "DAY" ? "Day" : "Period", "Expenses"]} rows={points.map((p) => [label(p), f.money(p.expenses, currency)])} />}
    >
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="spendFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={EXPENSE} stopOpacity={0.22} />
              <stop offset="100%" stopColor={EXPENSE} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="start" {...axisProps} minTickGap={24} tickFormatter={(v: string) => (granularity === "MONTH" ? f.month(v.slice(0, 7), "short") : f.date(v, "short"))} />
          <YAxis {...axisProps} width={56} tickFormatter={(v: number) => f.money(v, currency, { compact: true })} />
          <Tooltip
            cursor={{ stroke: AXIS, strokeDasharray: "3 3" }}
            content={({ active, payload }) => {
              const p = payload?.[0]?.payload as (TrendPoint & { value: number }) | undefined;
              if (!active || !p) return null;
              return <TooltipBox title={label(p)} rows={[{ label: "Spent", value: f.money(p.expenses, currency), color: EXPENSE }]} />;
            }}
          />
          <Area type="monotone" dataKey="value" stroke={EXPENSE} strokeWidth={2} fill="url(#spendFill)" activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--surface)" }} />
        </AreaChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

/** Income vs expenses by calendar month (grouped bars, one y-axis). */
export function MonthlyIncomeExpenseChart({ months, currency, height = 240 }: { months: MonthPoint[]; currency: string; height?: number }) {
  const f = useFormatters();
  const data = months.map((m) => ({ ...m, incomeN: Number(m.income), expensesN: Number(m.expenses) }));
  return (
    <ChartFrame
      label="Income and expenses by month"
      height={height}
      legend={
        <>
          <LegendItem color={INCOME} label="Income" />
          <LegendItem color={EXPENSE} label="Expenses" />
        </>
      }
      table={
        <SimpleTable
          head={["Month", "Income", "Expenses", "Net", "Expense change"]}
          rows={months.map((m) => [f.month(m.month), f.money(m.income, currency), f.money(m.expenses, currency), f.money(m.net, currency, { signed: true }), m.expenseChangePercent ? f.percent(m.expenseChangePercent, { signed: true }) : "n/a"])}
        />
      }
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="28%">
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="month" {...axisProps} tickFormatter={(v: string) => f.month(v, "short").replace(/\s\d{4}$/, "")} />
          <YAxis {...axisProps} width={56} tickFormatter={(v: number) => f.money(v, currency, { compact: true })} />
          <Tooltip
            cursor={{ fill: "var(--surface-sunken)", opacity: 0.6 }}
            content={({ active, payload }) => {
              const m = payload?.[0]?.payload as MonthPoint | undefined;
              if (!active || !m) return null;
              return (
                <TooltipBox
                  title={f.month(m.month)}
                  rows={[
                    { label: "Income", value: f.money(m.income, currency), color: INCOME },
                    { label: "Expenses", value: f.money(m.expenses, currency), color: EXPENSE },
                    { label: "Net", value: f.money(m.net, currency, { signed: true }) },
                  ]}
                />
              );
            }}
          />
          <Bar dataKey="incomeN" name="Income" fill={INCOME} radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Bar dataKey="expensesN" name="Expenses" fill={EXPENSE} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}

/** Ranked horizontal bars in one hue: magnitude, not identity, so no rainbow. */
export function CategoryBarChart({ slices, currency, limit = 8 }: { slices: CategorySlice[]; currency: string; limit?: number }) {
  const f = useFormatters();
  // Only the largest categories are drawn; the rest stay exact in the table view (no client-side money sums).
  const data = slices.slice(0, limit).map((s) => ({ name: s.name, value: Number(s.total), slice: s }));
  const hidden = slices.length - data.length;
  const height = Math.max(120, data.length * 34 + 16);
  return (
    <ChartFrame
      label="Spending by category"
      height={height}
      legend={hidden > 0 ? <span>Top {data.length} of {slices.length} categories · all in table view</span> : undefined}
      table={<SimpleTable head={["Category", "Spent", "Share", "Transactions"]} rows={slices.map((s) => [s.name, f.money(s.total, currency), f.percent(s.sharePercent), s.count])} />}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 12, bottom: 0, left: 0 }} barCategoryGap={8}>
          <XAxis type="number" hide />
          <YAxis type="category" dataKey="name" {...axisProps} width={120} tick={{ fill: "var(--text-muted)", fontSize: 12 }} />
          <Tooltip
            cursor={{ fill: "var(--surface-sunken)", opacity: 0.6 }}
            content={({ active, payload }) => {
              const d = payload?.[0]?.payload as { name: string; slice: CategorySlice } | undefined;
              if (!active || !d) return null;
              return (
                <TooltipBox
                  title={d.name}
                  rows={[
                    { label: "Spent", value: f.money(d.slice.total, currency) },
                    { label: "Share", value: f.percent(d.slice.sharePercent) },
                    { label: "Transactions", value: String(d.slice.count) },
                  ]}
                />
              );
            }}
          />
          <Bar dataKey="value" fill={CATEGORY} radius={[0, 4, 4, 0]} maxBarSize={20} />
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  );
}
