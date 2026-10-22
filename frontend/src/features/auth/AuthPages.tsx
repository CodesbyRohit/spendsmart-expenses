import { useState, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { CheckCircle2 } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { Logo } from "@/layouts/AppLayout";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { InlineAlert } from "@/components/ui/States";
import { applyServerErrors } from "@/lib/forms";
import { passwordSchema } from "@/lib/schemas";
import { safeNext } from "@/routes/guards";

/** Must match the server lists (Money.SUPPORTED_CURRENCIES / Locales.SUPPORTED); the server validates regardless. */
const CURRENCIES = ["INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD", "AED", "CHF", "CNY", "HKD", "NZD", "SEK", "NOK", "DKK", "ZAR", "BRL", "MXN", "KWD"];
const LOCALES: Record<string, string> = {
  "en-IN": "English (India)", "en-US": "English (US)", "en-GB": "English (UK)", "en-AU": "English (Australia)",
  "en-CA": "English (Canada)", "en-SG": "English (Singapore)", "en-IE": "English (Ireland)", "en-NZ": "English (New Zealand)",
  "en-ZA": "English (South Africa)", "hi-IN": "हिन्दी (भारत)", "de-DE": "Deutsch", "fr-FR": "Français", "es-ES": "Español",
  "it-IT": "Italiano", "nl-NL": "Nederlands", "pt-BR": "Português (Brasil)", "ja-JP": "日本語", "sv-SE": "Svenska",
};
export { CURRENCIES as SUPPORTED_CURRENCIES, LOCALES as LOCALE_LABELS };

/** The browser's IANA zone, so "today" on the server matches the user's calendar. */
function browserTimeZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
  } catch {
    return undefined;
  }
}

function guessLocale(): string {
  const nav = typeof navigator !== "undefined" ? navigator.language : "en-IN";
  return LOCALES[nav] ? nav : "en-IN";
}

function guessCurrency(locale: string): string {
  const map: Record<string, string> = { "en-US": "USD", "en-GB": "GBP", "en-AU": "AUD", "en-CA": "CAD", "en-SG": "SGD", "en-NZ": "NZD", "en-ZA": "ZAR", "de-DE": "EUR", "fr-FR": "EUR", "es-ES": "EUR", "it-IT": "EUR", "nl-NL": "EUR", "en-IE": "EUR", "pt-BR": "BRL", "ja-JP": "JPY", "sv-SE": "SEK" };
  return map[locale] ?? "INR";
}

function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="grid min-h-dvh bg-bg lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-sidebar p-10 text-sidebar-text lg:flex">
        <Logo className="text-lg text-white" />
        <div className="max-w-md">
          <p className="text-3xl font-semibold leading-tight tracking-tight text-white">
            Know where your money goes.
            <br />
            <span className="text-[#5fd0aa]">Decide where it should go.</span>
          </p>
          <ul className="mt-8 space-y-3 text-sm">
            {[
              "Every figure is calculated from transactions you record — nothing is estimated or invented.",
              "Monthly budgets that warn you before you overspend, not after.",
              "Savings goals that show exactly what's left and what it takes per month.",
            ].map((t) => (
              <li key={t} className="flex gap-2.5">
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-[#5fd0aa]" aria-hidden="true" />
                {t}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-sidebar-text/70">Your data stays in this app. Nothing is shared with third parties.</p>
      </aside>
      <main className="flex items-center justify-center px-4 py-10 sm:px-6">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo className="text-lg text-ink" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
          <p className="mt-1 text-sm text-muted">{subtitle}</p>
          <div className="mt-6">{children}</div>
          <p className="mt-6 text-center text-sm text-muted">{footer}</p>
        </div>
      </main>
    </div>
  );
}

const loginSchema = z.object({
  email: z.string().trim().min(1, "Enter your email.").email("Enter a valid email address."),
  password: z.string().min(1, "Enter your password."),
});

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<z.infer<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null);
    try {
      await login(email, password);
      navigate(safeNext(params.get("next")), { replace: true });
    } catch (e) {
      setFormError(applyServerErrors(e, setError, ["email", "password"]));
    }
  });

  return (
    <AuthShell
      title="Sign in"
      subtitle="Welcome back. Pick up where you left off."
      footer={
        <>
          New to SpendSmart?{" "}
          <Link to="/register" className="font-medium text-primary-text hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <InlineAlert>{formError}</InlineAlert>}
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
        <Input label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register("password")} />
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Sign in
        </Button>
      </form>
    </AuthShell>
  );
}

const registerSchema = z.object({
  displayName: z.string().trim().min(1, "Enter your name.").max(80, "Name can be at most 80 characters."),
  email: z.string().trim().min(1, "Enter your email.").email("Enter a valid email address.").max(254),
  password: passwordSchema,
  defaultCurrency: z.string().length(3),
  locale: z.string().min(2),
});

export function RegisterPage() {
  const { register: signUp } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const locale = guessLocale();
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { displayName: "", email: "", password: "", defaultCurrency: guessCurrency(locale), locale },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await signUp({ ...values, email: values.email.trim(), displayName: values.displayName.trim(), timeZone: browserTimeZone() });
      navigate("/", { replace: true });
    } catch (e) {
      setFormError(applyServerErrors(e, setError, ["displayName", "email", "password", "defaultCurrency", "locale"]));
    }
  });

  return (
    <AuthShell
      title="Create your account"
      subtitle="Free, private, and ready in under a minute."
      footer={
        <>
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-primary-text hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && <InlineAlert>{formError}</InlineAlert>}
        <Input label="Your name" autoComplete="name" error={errors.displayName?.message} {...register("displayName")} />
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 10 characters. A short phrase is easier to remember and harder to guess."
          error={errors.password?.message}
          {...register("password")}
        />
        <div className="grid grid-cols-2 gap-3">
          <Select label="Main currency" error={errors.defaultCurrency?.message} {...register("defaultCurrency")}>
            {CURRENCIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </Select>
          <Select label="Number format" error={errors.locale?.message} {...register("locale")}>
            {Object.entries(LOCALES).map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </Select>
        </div>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
        <p className="text-xs text-muted">We'll set up common categories for you. You can rename or remove them later.</p>
      </form>
    </AuthShell>
  );
}
