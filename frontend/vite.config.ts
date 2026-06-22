/// <reference types="vitest/config" />
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Backend target for the dev proxy. Defaults to localhost for host-run `npm run
// dev`; the docker dev container sets VITE_PROXY_TARGET=http://host.docker.internal:8081
// to reach the backend running on the host.
const apiTarget = process.env.VITE_PROXY_TARGET ?? "http://localhost:8081";
// In a container the host bind-mount needs filesystem polling for HMR to fire.
const usePolling = process.env.VITE_USE_POLLING === "1";

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
    // Bind 0.0.0.0 so the port is reachable from the host when containerized.
    host: true,
    watch: usePolling ? { usePolling: true } : undefined,
    // Dev: proxy API calls to the Spring Boot backend so the browser stays
    // same-origin (no CORS). Production sets VITE_API_URL to the backend URL
    // and the client calls it directly (CORS handled server-side).
    proxy: {
      "/api": {
        target: apiTarget,
        changeOrigin: true,
      },
      "/uploads": {
        target: apiTarget,
        changeOrigin: true,
      },
      // OAuth2 sign-in initiation + provider callback must reach the backend in dev
      // (BASE_URL is empty, so the buttons navigate to same-origin /oauth2/...).
      "/oauth2": {
        target: apiTarget,
        changeOrigin: true,
      },
      "/login/oauth2": {
        target: apiTarget,
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
