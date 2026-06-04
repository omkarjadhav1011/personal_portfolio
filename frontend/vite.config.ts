/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";
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
  server: {
    // Dev: proxy API calls to the Spring Boot backend so the browser stays
    // same-origin (no CORS). Production sets VITE_API_URL to the backend URL
    // and the client calls it directly (CORS handled server-side).
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
      "/uploads": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
  build: {
    // Don't ship source maps publicly; raise the warning limit (vendor chunks
    // like framer-motion + radix are legitimately large for a portfolio).
    sourcemap: false,
    chunkSizeWarningLimit: 700,
  },
  test: {
    environment: "jsdom",
    globals: true,
  },
});
