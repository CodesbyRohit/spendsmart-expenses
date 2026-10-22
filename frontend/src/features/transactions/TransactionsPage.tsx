import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Download, Pencil, Plus, Receipt, Search, Trash2, X } from "lucide-react";
import { useFormatters } from "@/hooks/useFormatters";
import { useDebounced } from "@/hooks/useDebounced";
import { Card, PageHeader, Badge } from "@/components/ui/Card";
import { Button, IconButton } from "@/components/ui/Button";
import { FilterSelect } from "@/components/ui/Field";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { Skeleton } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Money } from "@/components/Money";
import { useCategories } from "@/features/categories/api";
import { errorMessage } from "@/lib/api";
import { isValidISODate } from "@/lib/dates";
import { cn } from "@/lib/cn";
import type { Transaction, TransactionType } from "@/types/api";
import { exportTransactionsCsv, useTransactionCurrencies, useTransactions, type TransactionFilters } from "./api";
import { useTransactionDialogs } from "./TransactionDialogs";

const PAGE_SIZE = 20;
const SORTS: Record<string, string> = {
  "date,desc": "Newest first",
  "date,asc": "Oldest first",
  "amount,desc": "Largest amount",
  "amount,asc": "Smallest amount",
  "description,asc": "Description A–Z",
};

/** All filters live in the URL so they survive refresh, back/forward and can be shared or bookmarked. */
function useFiltersFromUrl() {
  const [params, setParams] = useSearchParams();
  const get = (k: string) => params.get(k) ?? "";
  const filters: TransactionFilters = {
    q: get("q"),
    type: (get("type") as TransactionType | "") || "",
    categoryId: get("categoryId"),
    currency: get("currency"),
    from: isValidISODate(get("from")) ? get("from") : "",
    to: isValidISODate(get("to")) ? get("to") : "",
    sort: SORTS[get("sort")] ? get("sort") : "date,desc",
    page: Math.max(0, Number(get("page")) || 0),
    size: PAGE_SIZE,
  };
  const update = (patch: Partial<Record<keyof TransactionFilters, string | number>>) => {
    const p = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === "" || v === undefined || v === null || (k === "page" && v === 0)) p.delete(k);
      else p.set(k, String(v));
    }
    if (!("page" in patch)) p.delete("page");
    setParams(p, { replace: true });
  };
  const active = Boolean(filters.q || filters.type || filters.categoryId || filters.currency || filters.from || filters.to);
  return { filters, update, active, clear: () => setParams(new URLSearchParams(), { replace: true }) };
}

