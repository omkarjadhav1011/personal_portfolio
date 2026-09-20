import { StrictMode } from "react";
import { createRoot, hydrateRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { AppProviders } from "@/providers/AppProviders";
import { router } from "@/router";
import "./styles/globals.css";

const container = document.getElementById("root")!;

const app = (
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>
);

// Prerendered pages arrive with real markup already in #root (see
// scripts/prerender.mjs), so they must be hydrated rather than re-rendered —
// createRoot would throw the server HTML away and repaint, which both wastes
// the work and causes a visible flash. The dev server ships an empty root, so
// fall back to a normal client render there.
if (container.hasChildNodes()) {
  hydrateRoot(container, app);
} else {
  createRoot(container).render(app);
}
