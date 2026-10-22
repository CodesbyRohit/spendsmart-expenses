import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeftRight, Bell, ChartColumn, Ellipsis, FlaskConical, Gauge, LayoutDashboard, LogOut, PiggyBank, Plus, Search,
  Settings, Tags, X,
} from "lucide-react";
import { useAuth, useCurrentUser } from "@/hooks/useAuth";
import { useApplyTheme } from "@/hooks/useTheme";
import { useFormatters } from "@/hooks/useFormatters";
import { api } from "@/lib/api";
import { cn } from "@/lib/cn";
import { Button, IconButton } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import { useTransactionDialogs } from "@/features/transactions/TransactionDialogs";
import { useMarkNotificationsRead, useNotifications, useSampleData } from "@/features/analytics/api";

const NAV = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/transactions", label: "Transactions", icon: ArrowLeftRight },
  { to: "/budgets", label: "Budgets", icon: Gauge },
  { to: "/goals", label: "Savings goals", icon: PiggyBank },
  { to: "/analytics", label: "Analytics", icon: ChartColumn },
  { to: "/categories", label: "Categories", icon: Tags },
  { to: "/settings", label: "Settings", icon: Settings },
];

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-semibold tracking-tight", className)}>
      <svg viewBox="0 0 32 32" className="size-7" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill="#0f1b33" />
        <path d="M9 20.5c1.4 1.6 3.9 2.5 6.6 2.5 3.6 0 6.4-1.7 6.4-4.6 0-2.6-2-3.7-5.6-4.4l-1.6-.3c-2-.4-2.8-.9-2.8-1.8 0-1 1.1-1.7 3-1.7 1.7 0 3 .6 4 1.6" fill="none" stroke="#34d399" strokeWidth="2.6" strokeLinecap="round" />
      </svg>
      <span>SpendSmart</span>
    </span>
  );
}

export function AppLayout() {
  const user = useCurrentUser();
  const { logout } = useAuth();
  const dialogs = useTransactionDialogs();
  const navigate = useNavigate();
  const location = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  useApplyTheme(user.theme);

  useEffect(() => setMoreOpen(false), [location.pathname]);

  const signOut = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="min-h-dvh bg-bg">
      <a href="#main" className="sr-only z-50 rounded-md bg-surface px-3 py-2 focus:not-sr-only focus:fixed focus:left-3 focus:top-3">
        Skip to content
      </a>

      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col bg-sidebar text-sidebar-text lg:flex">
        <div className="px-5 pb-6 pt-5 text-white">
          <Logo className="text-[15px]" />
        </div>
        <nav aria-label="Main" className="flex-1 space-y-0.5 px-3">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors",
                  isActive ? "bg-sidebar-active font-medium text-white" : "hover:bg-sidebar-active/60 hover:text-white",
                )
              }
            >
              <item.icon className="size-4" aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-white/10 p-3">
          <div className="px-3 pb-2">
            <p className="truncate text-sm font-medium text-white">{user.displayName}</p>
            <p className="truncate text-xs">{user.email}</p>
          </div>
          <button type="button" onClick={signOut} className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm hover:bg-sidebar-active hover:text-white">
            <LogOut className="size-4" aria-hidden="true" /> Sign out
          </button>
        </div>
      </aside>

      <div className="lg:pl-60">
        <header className="sticky top-0 z-20 border-b border-line bg-surface/95 backdrop-blur supports-[backdrop-filter]:bg-surface/85">
          <div className="mx-auto flex h-14 max-w-7xl items-center gap-3 px-4 sm:px-6">
            <Link to="/" className="text-ink lg:hidden" aria-label="SpendSmart home">
              <Logo className="text-[15px]" />
            </Link>
            <GlobalSearch />
            <div className="ml-auto flex items-center gap-1.5">
              <NotificationsMenu />
              {/* Wrapper carries the breakpoint: the Button's own display class would override `hidden`. */}
              <div className="hidden sm:block">
                <Button icon={<Plus className="size-4" />} onClick={() => dialogs.create()}>
                  Add transaction
                </Button>
              </div>
            </div>
          </div>
        </header>

        <SampleDataBanner />

        <main id="main" className="mx-auto max-w-7xl px-4 pb-28 pt-6 sm:px-6 lg:pb-12">
          <Outlet />
        </main>
      </div>

      {/* Mobile bottom navigation; the primary action sits in the thumb zone. */}
      <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] lg:hidden">
        <div className="grid grid-cols-5">
          {[NAV[0]!, NAV[1]!].map((item) => (
            <MobileTab key={item.to} {...item} />
          ))}
          <div className="flex items-center justify-center">
            <button
              type="button"
              onClick={() => dialogs.create()}
              aria-label="Add transaction"
              className="-mt-5 flex size-12 items-center justify-center rounded-full bg-primary text-on-primary shadow-pop"
            >
              <Plus className="size-6" />
            </button>
          </div>
          <MobileTab {...NAV[2]!} />
          <button
            type="button"
            onClick={() => setMoreOpen((v) => !v)}
            aria-expanded={moreOpen}
            aria-controls="more-menu"
            className="flex flex-col items-center gap-0.5 py-2 text-[11px] text-muted"
          >
            <Ellipsis className="size-5" aria-hidden="true" />
            More
          </button>
        </div>
        {moreOpen && (
          <div id="more-menu" className="absolute inset-x-3 bottom-[calc(100%+0.5rem)] rounded-xl border border-line bg-surface p-2 shadow-pop animate-fade-in">
            {NAV.slice(3).map((item) => (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => cn("flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm", isActive ? "bg-sunken font-medium text-ink" : "text-body")}>
                <item.icon className="size-4 text-muted" aria-hidden="true" />
                {item.label}
              </NavLink>
            ))}
            <button type="button" onClick={signOut} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-body">
              <LogOut className="size-4 text-muted" aria-hidden="true" /> Sign out
            </button>
          </div>
        )}
      </nav>
    </div>
  );
}

