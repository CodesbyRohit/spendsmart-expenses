import { AlertTriangle, CheckCircle2, CircleDashed, OctagonAlert } from "lucide-react";
import type { BudgetStatus, Decimal } from "@/types/api";
import { cn } from "@/lib/cn";

const statusBar: Record<BudgetStatus, string> = {
  NO_LIMIT: "bg-subtle",
  ON_TRACK: "bg-primary",
  WARNING: "bg-warning",
  EXCEEDED: "bg-danger",
};

/**
 * Progress bar driven by the server's clamped {@code barPercent} (0–100), so overspending can never render a
 * negative or overflowing bar. The accessible value is the real (uncapped) percentage.
 */
export function ProgressBar({ barPercent, percentUsed, status, label, className }: { barPercent?: Decimal; percentUsed?: Decimal; status: BudgetStatus; label: string; className?: string }) {
  const width = Math.max(0, Math.min(100, Number(barPercent ?? 0)));
  const real = Number(percentUsed ?? 0);
  return (
    <div
      role="meter"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.min(real, 100)}
      aria-valuetext={`${real}% used`}
      className={cn("h-2 w-full overflow-hidden rounded-full bg-sunken", className)}
    >
      <div className={cn("h-full rounded-full transition-[width] duration-500", statusBar[status])} style={{ width: `${width}%` }} />
    </div>
  );
}

export function GoalBar({ barPercent, label, completed }: { barPercent: Decimal; label: string; completed: boolean }) {
  const width = Math.max(0, Math.min(100, Number(barPercent)));
  return (
    <div role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={width} className="h-2 w-full overflow-hidden rounded-full bg-sunken">
      <div className={cn("h-full rounded-full transition-[width] duration-500", completed ? "bg-primary" : "bg-[var(--chart-category)]")} style={{ width: `${width}%` }} />
    </div>
  );
}

/** Status is always shown with an icon AND words — never colour alone. */
export function StatusPill({ status, threshold }: { status: BudgetStatus; threshold?: number }) {
  const map = {
    NO_LIMIT: { icon: CircleDashed, text: "No limit", cls: "bg-sunken text-muted" },
    ON_TRACK: { icon: CheckCircle2, text: "On track", cls: "bg-primary-soft text-primary-text" },
    WARNING: { icon: AlertTriangle, text: threshold ? `Over ${threshold}%` : "Near limit", cls: "bg-warning-soft text-warning" },
    EXCEEDED: { icon: OctagonAlert, text: "Over budget", cls: "bg-danger-soft text-danger-text" },
  }[status];
  const Icon = map.icon;
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-medium", map.cls)}>
      <Icon className="size-3.5" aria-hidden="true" />
      {map.text}
    </span>
  );
}
