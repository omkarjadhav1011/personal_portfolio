import { Suspense } from "react";
import { Outlet } from "react-router-dom";
import { Providers } from "@/components/layout/Providers";
import { RouteFallback } from "./RouteFallback";

/**
 * Root layout — replaces Next's app/layout.tsx. Wraps every route in the app
 * providers (theme init + toast) and a Suspense boundary, then renders the
 * matched route via <Outlet>. The QueryClient provider sits above the router
 * in main.tsx. Page chrome (Navbar/Footer/etc.) is assembled in a later phase.
 */
export function RootLayout() {
  return (
    <Providers>
      <Suspense fallback={<RouteFallback />}>
        <Outlet />
      </Suspense>
    </Providers>
  );
}
