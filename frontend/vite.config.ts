import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Mirrors the "@/..." import alias from the original Next.js app.
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
