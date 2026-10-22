import { useState } from "react";
import { useNavigate } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Download, ShieldAlert } from "lucide-react";
import { useAuth, useCurrentUser } from "@/hooks/useAuth";
import { useFormatters } from "@/hooks/useFormatters";
import { Card, CardBody, CardHeader, PageHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Field";
import { InlineAlert } from "@/components/ui/States";
import { useToast } from "@/components/ui/Toast";
import { LOCALE_LABELS } from "@/features/auth/AuthPages";
import { ApiError, errorMessage } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";
import { formatMoney } from "@/lib/format";
import { passwordSchema } from "@/lib/schemas";
import type { Theme } from "@/types/api";
import { exportAccountData, useAccountOptions, useChangePassword, useDeleteAccount, useUpdatePreferences, useUpdateProfile } from "./api";

export function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Your profile, preferences, security and data." />
      <div className="grid gap-5 lg:grid-cols-2">
        <ProfileCard />
        <PreferencesCard />
        <PasswordCard />
        <DataCard />
      </div>
    </>
  );
}

function ProfileCard() {
  const user = useCurrentUser();
  const update = useUpdateProfile();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);
  const schema = z.object({
    displayName: z.string().trim().min(1, "Enter your name.").max(80),
    email: z.string().trim().email("Enter a valid email address."),
    currentPassword: z.string(),
  });
  const { register, handleSubmit, watch, setError, reset, formState: { errors, isDirty } } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { displayName: user.displayName, email: user.email, currentPassword: "" },
  });
  const emailChanged = watch("email").trim().toLowerCase() !== user.email;

  const submit = handleSubmit(async (v) => {
    setFormError(null);
    if (emailChanged && !v.currentPassword) {
      setError("currentPassword", { message: "Enter your current password to change your email." });
      return;
    }
    try {
      const u = await update.mutateAsync(v);
      reset({ displayName: u.displayName, email: u.email, currentPassword: "" });
      toast.success("Profile saved.");
    } catch (e) {
      setFormError(applyServerErrors(e, setError, ["displayName", "email", "currentPassword"]));
    }
  });

  return (
    <Card>
      <CardHeader title="Profile" />
      <CardBody>
        <form onSubmit={submit} noValidate className="space-y-4">
          {formError && <InlineAlert>{formError}</InlineAlert>}
          <Input label="Name" autoComplete="name" error={errors.displayName?.message} {...register("displayName")} />
          <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
          {emailChanged && <Input label="Current password" type="password" autoComplete="current-password" error={errors.currentPassword?.message} {...register("currentPassword")} />}
          <Button type="submit" loading={update.isPending} disabled={!isDirty}>
            Save profile
          </Button>
        </form>
      </CardBody>
    </Card>
  );
}

function PreferencesCard() {
  const user = useCurrentUser();
  const options = useAccountOptions();
  const update = useUpdatePreferences();
  const toast = useToast();
  const [values, setValues] = useState({
    defaultCurrency: user.defaultCurrency,
    locale: user.locale,
    theme: user.theme,
    budgetAlertsEnabled: user.budgetAlertsEnabled,
    budgetAlertThreshold: user.budgetAlertThreshold,
  });
  const preview = formatMoney("1234567.89", values.defaultCurrency, values.locale);
  const f = useFormatters();

  return (
    <Card>
      <CardHeader title="Preferences" subtitle="How amounts and dates appear, and when you're alerted" />
      <CardBody>
        <form
          noValidate
          className="space-y-4"
          onSubmit={async (e) => {
            e.preventDefault();
            try {
              await update.mutateAsync(values);
              toast.success("Preferences saved.");
            } catch (err) {
              toast.error(errorMessage(err));
            }
          }}
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <Select label="Default currency" hint="Used for new transactions and the dashboard" value={values.defaultCurrency} onChange={(e) => setValues({ ...values, defaultCurrency: e.target.value })}>
              {(options.data?.currencies ?? [values.defaultCurrency]).map((c) => (
                <option key={c}>{c}</option>
              ))}
            </Select>
            <Select label="Number and date format" value={values.locale} onChange={(e) => setValues({ ...values, locale: e.target.value })}>
              {(options.data?.locales ?? [values.locale]).map((l) => (
                <option key={l} value={l}>
                  {LOCALE_LABELS[l] ?? l}
                </option>
              ))}
            </Select>
          </div>
          <p className="text-xs text-muted">
            Preview: <span className="tabular font-medium text-body">{preview}</span> · {new Intl.DateTimeFormat(values.locale, { dateStyle: "medium" }).format(new Date())}
          </p>
          <Select label="Theme" value={values.theme} onChange={(e) => setValues({ ...values, theme: e.target.value as Theme })}>
            <option value="SYSTEM">Match my device</option>
            <option value="LIGHT">Light</option>
            <option value="DARK">Dark</option>
          </Select>
          <fieldset className="space-y-3 rounded-lg border border-line p-3">
            <legend className="px-1 text-sm font-medium text-body">Budget alerts</legend>
            <label className="flex items-start gap-2.5 text-sm">
              <input type="checkbox" className="mt-0.5 size-4 accent-[var(--primary)]" checked={values.budgetAlertsEnabled} onChange={(e) => setValues({ ...values, budgetAlertsEnabled: e.target.checked })} />
              <span>
                Show in-app alerts when a budget reaches the threshold or is exceeded
                <span className="block text-xs text-muted">Alerts appear under the bell icon. SpendSmart does not send email, SMS or push notifications.</span>
              </span>
            </label>
            <Input
              label="Alert threshold (%)"
              type="number"
              min={50}
              max={100}
              step={5}
              disabled={!values.budgetAlertsEnabled}
              value={values.budgetAlertThreshold}
              onChange={(e) => setValues({ ...values, budgetAlertThreshold: Number(e.target.value) })}
              hint="Between 50% and 100% of a limit"
            />
          </fieldset>
          <Button type="submit" loading={update.isPending}>
            Save preferences
          </Button>
          <p className="text-xs text-subtle">Member since {f.date(user.createdAt.slice(0, 10))}</p>
        </form>
      </CardBody>
    </Card>
  );
}

