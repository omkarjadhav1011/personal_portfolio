import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import OAuthCallback from "./OAuthCallback";
import { useAuthStore } from "@/store/auth";

// Mock the API client so the exchange call is observable and never hits the network.
const apiFetchMock = vi.fn();
vi.mock("@/lib/api", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
  ApiError: class ApiError extends Error {},
  BASE_URL: "",
}));

function renderCallbackAt(path: string) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/admin/oauth/callback" element={<OAuthCallback />} />
          <Route path="/admin" element={<div>ADMIN DASHBOARD</div>} />
          <Route path="/admin/login" element={<div>LOGIN PAGE</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("OAuthCallback", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null });
    apiFetchMock.mockReset();
  });

  it("exchanges the code, stores the token, and redirects to /admin", async () => {
    apiFetchMock.mockResolvedValue({ token: "jwt-123", expiresIn: 28800 });
    renderCallbackAt("/admin/oauth/callback?code=abc123");

    expect(await screen.findByText("ADMIN DASHBOARD")).toBeTruthy();
    expect(useAuthStore.getState().token).toBe("jwt-123");
    expect(apiFetchMock).toHaveBeenCalledWith(
      "/api/auth/oauth/exchange",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("redirects to login with an error when the provider denied access (no exchange)", async () => {
    renderCallbackAt("/admin/oauth/callback?error=oauth_denied");

    expect(await screen.findByText("LOGIN PAGE")).toBeTruthy();
    expect(useAuthStore.getState().token).toBeNull();
    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("redirects to login when the exchange fails", async () => {
    apiFetchMock.mockRejectedValue(new Error("boom"));
    renderCallbackAt("/admin/oauth/callback?code=bad");

    expect(await screen.findByText("LOGIN PAGE")).toBeTruthy();
    expect(useAuthStore.getState().token).toBeNull();
  });
});
