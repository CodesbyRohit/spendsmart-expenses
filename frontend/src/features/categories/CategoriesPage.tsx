import { useId, useState } from "react";
import { Archive, ArchiveRestore, Pencil, Plus, Trash2 } from "lucide-react";
import { Card, CardHeader, PageHeader, Badge } from "@/components/ui/Card";
import { Button, IconButton } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Field";
import { Segmented } from "@/components/ui/Segmented";
import { ErrorState, InlineAlert } from "@/components/ui/States";
import { PageLoader } from "@/components/ui/Spinner";
import { useToast } from "@/components/ui/Toast";
import { CATEGORY_ICONS, CategoryIcon } from "@/components/CategoryIcon";
import { ApiError, errorMessage } from "@/lib/api";
import { cn } from "@/lib/cn";
import type { Category, TransactionType } from "@/types/api";
import { useCategories, useCategoryIcons, useCategoryUsage, useDeleteCategory, useSaveCategory } from "./api";

type Modal = { kind: "none" } | { kind: "form"; category?: Category; type: TransactionType } | { kind: "delete"; category: Category };

export function CategoriesPage() {
  const categories = useCategories(true);
  const [modal, setModal] = useState<Modal>({ kind: "none" });
  const save = useSaveCategory();
  const toast = useToast();

  const toggleArchive = async (c: Category) => {
    try {
      await save.mutateAsync({ id: c.id, input: { name: c.name, icon: c.icon, archived: !c.archived } });
      toast.success(c.archived ? `${c.name} restored.` : `${c.name} archived. Existing transactions keep it.`);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader
        title="Categories"
        description="Organise income and expenses. Archived categories stay on past transactions but can't be picked for new ones."
        actions={
          <Button icon={<Plus className="size-4" />} onClick={() => setModal({ kind: "form", type: "EXPENSE" })}>
            New category
          </Button>
        }
      />
      {categories.isPending ? (
        <PageLoader />
      ) : categories.isError ? (
        <Card>
          <ErrorState error={categories.error} onRetry={() => categories.refetch()} />
        </Card>
      ) : (
        <div className="grid gap-5 lg:grid-cols-2">
          {(["EXPENSE", "INCOME"] as const).map((type) => {
            const list = categories.data.filter((c) => c.type === type);
            return (
              <Card key={type}>
                <CardHeader
                  title={type === "EXPENSE" ? "Expense categories" : "Income categories"}
                  subtitle={`${list.filter((c) => !c.archived).length} active`}
                  action={
                    <Button variant="ghost" size="sm" icon={<Plus className="size-4" />} onClick={() => setModal({ kind: "form", type })}>
                      Add
                    </Button>
                  }
                />
                <ul className="divide-y divide-line border-t border-line">
                  {list.map((c) => (
                    <li key={c.id} className={cn("flex items-center gap-3 px-5 py-2.5", c.archived && "opacity-60")}>
                      <CategoryIcon icon={c.icon} type={c.type} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-ink">
                          {c.name}
                          {c.archived && <Badge className="ml-2">Archived</Badge>}
                        </p>
                        <p className="text-xs text-muted">
                          {c.transactionCount} transaction{c.transactionCount === 1 ? "" : "s"}
                          {c.isDefault ? " · default" : ""}
                        </p>
                      </div>
                      <IconButton label={`Edit ${c.name}`} onClick={() => setModal({ kind: "form", category: c, type: c.type })}>
                        <Pencil className="size-4" />
                      </IconButton>
                      <IconButton label={c.archived ? `Restore ${c.name}` : `Archive ${c.name}`} onClick={() => toggleArchive(c)}>
                        {c.archived ? <ArchiveRestore className="size-4" /> : <Archive className="size-4" />}
                      </IconButton>
                      <IconButton label={`Delete ${c.name}`} className="hover:text-danger-text" onClick={() => setModal({ kind: "delete", category: c })}>
                        <Trash2 className="size-4" />
                      </IconButton>
                    </li>
                  ))}
                </ul>
              </Card>
            );
          })}
        </div>
      )}
      {modal.kind === "form" && <CategoryForm category={modal.category} type={modal.type} onClose={() => setModal({ kind: "none" })} />}
      {modal.kind === "delete" && <DeleteCategory category={modal.category} all={categories.data ?? []} onClose={() => setModal({ kind: "none" })} />}
    </>
  );
}

function CategoryForm({ category, type: initialType, onClose }: { category?: Category; type: TransactionType; onClose: () => void }) {
  const icons = useCategoryIcons();
  const save = useSaveCategory();
  const toast = useToast();
  const formId = useId();
  const [name, setName] = useState(category?.name ?? "");
  const [type, setType] = useState<TransactionType>(initialType);
  const [icon, setIcon] = useState(category?.icon ?? "circle-dot");
  const [error, setError] = useState<{ name?: string; form?: string }>({});

  const submit = async () => {
    if (!name.trim()) return setError({ name: "Enter a name." });
    try {
      await save.mutateAsync({ id: category?.id, input: { name: name.trim(), icon, type, archived: category?.archived } });
      toast.success(category ? "Category updated." : "Category created.");
      onClose();
    } catch (e) {
      const fieldMsg = e instanceof ApiError ? e.fieldError("name") : undefined;
      setError(fieldMsg ? { name: fieldMsg } : { form: errorMessage(e) });
    }
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title={category ? "Edit category" : "New category"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" form={formId} loading={save.isPending}>{category ? "Save" : "Create"}</Button>
        </>
      }
    >
      <form id={formId} noValidate onSubmit={(e) => { e.preventDefault(); void submit(); }} className="space-y-4">
        {error.form && <InlineAlert>{error.form}</InlineAlert>}
        {category ? (
          <p className="text-sm text-muted">Type: {category.type === "EXPENSE" ? "Expense" : "Income"} (can't be changed once created)</p>
        ) : (
          <Segmented label="Category type" value={type} onChange={setType} options={[{ value: "EXPENSE", label: "Expense" }, { value: "INCOME", label: "Income" }]} />
        )}
        <Input label="Name" maxLength={50} value={name} onChange={(e) => setName(e.target.value)} error={error.name} autoFocus />
        <fieldset>
          <legend className="mb-2 text-sm font-medium text-body">Icon</legend>
          <div className="grid grid-cols-8 gap-1.5 sm:grid-cols-11">
            {(icons.data ?? Object.keys(CATEGORY_ICONS)).map((key) => {
              const Icon = CATEGORY_ICONS[key];
              if (!Icon) return null;
              return (
                <label key={key} className={cn("flex size-9 cursor-pointer items-center justify-center rounded-lg border has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus", icon === key ? "border-primary bg-primary-soft text-primary-text" : "border-line text-muted hover:bg-sunken")}>
                  <input type="radio" name="icon" value={key} checked={icon === key} onChange={() => setIcon(key)} className="sr-only" aria-label={key.replace(/-/g, " ")} />
                  <Icon className="size-4" aria-hidden="true" />
                </label>
              );
            })}
          </div>
        </fieldset>
      </form>
    </Dialog>
  );
}

function DeleteCategory({ category, all, onClose }: { category: Category; all: Category[]; onClose: () => void }) {
  const usage = useCategoryUsage(category.id);
  const del = useDeleteCategory();
  const toast = useToast();
  const targets = all.filter((c) => c.type === category.type && c.id !== category.id && !c.archived);
  const [target, setTarget] = useState("");
  const inUse = (usage.data?.transactionCount ?? 0) > 0;

  return (
    <Dialog
      open
      onClose={onClose}
      size="sm"
      title={`Delete “${category.name}”?`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            variant="danger"
            loading={del.isPending}
            disabled={usage.isPending || (inUse && !target)}
            onClick={async () => {
              try {
                await del.mutateAsync({ id: category.id, reassignTo: inUse ? target : undefined });
                toast.success(inUse ? `Deleted ${category.name} and moved its transactions.` : `Deleted ${category.name}.`);
                onClose();
              } catch (e) {
                toast.error(errorMessage(e));
              }
            }}
          >
            {inUse ? "Move and delete" : "Delete"}
          </Button>
        </>
      }
    >
      {usage.isPending ? (
        <PageLoader />
      ) : (
        <div className="space-y-4 text-sm text-muted">
          {inUse ? (
            <>
              <p>
                {usage.data!.transactionCount} transaction(s) use this category. Choose where to move them — or cancel and archive the category instead to keep history unchanged.
              </p>
              <Select label="Move transactions to" value={target} onChange={(e) => setTarget(e.target.value)}>
                <option value="">Choose a category</option>
                {targets.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </Select>
            </>
          ) : (
            <p>No transactions use this category.</p>
          )}
          {(usage.data?.budgetLineCount ?? 0) > 0 && <InlineAlert tone="warning">Its {usage.data!.budgetLineCount} budget limit(s) will also be removed.</InlineAlert>}
        </div>
      )}
    </Dialog>
  );
}
