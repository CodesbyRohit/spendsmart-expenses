import { useEffect } from "react";
import type { Theme } from "@/types/api";

/** Applies the theme preference to <html>. SYSTEM follows the OS setting live. */
export function useApplyTheme(theme: Theme | undefined) {
  useEffect(() => {
    const root = document.documentElement;
    const media = window.matchMedia?.("(prefers-color-scheme: dark)");
    const apply = () => {
      const dark = theme === "DARK" || ((theme === "SYSTEM" || !theme) && Boolean(media?.matches));
      root.classList.toggle("dark", dark);
    };
    apply();
    media?.addEventListener?.("change", apply);
    return () => media?.removeEventListener?.("change", apply);
  }, [theme]);
}
