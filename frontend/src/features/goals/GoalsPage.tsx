import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { CalendarClock, History, Minus, MoreHorizontal, PiggyBank, Plus, Trash2 } from "lucide-react";
import { useCurrentUser } from "@/hooks/useAuth";
import { useFormatters } from "@/hooks/useFormatters";
import { Card, PageHeader, Badge } from "@/components/ui/Card";
import { Button, IconButton } from "@/components/ui/Button";
import { ConfirmDialog, Dialog } from "@/components/ui/Dialog";
import { Input, Select, Textarea } from "@/components/ui/Field";
import { Segmented } from "@/components/ui/Segmented";
import { EmptyState, ErrorState, InlineAlert } from "@/components/ui/States";
import { PageLoader } from "@/components/ui/Spinner";
import { GoalBar } from "@/components/ui/Progress";
import { useToast } from "@/components/ui/Toast";
import { useAccountOptions } from "@/features/settings/api";
import { errorMessage } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";
import { amountSchema, isoDateSchema } from "@/lib/schemas";
import { currencySymbol } from "@/lib/format";
import { todayISO } from "@/lib/dates";
import type { ContributionType, Goal } from "@/types/api";
import { useAddContribution, useContributions, useDeleteContribution, useDeleteGoal, useGoals, useSaveGoal } from "./api";

type Modal = { kind: "none" } | { kind: "create" } | { kind: "edit"; goal: Goal } | { kind: "contribute"; goal: Goal; type: ContributionType } | { kind: "history"; goal: Goal } | { kind: "delete"; goal: Goal };

