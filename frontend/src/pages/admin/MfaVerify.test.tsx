import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import MfaVerify from "./MfaVerify";
import { useAuthStore } from "@/store/auth";

const apiFetchMock = vi.fn();
vi.mock("@/lib/api", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
  ApiError: class ApiError extends Error {},
  BASE_URL: "",
}));

function renderAt(entry: { pathname: string; state?: unknown }) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/admin/mfa/verify" element={<MfaVerify />} />
          <Route path="/admin" element={<div>ADMIN DASHBOARD</div>} />
          <Route path="/admin/login" element={<div>LOGIN PAGE</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("MfaVerify", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null });
    apiFetchMock.mockReset();
  });

  it("verifies the code, stores the ADMIN token, and redirects to /admin", async () => {
    apiFetchMock.mockResolvedValue({ token: "admin-jwt", expiresIn: 28800 });
    renderAt({ pathname: "/admin/mfa/verify", state: { preAuthToken: "pre-123" } });

    fireEvent.change(screen.getByPlaceholderText(/123456/), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: /verify/i }));

    expect(await screen.findByText("ADMIN DASHBOARD")).toBeTruthy();
    expect(useAuthStore.getState().token).toBe("admin-jwt");
    expect(apiFetchMock).toHaveBeenCalledWith(
      "/api/auth/mfa/verify",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer pre-123" }),
      }),
    );
  });

  it("redirects to login when there is no PRE_AUTH token (direct nav / refresh)", async () => {
    renderAt({ pathname: "/admin/mfa/verify" });

    expect(await screen.findByText("LOGIN PAGE")).toBeTruthy();
    expect(apiFetchMock).not.toHaveBeenCalled();
  });
});
