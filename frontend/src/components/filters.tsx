import { useSearchParams } from "react-router";
import { FilterSelect } from "@/components/ui/Field";
import { PRESET_LABELS, detectPreset, isValidISODate, presetRange, type DateRangeValue, type RangePreset } from "@/lib/dates";

/** Reads a date range from the URL (?from=&to=), defaulting to the current month. */
export function useUrlDateRange(defaultPreset: Exclude<RangePreset, "custom"> = "this-month") {
  const [params, setParams] = useSearchParams();
  const from = params.get("from");
  const to = params.get("to");
  const range: DateRangeValue =
    isValidISODate(from) && isValidISODate(to) && from <= to ? { from, to } : presetRange(defaultPreset);
  const setRange = (next: DateRangeValue) => {
    const p = new URLSearchParams(params);
    p.set("from", next.from);
    p.set("to", next.to);
    p.delete("page");
    setParams(p, { replace: true });
  };
  return { range, setRange, params, setParams };
}

export function DateRangeControl({ value, onChange }: { value: DateRangeValue; onChange: (v: DateRangeValue) => void }) {
  const preset = detectPreset(value);
  return (
    <div className="flex flex-wrap items-center gap-2">
      <FilterSelect
        label="Date range"
        value={preset}
        onChange={(e) => {
          const p = e.target.value as RangePreset;
          if (p !== "custom") onChange(presetRange(p));
        }}
      >
        {(Object.keys(PRESET_LABELS) as RangePreset[]).map((p) => (
          <option key={p} value={p} disabled={p === "custom"}>
            {PRESET_LABELS[p]}
          </option>
        ))}
      </FilterSelect>
      <div className="flex items-center gap-1.5">
        <label className="sr-only" htmlFor="range-from">
          From
        </label>
        <input
          id="range-from"
          type="date"
          value={value.from}
          max={value.to}
          onChange={(e) => isValidISODate(e.target.value) && e.target.value <= value.to && onChange({ ...value, from: e.target.value })}
          className="h-9 rounded-lg border border-line-strong bg-surface px-2 text-sm text-body focus:border-focus focus:outline-none"
        />
        <span className="text-subtle" aria-hidden="true">
          –
        </span>
        <label className="sr-only" htmlFor="range-to">
          To
        </label>
        <input
          id="range-to"
          type="date"
          value={value.to}
          min={value.from}
          onChange={(e) => isValidISODate(e.target.value) && e.target.value >= value.from && onChange({ ...value, to: e.target.value })}
          className="h-9 rounded-lg border border-line-strong bg-surface px-2 text-sm text-body focus:border-focus focus:outline-none"
        />
      </div>
    </div>
  );
}

export function CurrencyControl({ value, options, onChange }: { value: string; options: string[]; onChange: (c: string) => void }) {
  const list = options.includes(value) ? options : [value, ...options];
  if (list.length <= 1) return null;
  return (
    <FilterSelect label="Currency" value={value} onChange={(e) => onChange(e.target.value)}>
      {list.map((c) => (
        <option key={c} value={c}>
          {c}
        </option>
      ))}
    </FilterSelect>
  );
}