export function GoalsPage() {
  const goals = useGoals();
  const [modal, setModal] = useState<Modal>({ kind: "none" });
  const close = () => setModal({ kind: "none" });
  const del = useDeleteGoal();
  const toast = useToast();

  return (
    <>
      <PageHeader
        title="Savings goals"
        description="Track what you're setting aside. Saved amounts are what you record here — SpendSmart doesn't connect to or verify any bank account."
        actions={
          <Button icon={<Plus className="size-4" />} onClick={() => setModal({ kind: "create" })}>
            New goal
          </Button>
        }
      />
      {goals.isPending ? (
        <PageLoader />
      ) : goals.isError ? (
        <Card>
          <ErrorState error={goals.error} onRetry={() => goals.refetch()} />
        </Card>
      ) : goals.data.length === 0 ? (
        <Card>
          <EmptyState
            icon={<PiggyBank className="size-5" />}
            title="No savings goals yet"
            description="An emergency fund, a laptop, next semester's fees — give it a target and record contributions as you save."
            action={
              <Button icon={<Plus className="size-4" />} onClick={() => setModal({ kind: "create" })}>
                Create a goal
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {goals.data.map((g) => (
            <GoalCard key={g.id} goal={g} onAction={setModal} />
          ))}
        </div>
      )}

      {(modal.kind === "create" || modal.kind === "edit") && <GoalFormDialog goal={modal.kind === "edit" ? modal.goal : undefined} onClose={close} />}
      {modal.kind === "contribute" && <ContributionDialog goal={modal.goal} type={modal.type} onClose={close} />}
      {modal.kind === "history" && <HistoryDialog goal={modal.goal} onClose={close} />}
      <ConfirmDialog
        open={modal.kind === "delete"}
        title="Delete this goal?"
        message={modal.kind === "delete" ? <p>“{modal.goal.name}” and its {modal.goal.contributionCount} recorded contribution(s) will be permanently removed. Your transactions are not affected.</p> : null}
        confirmLabel="Delete goal"
        loading={del.isPending}
        onClose={close}
        onConfirm={async () => {
          if (modal.kind !== "delete") return;
          try {
            await del.mutateAsync(modal.goal.id);
            toast.success("Goal deleted.");
            close();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </>
  );
}

function GoalCard({ goal: g, onAction }: { goal: Goal; onAction: (m: Modal) => void }) {
  const f = useFormatters();
  const [menu, setMenu] = useState(false);
  return (
    <Card className="flex flex-col p-5">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-ink">{g.name}</h2>
          <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-muted">
            {g.targetDate ? (
              <>
                <CalendarClock className="size-3.5" aria-hidden="true" /> Target {f.date(g.targetDate)}
                {g.daysLeft !== undefined && !g.completed && <span>· {g.daysLeft < 0 ? `${Math.abs(g.daysLeft)} days past` : `${g.daysLeft} days left`}</span>}
              </>
            ) : (
              "No target date"
            )}
          </p>
        </div>
        <div className="flex items-center gap-1">
          {g.completed && <Badge tone="positive">Reached</Badge>}
          {g.sample && <Badge tone="info">Sample</Badge>}
          <div className="relative">
            <IconButton label={`More actions for ${g.name}`} aria-expanded={menu} onClick={() => setMenu((v) => !v)} onBlur={(e) => !e.currentTarget.parentElement?.contains(e.relatedTarget) && setMenu(false)}>
              <MoreHorizontal className="size-4" />
            </IconButton>
            {menu && (
              <div className="absolute right-0 top-10 z-10 w-44 rounded-lg border border-line bg-surface p-1 shadow-pop" onBlur={(e) => !e.currentTarget.contains(e.relatedTarget) && setMenu(false)}>
                {[
                  ["Edit goal", () => onAction({ kind: "edit", goal: g })],
                  ["Contribution history", () => onAction({ kind: "history", goal: g })],
                  ["Delete goal", () => onAction({ kind: "delete", goal: g })],
                ].map(([label, fn]) => (
                  <button key={label as string} type="button" className="block w-full rounded-md px-3 py-2 text-left text-sm text-body hover:bg-sunken" onClick={() => { setMenu(false); (fn as () => void)(); }}>
                    {label as string}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
      <div className="mt-4">
        <div className="mb-1.5 flex items-baseline justify-between">
          <span className="tabular text-2xl font-semibold tracking-tight text-ink">{f.money(g.savedAmount, g.currency)}</span>
          <span className="tabular text-sm text-muted">{f.percent(g.progressPercent)}</span>
        </div>
        <GoalBar barPercent={g.barPercent} completed={g.completed} label={`${g.name}: ${g.progressPercent}% saved`} />
        <p className="tabular mt-1.5 text-xs text-muted">of {f.money(g.targetAmount, g.currency)} target</p>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-line pt-3 text-sm">
        <div>
          <dt className="text-xs text-muted">Still to save</dt>
          <dd className="tabular font-medium text-body">{f.money(g.remainingAmount, g.currency)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted">Needed per month</dt>
          <dd className="tabular font-medium text-body">{g.monthlyNeeded ? f.money(g.monthlyNeeded, g.currency) : "—"}</dd>
        </div>
      </dl>
      <div className="mt-4 flex gap-2">
        <Button size="sm" className="flex-1" icon={<Plus className="size-4" />} onClick={() => onAction({ kind: "contribute", goal: g, type: "DEPOSIT" })}>
          Add money
        </Button>
        <Button size="sm" variant="secondary" icon={<Minus className="size-4" />} disabled={Number(g.savedAmount) <= 0} onClick={() => onAction({ kind: "contribute", goal: g, type: "WITHDRAWAL" })}>
          Withdraw
        </Button>
        <IconButton label="Contribution history" onClick={() => onAction({ kind: "history", goal: g })}>
          <History className="size-4" />
        </IconButton>
      </div>
    </Card>
  );
}

function GoalFormDialog({ goal, onClose }: { goal?: Goal; onClose: () => void }) {
  const user = useCurrentUser();
  const options = useAccountOptions();
  const save = useSaveGoal();
  const toast = useToast();
  const formId = useId();
  const [formError, setFormError] = useState<string | null>(null);
  const currencyRef = { current: goal?.currency ?? user.defaultCurrency };
  const schema = z.object({
    name: z.string().trim().min(1, "Enter a goal name.").max(80, "Name can be at most 80 characters."),
    targetAmount: amountSchema(() => currencyRef.current, "A target"),
    currency: z.string().length(3),
    targetDate: z.union([z.literal(""), isoDateSchema]),
    notes: z.string().max(500, "Notes can be at most 500 characters."),
    initialAmount: z.union([z.literal(""), amountSchema(() => currencyRef.current, "The amount")]),
  });
  type Values = z.infer<typeof schema>;
  const { register, handleSubmit, setError, watch, formState: { errors } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { name: goal?.name ?? "", targetAmount: goal?.targetAmount ?? "", currency: goal?.currency ?? user.defaultCurrency, targetDate: goal?.targetDate ?? "", notes: goal?.notes ?? "", initialAmount: "" },
  });
  currencyRef.current = watch("currency");

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    try {
      await save.mutateAsync({ id: goal?.id, input: { ...v, version: goal?.version } });
      toast.success(goal ? "Goal updated." : "Goal created.");
      onClose();
    } catch (e) {
      setFormError(applyServerErrors(e, setError, ["name", "targetAmount", "currency", "targetDate", "notes", "initialAmount"]));
    }
  });

  return (
    <Dialog
      open
      onClose={onClose}
      title={goal ? "Edit goal" : "New savings goal"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form={formId} loading={save.isPending}>{goal ? "Save changes" : "Create goal"}</Button>
        </>
      }
    >
      <form id={formId} onSubmit={submit} noValidate className="space-y-4">
        {formError && <InlineAlert>{formError}</InlineAlert>}
        <Input label="Goal name" placeholder="e.g. Emergency fund" error={errors.name?.message} {...register("name")} autoFocus />
        <div className="grid grid-cols-[1fr_7rem] gap-3">
          <Input label="Target amount" inputMode="decimal" className="tabular" prefix={currencySymbol(currencyRef.current, user.locale)} error={errors.targetAmount?.message} {...register("targetAmount")} />
          <Select label="Currency" disabled={Boolean(goal)} hint={goal ? "Fixed after creation" : undefined} {...register("currency")}>
            {(options.data?.currencies ?? [user.defaultCurrency]).map((c) => (
              <option key={c}>{c}</option>
            ))}
          </Select>
        </div>
        <Input label="Target date" type="date" optional min={todayISO()} error={errors.targetDate?.message} {...register("targetDate")} />
        {!goal && (
          <Input
            label="Already saved"
            optional
            inputMode="decimal"
            className="tabular"
            hint="Recorded as your first deposit, dated today."
            error={errors.initialAmount?.message}
            {...register("initialAmount")}
          />
        )}
        <Textarea label="Notes" optional rows={2} error={errors.notes?.message} {...register("notes")} />
      </form>
    </Dialog>
  );
}

function ContributionDialog({ goal, type: initialType, onClose }: { goal: Goal; type: ContributionType; onClose: () => void }) {
  const user = useCurrentUser();
  const f = useFormatters();
  const add = useAddContribution();
  const toast = useToast();
  const formId = useId();
  const [type, setType] = useState<ContributionType>(initialType);
  const [formError, setFormError] = useState<string | null>(null);
  const schema = z.object({
    amount: amountSchema(() => goal.currency).refine((v) => type === "DEPOSIT" || Number(v) <= Number(goal.savedAmount), `You can withdraw at most ${f.money(goal.savedAmount, goal.currency)}.`),
    date: isoDateSchema.refine((v) => v <= todayISO(), "Contributions can't be dated in the future."),
    note: z.string().max(200, "Note can be at most 200 characters."),
  });
  type Values = z.infer<typeof schema>;
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { amount: "", date: todayISO(), note: "" } });

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    try {
      await add.mutateAsync({ goalId: goal.id, input: { type, ...v } });
      toast.success(type === "DEPOSIT" ? `Added to ${goal.name}.` : `Withdrawal recorded for ${goal.name}.`);
      onClose();
    } catch (e) {
      setFormError(applyServerErrors(e, setError, ["amount", "date", "note"]));
    }
  });

  return (
    <Dialog
      open
      onClose={onClose}
      title={goal.name}
      description={`Saved so far: ${f.money(goal.savedAmount, goal.currency)} of ${f.money(goal.targetAmount, goal.currency)}`}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form={formId} loading={add.isPending}>{type === "DEPOSIT" ? "Add money" : "Record withdrawal"}</Button>
        </>
      }
    >
      <form id={formId} onSubmit={submit} noValidate className="space-y-4">
        {formError && <InlineAlert>{formError}</InlineAlert>}
        <Segmented label="Contribution type" value={type} onChange={setType} className="w-full" options={[{ value: "DEPOSIT", label: "Deposit" }, { value: "WITHDRAWAL", label: "Withdrawal" }]} />
        <Input label="Amount" inputMode="decimal" className="tabular" prefix={currencySymbol(goal.currency, user.locale)} error={errors.amount?.message} autoFocus {...register("amount")} />
        <Input label="Date" type="date" max={todayISO()} error={errors.date?.message} {...register("date")} />
        <Input label="Note" optional maxLength={200} error={errors.note?.message} {...register("note")} />
        <p className="text-xs text-muted">This records money you've set aside yourself. It doesn't move money or create a transaction.</p>
      </form>
    </Dialog>
  );
}

function HistoryDialog({ goal, onClose }: { goal: Goal; onClose: () => void }) {
  const f = useFormatters();
  const history = useContributions(goal.id);
  const del = useDeleteContribution();
  const toast = useToast();
  const [confirming, setConfirming] = useState<string | null>(null);
  return (
    <Dialog open onClose={onClose} title={`${goal.name} · history`} description="Every deposit and withdrawal you've recorded for this goal." footer={<Button variant="secondary" onClick={onClose}>Close</Button>}>
      {history.isPending ? (
        <PageLoader />
      ) : history.isError ? (
        <ErrorState error={history.error} onRetry={() => history.refetch()} />
      ) : history.data.length === 0 ? (
        <EmptyState compact title="No contributions yet" />
      ) : (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {history.data.map((c) => (
            <li key={c.id} className="flex items-center gap-3 px-3 py-2.5">
              <div className="min-w-0 flex-1">
                <p className="text-sm text-body">{c.type === "DEPOSIT" ? "Deposit" : "Withdrawal"} · {f.date(c.date)}</p>
                {c.note && <p className="truncate text-xs text-muted">{c.note}</p>}
              </div>
              <span className={`tabular text-sm font-medium ${c.type === "DEPOSIT" ? "text-primary-text" : "text-body"}`}>
                {c.type === "DEPOSIT" ? "+" : "−"}
                {f.money(c.amount, goal.currency)}
              </span>
              {confirming === c.id ? (
                <span className="flex items-center gap-1">
                  <Button
                    size="sm"
                    variant="danger"
                    loading={del.isPending}
                    onClick={async () => {
                      try {
                        await del.mutateAsync({ goalId: goal.id, contributionId: c.id });
                        toast.success("Entry removed.");
                      } catch (e) {
                        toast.error(errorMessage(e));
                      } finally {
                        setConfirming(null);
                      }
                    }}
                  >
                    Remove
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setConfirming(null)}>
                    Keep
                  </Button>
                </span>
              ) : (
                <IconButton label="Delete this entry" className="size-8 hover:text-danger-text" onClick={() => setConfirming(c.id)}>
                  <Trash2 className="size-3.5" />
                </IconButton>
              )}
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  );
}
