import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { RequireAuth } from "./RequireAuth";
import { useAuthStore } from "@/store/auth";

function renderAdminAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/login" element={<div>LOGIN PAGE</div>} />
        <Route path="/admin" element={<RequireAuth />}>
          <Route index element={<div>ADMIN DASHBOARD</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("RequireAuth", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null });
  });

  it("bounces to /admin/login when there is no token", async () => {
    renderAdminAt("/admin");
    expect(await screen.findByText("LOGIN PAGE")).toBeTruthy();
    expect(screen.queryByText("ADMIN DASHBOARD")).toBeNull();
  });

  it("allows the admin route when a token is present", async () => {
    useAuthStore.setState({ token: "header.payload.signature" });
    renderAdminAt("/admin");
    expect(await screen.findByText("ADMIN DASHBOARD")).toBeTruthy();
  });
});
