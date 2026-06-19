import { create } from "zustand";

/**
 * Admin auth state. The JWT is held in MEMORY ONLY and sent as
 * `Authorization: Bearer <token>` (no cookies). It is intentionally NOT persisted
 * to localStorage/sessionStorage: persisted tokens are exfiltratable by any XSS on
 * the origin (CWE-922). The trade-off is that a full page refresh requires the admin
 * to log in again — acceptable for a single-admin panel.
 */
interface AuthState {
  token: string | null;
  setToken: (token: string | null) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  token: null,
  setToken: (token) => set({ token }),
  clear: () => set({ token: null }),
}));

/** Non-React accessor so the API client can read the token outside components. */
export const getAuthToken = (): string | null => useAuthStore.getState().token;

/** Clears the token (e.g. on a 401) so RequireAuth bounces to the login page. */
export const clearAuthToken = (): void => useAuthStore.getState().clear();
