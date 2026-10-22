import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError } from "./api";

/**
 * Copies server field errors onto the matching form fields. Returns the message to show at the form level when
 * the error isn't tied to a field the form knows about.
 */
export function applyServerErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>, fields: readonly string[]): string | null {
  if (!(error instanceof ApiError)) return "Something went wrong. Please try again.";
  let unmatched = error.fieldErrors.length === 0;
  for (const fe of error.fieldErrors) {
    if (fields.includes(fe.field)) {
      setError(fe.field as Path<T>, { type: "server", message: fe.message });
    } else {
      unmatched = true;
    }
  }
  if (!unmatched) return null;
  const extra = error.fieldErrors.filter((f) => !fields.includes(f.field)).map((f) => f.message);
  return extra.length ? extra.join(" ") : error.message;
}