function PasswordCard() {
  const change = useChangePassword();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);
  const schema = z
    .object({ currentPassword: z.string().min(1, "Enter your current password."), newPassword: passwordSchema, confirm: z.string() })
    .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Passwords don't match." });
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: "", newPassword: "", confirm: "" },
  });
  return (
    <Card>
      <CardHeader title="Password" subtitle="Changing it signs you out on every other device" />
      <CardBody>
        <form
          noValidate
          className="space-y-4"
          onSubmit={handleSubmit(async (v) => {
            setFormError(null);
            try {
              await change.mutateAsync({ currentPassword: v.currentPassword, newPassword: v.newPassword });
              reset();
              toast.success("Password changed. Other sessions have been signed out.");
            } catch (e) {
              setFormError(applyServerErrors(e, setError, ["currentPassword", "newPassword"]));
            }
          })}
        >
          {formError && <InlineAlert>{formError}</InlineAlert>}
          <Input label="Current password" type="password" autoComplete="current-password" error={errors.currentPassword?.message} {...register("currentPassword")} />
          <Input label="New password" type="password" autoComplete="new-password" hint="At least 10 characters." error={errors.newPassword?.message} {...register("newPassword")} />
          <Input label="Confirm new password" type="password" autoComplete="new-password" error={errors.confirm?.message} {...register("confirm")} />
          <Button type="submit" loading={change.isPending}>
            Change password
          </Button>
        </form>
      </CardBody>
    </Card>
  );
}

function DataCard() {
  const toast = useToast();
  const [exporting, setExporting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  return (
    <Card>
      <CardHeader title="Your data" />
      <CardBody className="space-y-5">
        <div>
          <p className="text-sm text-body">Download everything stored for your account — profile, categories, transactions, budgets, goals and alerts — as JSON.</p>
          <Button
            variant="secondary"
            className="mt-3"
            icon={<Download className="size-4" />}
            loading={exporting}
            onClick={async () => {
              setExporting(true);
              try {
                await exportAccountData();
              } catch (e) {
                toast.error(errorMessage(e));
              } finally {
                setExporting(false);
              }
            }}
          >
            Export account data
          </Button>
        </div>
        <div className="rounded-lg border border-danger/30 bg-danger-soft/50 p-4">
          <p className="flex items-center gap-2 text-sm font-medium text-danger-text">
            <ShieldAlert className="size-4" aria-hidden="true" /> Delete account
          </p>
          <p className="mt-1 text-sm text-muted">Permanently deletes your account and every record in it. This can't be undone, so export your data first.</p>
          <Button variant="danger" className="mt-3" onClick={() => setDeleting(true)}>
            Delete my account…
          </Button>
        </div>
      </CardBody>
      {deleting && <DeleteAccountDialog onClose={() => setDeleting(false)} />}
    </Card>
  );
}

function DeleteAccountDialog({ onClose }: { onClose: () => void }) {
  const del = useDeleteAccount();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog
      open
      onClose={onClose}
      size="sm"
      title="Delete your account permanently?"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            variant="danger"
            loading={del.isPending}
            disabled={confirmation !== "DELETE" || !password}
            onClick={async () => {
              setError(null);
              try {
                await del.mutateAsync({ password, confirmation });
                await logout().catch(() => undefined);
                navigate("/login", { replace: true });
              } catch (e) {
                setError(e instanceof ApiError && e.fieldErrors[0] ? e.fieldErrors[0].message : errorMessage(e));
              }
            }}
          >
            Delete everything
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-muted">All transactions, categories, budgets, savings goals and alerts will be erased immediately.</p>
        {error && <InlineAlert>{error}</InlineAlert>}
        <Input label="Your password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <Input label='Type "DELETE" to confirm' value={confirmation} onChange={(e) => setConfirmation(e.target.value)} autoComplete="off" />
      </div>
    </Dialog>
  );
}