function MobileTab({ to, label, icon: Icon, end }: { to: string; label: string; icon: typeof LayoutDashboard; end?: boolean }) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => cn("flex flex-col items-center gap-0.5 py-2 text-[11px]", isActive ? "font-medium text-primary-text" : "text-muted")}>
      <Icon className="size-5" aria-hidden="true" />
      {label === "Savings goals" ? "Goals" : label}
    </NavLink>
  );
}

function GlobalSearch() {
  const navigate = useNavigate();
  const location = useLocation();
  const initial = location.pathname === "/search" ? new URLSearchParams(location.search).get("q") ?? "" : "";
  const [q, setQ] = useState(initial);
  useEffect(() => setQ(initial), [initial]);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (q.trim().length >= 2) navigate(`/search?q=${encodeURIComponent(q.trim())}`);
  };
  return (
    <form role="search" onSubmit={submit} className="relative min-w-0 flex-1 sm:max-w-md">
      <label htmlFor="global-search" className="sr-only">
        Search transactions, categories and goals
      </label>
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-subtle" aria-hidden="true" />
      <input
        id="global-search"
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search"
        className="h-9 w-full rounded-lg border border-line bg-surface-2 pl-9 pr-3 text-sm placeholder:text-subtle focus:border-focus focus:bg-surface focus:outline-none focus:ring-2 focus:ring-focus/25"
      />
    </form>
  );
}

function NotificationsMenu() {
  const [open, setOpen] = useState(false);
  const notifications = useNotifications();
  const markRead = useMarkNotificationsRead();
  const f = useFormatters();
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const unread = notifications.data?.unreadCount ?? 0;

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <IconButton label={unread ? `Alerts, ${unread} unread` : "Alerts"} aria-expanded={open} aria-haspopup="true" onClick={() => setOpen((v) => !v)}>
        <Bell className="size-[18px]" />
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-4 text-white">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </IconButton>
      {open && (
        <div className="absolute right-0 top-11 w-[min(22rem,calc(100vw-2rem))] rounded-xl border border-line bg-surface shadow-pop animate-fade-in">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <h2 className="text-sm font-semibold text-ink">Budget alerts</h2>
            {unread > 0 && (
              <button type="button" className="text-xs font-medium text-primary-text hover:underline" onClick={() => markRead.mutate(undefined)}>
                Mark all read
              </button>
            )}
          </div>
          <ul className="max-h-96 divide-y divide-line overflow-y-auto">
            {notifications.data?.items.length ? (
              notifications.data.items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    className="block w-full px-4 py-3 text-left hover:bg-surface-2"
                    onClick={() => {
                      if (!n.read) markRead.mutate(n.id);
                      setOpen(false);
                      if (n.link) navigate(n.link);
                    }}
                  >
                    <p className={cn("flex items-center gap-2 text-sm", n.read ? "text-muted" : "font-medium text-ink")}>
                      {!n.read && <span className="size-1.5 rounded-full bg-danger" aria-label="Unread" />}
                      {n.title}
                    </p>
                    <p className="mt-0.5 text-xs text-muted">{n.message}</p>
                    <p className="mt-1 text-[11px] text-subtle">{f.dateTime(n.createdAt)}</p>
                  </button>
                </li>
              ))
            ) : (
              <li className="px-4 py-8 text-center text-sm text-muted">
                No alerts yet. You'll see one here when a budget reaches your alert threshold.
              </li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}

function SampleDataBanner() {
  const status = useQuery({ queryKey: ["sample-data"], queryFn: () => api<{ present: boolean }>("/api/sample-data") });
  const { remove } = useSampleData();
  const toast = useToast();
  if (!status.data?.present) return null;
  return (
    <div role="status" className="border-b border-[#c8d6f2] bg-[#eef3fc] text-[#24406f] dark:border-[#26385f] dark:bg-[#142240] dark:text-[#b9cbf0]">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2 text-sm sm:px-6">
        <FlaskConical className="size-4 shrink-0" aria-hidden="true" />
        <p className="flex-1">
          <span className="font-medium">Sample data is loaded.</span> Rows marked “Sample” are invented examples, not your finances.
        </p>
        <button
          type="button"
          disabled={remove.isPending}
          onClick={async () => {
            await remove.mutateAsync();
            toast.success("Sample data removed. Your own records are untouched.");
          }}
          className="inline-flex items-center gap-1 font-medium underline-offset-2 hover:underline"
        >
          <X className="size-3.5" aria-hidden="true" /> Remove sample data
        </button>
      </div>
    </div>
  );
}
