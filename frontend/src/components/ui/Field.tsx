import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

const control =
  "block w-full rounded-lg border bg-surface px-3 text-sm text-body placeholder:text-subtle transition-colors " +
  "border-line-strong hover:border-subtle focus:border-focus focus:outline-none focus:ring-2 focus:ring-focus/25 " +
  "disabled:cursor-not-allowed disabled:bg-sunken disabled:text-muted aria-[invalid=true]:border-danger";

interface FieldShellProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  optional?: boolean;
  hideLabel?: boolean;
  className?: string;
  children: (ids: { id: string; describedBy: string | undefined; invalid: boolean }) => ReactNode;
}

/** Label + control + hint + error, wired with aria-describedby / aria-invalid. */
export function FieldShell({ label, error, hint, optional, hideLabel, className, children }: FieldShellProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;
  return (
    <div className={cn("space-y-1.5", className)}>
      <label htmlFor={id} className={cn("block text-sm font-medium text-body", hideLabel && "sr-only")}>
        {label}
        {optional && <span className="ml-1 font-normal text-subtle">(optional)</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium text-danger-text" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: ReactNode;
  optional?: boolean;
  hideLabel?: boolean;
  prefix?: string;
  containerClassName?: string;
};

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, optional, hideLabel, prefix, className, containerClassName, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} hint={hint} optional={optional} hideLabel={hideLabel} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <div className="relative">
          {prefix && (
            <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-muted">{prefix}</span>
          )}
          <input
            ref={ref}
            id={id}
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            className={cn(control, "h-10", prefix && "pl-8", className)}
            {...rest}
          />
        </div>
      )}
    </FieldShell>
  );
});

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  error?: string;
  hint?: ReactNode;
  optional?: boolean;
  hideLabel?: boolean;
  containerClassName?: string;
};

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, hint, optional, hideLabel, className, containerClassName, children, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} hint={hint} optional={optional} hideLabel={hideLabel} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <select
          ref={ref}
          id={id}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={cn(control, "h-10 pr-8", className)}
          {...rest}
        >
          {children}
        </select>
      )}
    </FieldShell>
  );
});

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  error?: string;
  hint?: ReactNode;
  optional?: boolean;
  containerClassName?: string;
};

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, error, hint, optional, className, containerClassName, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} hint={hint} optional={optional} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <textarea
          ref={ref}
          id={id}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={cn(control, "min-h-20 py-2", className)}
          {...rest}
        />
      )}
    </FieldShell>
  );
});

/** Small inline select for filter bars (label kept for screen readers). */
export const FilterSelect = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { label: string }>(
  function FilterSelect({ label, className, children, ...rest }, ref) {
    return (
      <label className="relative inline-flex">
        <span className="sr-only">{label}</span>
        <select ref={ref} className={cn(control, "h-9 w-auto pr-8", className)} {...rest}>
          {children}
        </select>
      </label>
    );
  },
);
