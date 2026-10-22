import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Card({ className, ...rest }: HTMLAttributes<HTMLElement>) {
  // min-w-0: grid/flex children otherwise grow to fit wide content (charts, tables) and overflow on phones.
  return <section className={cn("min-w-0 rounded-xl border border-line bg-surface shadow-card", className)} {...rest} />;
}

export function CardHeader({ title, subtitle, action, className, id }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; className?: string; id?: string }) {
  return (
    <div className={cn("flex items-start justify-between gap-3 px-5 pt-4 pb-3", className)}>
      <div className="min-w-0">
        <h2 id={id} className="text-sm font-semibold text-ink">
          {title}
        </h2>
        {subtitle && <p className="mt-0.5 text-xs text-muted">{subtitle}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function CardBody({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-5 pb-5", className)} {...rest} />;
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-xl font-semibold tracking-tight text-ink sm:text-2xl">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Badge({ tone = "neutral", children, className }: { tone?: "neutral" | "positive" | "negative" | "warning" | "info"; children: ReactNode; className?: string }) {
  const tones = {
    neutral: "bg-sunken text-muted",
    positive: "bg-primary-soft text-primary-text",
    negative: "bg-danger-soft text-danger-text",
    warning: "bg-warning-soft text-warning",
    info: "bg-[#e8eefb] text-[#2f4b80] dark:bg-[#18264a] dark:text-[#a9bff0]",
  };
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-medium", tones[tone], className)}>
      {children}
    </span>
  );
}
