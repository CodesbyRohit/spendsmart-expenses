import {
  Baby, Book, Briefcase, Bus, Car, CircleDot, Coffee, Dumbbell, Film, Fuel, Gamepad2, Gift, GraduationCap, HandCoins,
  HeartPulse, Home, Landmark, Laptop, Music, PawPrint, PiggyBank, Plane, Receipt, Repeat, Shirt, ShoppingBag,
  ShoppingCart, Smartphone, TrendingUp, Utensils, Wifi, Wrench, Zap, type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/cn";
import type { TransactionType } from "@/types/api";

/** Must match CategoryIcons.ALL on the server; unknown keys fall back to a neutral dot. */
export const CATEGORY_ICONS: Record<string, LucideIcon> = {
  utensils: Utensils, "shopping-cart": ShoppingCart, bus: Bus, car: Car, fuel: Fuel, "graduation-cap": GraduationCap,
  "shopping-bag": ShoppingBag, home: Home, zap: Zap, wifi: Wifi, smartphone: Smartphone, "heart-pulse": HeartPulse,
  film: Film, music: Music, gamepad: Gamepad2, repeat: Repeat, plane: Plane, briefcase: Briefcase, laptop: Laptop,
  gift: Gift, "piggy-bank": PiggyBank, "trending-up": TrendingUp, baby: Baby, "paw-print": PawPrint, shirt: Shirt,
  dumbbell: Dumbbell, coffee: Coffee, book: Book, wrench: Wrench, receipt: Receipt, landmark: Landmark,
  "hand-coins": HandCoins, "circle-dot": CircleDot,
};

export function CategoryIcon({ icon, type, className }: { icon: string; type?: TransactionType; className?: string }) {
  const Icon = CATEGORY_ICONS[icon] ?? CircleDot;
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-flex size-8 shrink-0 items-center justify-center rounded-lg",
        type === "INCOME" ? "bg-primary-soft text-primary-text" : "bg-sunken text-muted",
        className,
      )}
    >
      <Icon className="size-4" />
    </span>
  );
}
