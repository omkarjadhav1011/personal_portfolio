import { Navigate, Outlet } from "react-router-dom";
import { useAuthStore } from "@/store/auth";

/**
 * Route guard replacing Next's middleware.ts. Bounces unauthenticated visitors
 * to the login page; renders the matched admin route otherwise. (Token validity
 * is enforced server-side on each API call; this gates the UI on presence.)
 */
export function RequireAuth() {
  const token = useAuthStore((s) => s.token);
  if (!token) {
    return <Navigate to="/admin/login" replace />;
  }
  return <Outlet />;
}

/** Inverse guard: skip the login page when already authenticated. */
export function RedirectIfAuthed() {
  const token = useAuthStore((s) => s.token);
  if (token) {
    return <Navigate to="/admin" replace />;
  }
  return <Outlet />;
}
