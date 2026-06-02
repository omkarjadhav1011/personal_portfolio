import { Suspense } from "react";
import { Outlet, ScrollRestoration } from "react-router-dom";
import { MotionConfig } from "framer-motion";
import { Providers } from "@/components/layout/Providers";
import { RouteFallback } from "./RouteFallback";

/**
 * Root layout — replaces Next's app/layout.tsx. Wraps every route in the app
 * providers (theme init + toast), a MotionConfig that honours the OS
 * "reduce motion" setting, and a Suspense boundary, then renders the matched
 * route via <Outlet>. <ScrollRestoration> scrolls to top on navigation.
 */
export function RootLayout() {
  return (
    <MotionConfig reducedMotion="user">
      <Providers>
        <ScrollRestoration />
        <Suspense fallback={<RouteFallback />}>
          <Outlet />
        </Suspense>
      </Providers>
    </MotionConfig>
  );
}
