import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Admin auth state. Per the migration's locked decision the JWT is held
 * client-side and sent as `Authorization: Bearer <token>` (no cookies).
 * Persisted to localStorage so a refresh keeps the admin signed in.
 */
interface AuthState {
  token: string | null;
  setToken: (token: string | null) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      setToken: (token) => set({ token }),
      clear: () => set({ token: null }),
    }),
    { name: "portfolio-auth" },
  ),
);

/** Non-React accessor so the API client can read the token outside components. */
export const getAuthToken = (): string | null => useAuthStore.getState().token;