export function TransactionsPage() {
  const { filters, update, active, clear } = useFiltersFromUrl();
  const [search, setSearch] = useState(filters.q ?? "");
  const debounced = useDebounced(search, 350);
  useEffect(() => {
    // Only the debounced text should trigger a URL update; `filters`/`update` change on every render.
    if (debounced !== (filters.q ?? "")) update({ q: debounced });
  }, [debounced]); // eslint-disable-line react-hooks/exhaustive-deps

  const query = useTransactions(filters);
  const categories = useCategories(true);
  const currencies = useTransactionCurrencies();
  const dialogs = useTransactionDialogs();
  const toast = useToast();
  const [exporting, setExporting] = useState(false);

  const sortKey = filters.sort ?? "date,desc";
  const toggleSort = (field: "date" | "amount" | "description") => {
    const [f, dir] = sortKey.split(",");
    update({ sort: f === field ? `${field},${dir === "asc" ? "desc" : "asc"}` : `${field},${field === "description" ? "asc" : "desc"}` });
  };

  const categoryOptions = (categories.data ?? []).filter((c) => !filters.type || c.type === filters.type);

  return (
    <>
      <PageHeader
        title="Transactions"
        description="Every income and expense you've recorded. Filters apply to the list and to CSV export."
        actions={
          <>
            <Button
              variant="secondary"
              icon={<Download className="size-4" />}
              loading={exporting}
              onClick={async () => {
                setExporting(true);
                try {
                  await exportTransactionsCsv(filters);
                } catch (e) {
                  toast.error(errorMessage(e));
                } finally {
                  setExporting(false);
                }
              }}
            >
              Export CSV
            </Button>
            <Button icon={<Plus className="size-4" />} onClick={() => dialogs.create()}>
              Add transaction
            </Button>
          </>
        }
      />

      <Card>
        <div className="flex flex-wrap items-center gap-2 border-b border-line p-3">
          <div className="relative min-w-[12rem] flex-1">
            <label htmlFor="tx-search" className="sr-only">
              Search by description or merchant
            </label>
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-subtle" aria-hidden="true" />
            <input
              id="tx-search"
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search description or merchant"
              maxLength={100}
              className="h-9 w-full rounded-lg border border-line-strong bg-surface pl-9 pr-3 text-sm focus:border-focus focus:outline-none focus:ring-2 focus:ring-focus/25"
            />
          </div>
          <FilterSelect label="Type" value={filters.type} onChange={(e) => update({ type: e.target.value, categoryId: "" })}>
            <option value="">All types</option>
            <option value="EXPENSE">Expenses</option>
            <option value="INCOME">Income</option>
          </FilterSelect>
          <FilterSelect label="Category" value={filters.categoryId} onChange={(e) => update({ categoryId: e.target.value })} className="max-w-[12rem]">
            <option value="">All categories</option>
            {categoryOptions.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
                {filters.type ? "" : c.type === "INCOME" ? " (income)" : ""}
              </option>
            ))}
          </FilterSelect>
          {(currencies.data?.length ?? 0) > 1 && (
            <FilterSelect label="Currency" value={filters.currency} onChange={(e) => update({ currency: e.target.value })}>
              <option value="">All currencies</option>
              {currencies.data!.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </FilterSelect>
          )}
          <div className="flex items-center gap-1.5">
            <label htmlFor="tx-from" className="sr-only">
              From date
            </label>
            <input id="tx-from" type="date" value={filters.from} max={filters.to || undefined} onChange={(e) => update({ from: e.target.value })} className="h-9 rounded-lg border border-line-strong bg-surface px-2 text-sm" />
            <span className="text-subtle" aria-hidden="true">–</span>
            <label htmlFor="tx-to" className="sr-only">
              To date
            </label>
            <input id="tx-to" type="date" value={filters.to} min={filters.from || undefined} onChange={(e) => update({ to: e.target.value })} className="h-9 rounded-lg border border-line-strong bg-surface px-2 text-sm" />
          </div>
          <FilterSelect label="Sort" value={sortKey} onChange={(e) => update({ sort: e.target.value })} className="md:hidden">
            {Object.entries(SORTS).map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </FilterSelect>
          {active && (
            <Button
              variant="ghost"
              size="sm"
              icon={<X className="size-3.5" />}
              onClick={() => {
                setSearch("");
                clear();
              }}
            >
              Clear filters
            </Button>
          )}
        </div>

        {query.isPending ? (
          <ListSkeleton />
        ) : query.isError ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : query.data.items.length === 0 ? (
          active ? (
            <EmptyState icon={<Search className="size-5" />} title="No transactions match these filters" description="Try a wider date range or clear the filters." />
          ) : (
            <EmptyState
              icon={<Receipt className="size-5" />}
              title="No transactions yet"
              description="Record an expense or income and it will appear here, in your budgets and on the dashboard."
              action={
                <Button icon={<Plus className="size-4" />} onClick={() => dialogs.create()}>
                  Add transaction
                </Button>
              }
            />
          )
        ) : (
          <>
            <TransactionTable items={query.data.items} sortKey={sortKey} onSort={toggleSort} fetching={query.isFetching} />
            <TransactionCards items={query.data.items} />
            <Pagination page={query.data.page} totalPages={query.data.totalPages} totalItems={query.data.totalItems} size={query.data.size} onPage={(p) => update({ page: p })} />
          </>
        )}
      </Card>
    </>
  );
}

function SortHeader({ label, field, sortKey, onSort, className }: { label: string; field: "date" | "amount" | "description"; sortKey: string; onSort: (f: "date" | "amount" | "description") => void; className?: string }) {
  const [f, dir] = sortKey.split(",");
  const active = f === field;
  return (
    <th scope="col" aria-sort={active ? (dir === "asc" ? "ascending" : "descending") : "none"} className={cn("px-4 py-2.5 font-medium", className)}>
      <button type="button" onClick={() => onSort(field)} className={cn("inline-flex items-center gap-1 hover:text-body", active && "text-body")}>
        {label}
        {active && (dir === "asc" ? <ArrowUp className="size-3" aria-hidden="true" /> : <ArrowDown className="size-3" aria-hidden="true" />)}
      </button>
    </th>
  );
}

