import type { ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import { errorMessage } from "@/lib/api";
import { Button } from "./Button";

export function EmptyState({ icon, title, description, action, compact }: { icon?: ReactNode; title: string; description?: ReactNode; action?: ReactNode; compact?: boolean }) {
  return (
    <div className={compact ? "px-4 py-8 text-center" : "px-6 py-14 text-center"}>
      {icon && <div className="mx-auto mb-3 flex size-11 items-center justify-center rounded-full bg-sunken text-muted">{icon}</div>}
      <h3 className="text-sm font-semibold text-ink">{title}</h3>
      {description && <p className="mx-auto mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-4 flex justify-center gap-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry, title = "Couldn't load this" }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <div role="alert" className="px-6 py-10 text-center">
      <div className="mx-auto mb-3 flex size-11 items-center justify-center rounded-full bg-danger-soft text-danger-text">
        <AlertTriangle className="size-5" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-semibold text-ink">{title}</h3>
      <p className="mx-auto mt-1 max-w-sm text-sm text-muted">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function InlineAlert({ tone = "danger", children }: { tone?: "danger" | "warning" | "info" | "positive"; children: ReactNode }) {
  const tones = {
    danger: "border-danger/30 bg-danger-soft text-danger-text",
    warning: "border-warning/30 bg-warning-soft text-warning",
    info: "border-line bg-surface-2 text-muted",
    positive: "border-primary/30 bg-primary-soft text-primary-text",
  };
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={`rounded-lg border px-3 py-2.5 text-sm ${tones[tone]}`}>
      {children}
    </div>
  );
}
