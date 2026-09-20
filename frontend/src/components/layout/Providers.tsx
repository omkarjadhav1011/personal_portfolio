
import { ToastProvider } from "@/components/admin/ToastProvider";

export function Providers({ children }: { children: React.ReactNode }) {
  return <ToastProvider>{children}</ToastProvider>;
}