function TransactionTable({ items, sortKey, onSort, fetching }: { items: Transaction[]; sortKey: string; onSort: (f: "date" | "amount" | "description") => void; fetching: boolean }) {
  const f = useFormatters();
  const dialogs = useTransactionDialogs();
  return (
    <div className={cn("hidden overflow-x-auto md:block", fetching && "opacity-70 transition-opacity")}>
      <table className="w-full text-sm">
        <caption className="sr-only">Transactions</caption>
        <thead className="bg-surface-2 text-left text-xs text-muted">
          <tr>
            <SortHeader label="Date" field="date" sortKey={sortKey} onSort={onSort} />
            <SortHeader label="Description" field="description" sortKey={sortKey} onSort={onSort} />
            <th scope="col" className="px-4 py-2.5 font-medium">Category</th>
            <th scope="col" className="px-4 py-2.5 font-medium">Merchant</th>
            <SortHeader label="Amount" field="amount" sortKey={sortKey} onSort={onSort} className="text-right" />
            <th scope="col" className="w-24 px-4 py-2.5"><span className="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {items.map((t) => (
            <tr key={t.id} className="group hover:bg-surface-2">
              <td className="tabular whitespace-nowrap px-4 py-3 text-muted">{f.date(t.transactionDate)}</td>
              <td className="max-w-xs px-4 py-3">
                <button type="button" onClick={() => dialogs.view(t)} className="block max-w-full truncate text-left font-medium text-ink hover:underline">
                  {t.description}
                </button>
                {t.sample && <Badge tone="info" className="mt-0.5">Sample</Badge>}
              </td>
              <td className="px-4 py-3">
                <span className="inline-flex items-center gap-2 text-body">
                  <CategoryIcon icon={t.category.icon} type={t.type} className="size-6 rounded-md" />
                  {t.category.name}
                </span>
              </td>
              <td className="max-w-[10rem] truncate px-4 py-3 text-muted">{t.merchant ?? "—"}</td>
              <td className="px-4 py-3 text-right font-medium">
                <Money amount={t.amount} currency={t.currency} type={t.type} />
              </td>
              <td className="px-4 py-3">
                <div className="flex justify-end gap-0.5 opacity-100 md:opacity-0 md:group-focus-within:opacity-100 md:group-hover:opacity-100">
                  <IconButton label={`Edit ${t.description}`} onClick={() => dialogs.edit(t)} className="size-8">
                    <Pencil className="size-3.5" />
                  </IconButton>
                  <IconButton label={`Delete ${t.description}`} onClick={() => dialogs.remove(t)} className="size-8 hover:text-danger-text">
                    <Trash2 className="size-3.5" />
                  </IconButton>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Narrow screens: a stacked list instead of a squeezed table. */
function TransactionCards({ items }: { items: Transaction[] }) {
  const f = useFormatters();
  const dialogs = useTransactionDialogs();
  return (
    <ul className="divide-y divide-line md:hidden">
      {items.map((t) => (
        <li key={t.id}>
          <button type="button" onClick={() => dialogs.view(t)} className="flex w-full items-center gap-3 px-4 py-3 text-left active:bg-surface-2">
            <CategoryIcon icon={t.category.icon} type={t.type} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium text-ink">{t.description}</span>
              <span className="block truncate text-xs text-muted">
                {f.date(t.transactionDate)} · {t.category.name}
                {t.merchant ? ` · ${t.merchant}` : ""}
              </span>
              {t.sample && <Badge tone="info" className="mt-1">Sample</Badge>}
            </span>
            <Money amount={t.amount} currency={t.currency} type={t.type} className="text-sm font-medium" />
          </button>
        </li>
      ))}
    </ul>
  );
}

export function Pagination({ page, totalPages, totalItems, size, onPage }: { page: number; totalPages: number; totalItems: number; size: number; onPage: (p: number) => void }) {
  const first = page * size + 1;
  const last = Math.min(totalItems, (page + 1) * size);
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-muted">
      <p className="tabular">
        {first}–{last} of {totalItems}
      </p>
      <div className="flex items-center gap-1">
        <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)} icon={<ChevronLeft className="size-4" />}>
          <span className="sr-only sm:not-sr-only">Previous</span>
        </Button>
        <span className="tabular px-2">
          Page {page + 1} of {Math.max(1, totalPages)}
        </span>
        <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          <span className="sr-only sm:not-sr-only">Next</span>
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </nav>
  );
}

function ListSkeleton() {
  return (
    <div className="space-y-3 p-4" aria-busy="true" aria-label="Loading transactions">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="size-8 rounded-lg" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-3.5 w-1/3" />
            <Skeleton className="h-3 w-1/4" />
          </div>
          <Skeleton className="h-4 w-20" />
        </div>
      ))}
    </div>
  );
}
