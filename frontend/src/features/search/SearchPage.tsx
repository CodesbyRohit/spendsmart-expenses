import { Link, useSearchParams } from "react-router";
import { PiggyBank, Search, Tags } from "lucide-react";
import { useFormatters } from "@/hooks/useFormatters";
import { Card, CardHeader, PageHeader } from "@/components/ui/Card";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { PageLoader } from "@/components/ui/Spinner";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Money } from "@/components/Money";
import { useSearch } from "@/features/analytics/api";
import { useTransactionDialogs } from "@/features/transactions/TransactionDialogs";

export function SearchPage() {
  const [params] = useSearchParams();
  const q = (params.get("q") ?? "").trim();
  const results = useSearch(q);
  const dialogs = useTransactionDialogs();
  const f = useFormatters();

  if (q.length < 2) {
    return (
      <>
        <PageHeader title="Search" />
        <Card>
          <EmptyState icon={<Search className="size-5" />} title="Type at least two characters" description="Search looks through transaction descriptions and merchants, category names and goal names." />
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader title={`Results for “${q}”`} />
      {results.isPending ? (
        <PageLoader />
      ) : results.isError ? (
        <Card>
          <ErrorState error={results.error} onRetry={() => results.refetch()} />
        </Card>
      ) : results.data.transactions.length + results.data.categories.length + results.data.goals.length === 0 ? (
        <Card>
          <EmptyState icon={<Search className="size-5" />} title="Nothing matched" description="Try a shorter word or check the spelling." />
        </Card>
      ) : (
        <div className="grid gap-5 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader
              title="Transactions"
              subtitle={`${results.data.transactionTotal} match${results.data.transactionTotal === 1 ? "" : "es"}`}
              action={results.data.transactionTotal > results.data.transactions.length ? <Link className="text-xs font-medium text-primary-text hover:underline" to={`/transactions?q=${encodeURIComponent(q)}`}>See all</Link> : undefined}
            />
            <ul className="divide-y divide-line border-t border-line">
              {results.data.transactions.length === 0 && <li className="px-5 py-4 text-sm text-muted">No matching transactions.</li>}
              {results.data.transactions.map((t) => (
                <li key={t.id}>
                  <button type="button" onClick={() => dialogs.view(t)} className="flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-surface-2">
                    <CategoryIcon icon={t.category.icon} type={t.type} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-ink">{t.description}</span>
                      <span className="block truncate text-xs text-muted">
                        {f.date(t.transactionDate)} · {t.category.name}
                        {t.merchant ? ` · ${t.merchant}` : ""}
                      </span>
                    </span>
                    <Money amount={t.amount} currency={t.currency} type={t.type} className="text-sm font-medium" />
                  </button>
                </li>
              ))}
            </ul>
          </Card>
          <div className="space-y-5">
            <Card>
              <CardHeader title="Categories" />
              <ul className="divide-y divide-line border-t border-line">
                {results.data.categories.length === 0 && <li className="px-5 py-4 text-sm text-muted">No matching categories.</li>}
                {results.data.categories.map((c) => (
                  <li key={c.id}>
                    <Link to={`/transactions?categoryId=${c.id}`} className="flex items-center gap-3 px-5 py-2.5 text-sm hover:bg-surface-2">
                      <Tags className="size-4 text-muted" aria-hidden="true" />
                      {c.name}
                      <span className="ml-auto text-xs text-muted">{c.type === "INCOME" ? "Income" : "Expense"}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
            <Card>
              <CardHeader title="Savings goals" />
              <ul className="divide-y divide-line border-t border-line">
                {results.data.goals.length === 0 && <li className="px-5 py-4 text-sm text-muted">No matching goals.</li>}
                {results.data.goals.map((g) => (
                  <li key={g.id}>
                    <Link to="/goals" className="flex items-center gap-3 px-5 py-2.5 text-sm hover:bg-surface-2">
                      <PiggyBank className="size-4 text-muted" aria-hidden="true" />
                      {g.name}
                      <span className="ml-auto text-xs text-muted">{g.currency}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
