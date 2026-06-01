import { Outlet } from "react-router-dom";
import { Navbar } from "@/components/layout/Navbar";
import { Footer } from "@/components/layout/Footer";
import { CommandPalette } from "@/components/layout/CommandPalette";
import { StatusBar } from "@/components/layout/StatusBar";
import { FloatingAIButton } from "@/components/ui/FloatingAIButton";

/**
 * Public site chrome — replaces Next's (main)/layout.tsx. The Next
 * dynamic(ssr:false) imports become plain imports (Vite SPA is client-only),
 * and {children} becomes <Outlet/>.
 */
export function MainLayout() {
  return (
    <>
      <Navbar />
      <CommandPalette />
      <main className="pb-7">
        <Outlet />
      </main>
      <Footer />
      <StatusBar />
      <FloatingAIButton />
    </>
  );
}
