import { createContext, useCallback, useContext, useId, useMemo, useState, type ReactNode } from "react";
import { Pencil, Trash2 } from "lucide-react";
import { Dialog, ConfirmDialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Money } from "@/components/Money";
import { useFormatters } from "@/hooks/useFormatters";
import { errorMessage } from "@/lib/api";
import { PAYMENT_METHOD_LABELS } from "@/lib/format";
import type { Transaction, TransactionType } from "@/types/api";
import { useDeleteTransaction, useSaveTransaction } from "./api";
import { TransactionForm } from "./TransactionForm";

interface TransactionDialogApi {
  create: (defaults?: { type?: TransactionType }) => void;
  edit: (transaction: Transaction) => void;
  view: (transaction: Transaction) => void;
  remove: (transaction: Transaction) => void;
}

const Ctx = createContext<TransactionDialogApi | null>(null);

type State =
  | { kind: "closed" }
  | { kind: "create"; defaults?: { type?: TransactionType } }
  | { kind: "edit"; transaction: Transaction }
  | { kind: "view"; transaction: Transaction }
  | { kind: "delete"; transaction: Transaction };

/** One place owns add / edit / view / delete so every screen (and the global "Add" button) shares the same flow. */
export function TransactionDialogProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ kind: "closed" });
  const close = useCallback(() => setState({ kind: "closed" }), []);
  const save = useSaveTransaction();
  const del = useDeleteTransaction();
  const toast = useToast();
  const formId = useId();
  const f = useFormatters();

  const api = useMemo<TransactionDialogApi>(
    () => ({
      create: (defaults) => setState({ kind: "create", defaults }),
      edit: (transaction) => setState({ kind: "edit", transaction }),
      view: (transaction) => setState({ kind: "view", transaction }),
      remove: (transaction) => setState({ kind: "delete", transaction }),
    }),
    [],
  );

  const editing = state.kind === "edit" ? state.transaction : undefined;
  const formOpen = state.kind === "create" || state.kind === "edit";

  return (
    <Ctx.Provider value={api}>
      {children}

      <Dialog
        open={formOpen}
        onClose={close}
        title={editing ? "Edit transaction" : "Add transaction"}
        footer={
          <>
            <Button variant="secondary" onClick={close} disabled={save.isPending}>
              Cancel
            </Button>
            <Button type="submit" form={formId} loading={save.isPending}>
              {editing ? "Save changes" : "Add transaction"}
            </Button>
          </>
        }
      >
        {formOpen && (
          <TransactionForm
            key={editing?.id ?? "new"}
            formId={formId}
            transaction={editing}
            defaults={state.kind === "create" ? state.defaults : undefined}
            onSubmit={async (input) => {
              const saved = await save.mutateAsync({ id: editing?.id, input });
              toast.success(editing ? "Transaction updated." : `${saved.type === "INCOME" ? "Income" : "Expense"} added.`);
              close();
            }}
          />
        )}
      </Dialog>

      {state.kind === "view" && (
        <Dialog
          open
          onClose={close}
          title="Transaction details"
          footer={
            <>
              <Button variant="ghost" icon={<Trash2 className="size-4" />} onClick={() => setState({ kind: "delete", transaction: state.transaction })}>
                Delete
              </Button>
              <Button icon={<Pencil className="size-4" />} onClick={() => setState({ kind: "edit", transaction: state.transaction })}>
                Edit
              </Button>
            </>
          }
        >
          <TransactionDetail transaction={state.transaction} formatters={f} />
        </Dialog>
      )}

      <ConfirmDialog
        open={state.kind === "delete"}
        title="Delete this transaction?"
        confirmLabel="Delete transaction"
        loading={del.isPending}
        onClose={close}
        onConfirm={async () => {
          if (state.kind !== "delete") return;
          try {
            await del.mutateAsync(state.transaction.id);
            toast.success("Transaction deleted.");
            close();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
        message={
          state.kind === "delete" ? (
            <p>
              <span className="font-medium text-body">{state.transaction.description}</span> ·{" "}
              <Money amount={state.transaction.amount} currency={state.transaction.currency} type={state.transaction.type} /> on{" "}
              {f.date(state.transaction.transactionDate)}. Budgets and reports update immediately. This can't be undone.
            </p>
          ) : null
        }
      />
    </Ctx.Provider>
  );
}

function TransactionDetail({ transaction: t, formatters: f }: { transaction: Transaction; formatters: ReturnType<typeof useFormatters> }) {
  const rows: [string, ReactNode][] = [
    ["Category", t.category.name],
    ["Date", f.date(t.transactionDate, "long")],
    ["Merchant / payee", t.merchant ?? "—"],
    ["Payment method", t.paymentMethod ? PAYMENT_METHOD_LABELS[t.paymentMethod] : "—"],
    ["Currency", t.currency],
    ["Notes", t.notes ? <span className="whitespace-pre-wrap">{t.notes}</span> : "—"],
    ["Created", f.dateTime(t.createdAt)],
    ["Last updated", f.dateTime(t.updatedAt)],
  ];
  return (
    <div>
      <div className="mb-4 flex items-center gap-3">
        <CategoryIcon icon={t.category.icon} type={t.type} className="size-10" />
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">{t.description}</p>
          <p className="text-2xl font-semibold tracking-tight">
            <Money amount={t.amount} currency={t.currency} type={t.type} />
          </p>
        </div>
        {t.sample && <Badge tone="info" className="ml-auto">Sample</Badge>}
      </div>
      <dl className="divide-y divide-line rounded-lg border border-line">
        {rows.map(([k, v]) => (
          <div key={k} className="grid grid-cols-[9rem_1fr] gap-3 px-3 py-2 text-sm">
            <dt className="text-muted">{k}</dt>
            <dd className="min-w-0 break-words text-body">{v}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

export function useTransactionDialogs(): TransactionDialogApi {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useTransactionDialogs must be used inside TransactionDialogProvider");
  return ctx;
}
