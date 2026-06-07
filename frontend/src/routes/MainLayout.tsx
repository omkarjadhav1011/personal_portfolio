import { Suspense } from "react";
import { Outlet } from "react-router-dom";
import { Navbar } from "@/components/layout/Navbar";
import { Footer } from "@/components/layout/Footer";
import { CommandPalette } from "@/components/layout/CommandPalette";
import { StatusBar } from "@/components/layout/StatusBar";
// AI assistant — commented out for future use
// import { FloatingAIButton } from "@/components/ui/FloatingAIButton";
import { RouteFallback } from "./RouteFallback";

/**
 * Public site chrome — replaces Next's (main)/layout.tsx. The Next
 * dynamic(ssr:false) imports become plain imports (Vite SPA is client-only),
 * and {children} becomes <Outlet/>. The inner Suspense keeps the chrome
 * mounted while a lazy page chunk loads.
 */
export function MainLayout() {
  return (
    <>
      <Navbar />
      <CommandPalette />
      <main className="pb-7">
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
      </main>
      <Footer />
      <StatusBar />
      {/* AI assistant — commented out for future use */}
      {/* <FloatingAIButton /> */}
    </>
  );
}
