
import { useEffect } from "react";
import { ToastProvider } from "@/components/admin/ToastProvider";
import { initTheme } from "@/store/theme";

export function Providers({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    initTheme();
  }, []);

  return <ToastProvider>{children}</ToastProvider>;
}
