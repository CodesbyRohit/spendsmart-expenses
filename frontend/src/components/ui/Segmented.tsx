import { useId } from "react";
import { cn } from "@/lib/cn";

interface Option<T extends string> {
  value: T;
  label: string;
}

/** Radio group styled as a segmented control; arrow keys move between options natively. */
export function Segmented<T extends string>({ label, value, options, onChange, className, size = "md" }: { label: string; value: T; options: Option<T>[]; onChange: (v: T) => void; className?: string; size?: "sm" | "md" }) {
  const name = useId();
  return (
    <fieldset className={cn("inline-flex rounded-lg border border-line bg-sunken p-0.5", className)}>
      <legend className="sr-only">{label}</legend>
      {options.map((o) => (
        <label
          key={o.value}
          className={cn(
            "relative flex-1 cursor-pointer rounded-md text-center text-sm font-medium transition-colors",
            "has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus",
            size === "sm" ? "px-2.5 py-1" : "px-3.5 py-1.5",
            value === o.value ? "bg-surface text-ink shadow-sm" : "text-muted hover:text-body",
          )}
        >
          <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)} className="sr-only" />
          {o.label}
        </label>
      ))}
    </fieldset>
  );
}
