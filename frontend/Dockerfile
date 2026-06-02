# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Cache node_modules layer separately from source
COPY package*.json ./
# npm install instead of ci: the Windows-generated lock file omits Linux-specific
# optional esbuild platform packages, so ci would fail on the Linux builder.
RUN npm install --ignore-scripts

COPY . .
# VITE_API_URL is intentionally empty: nginx proxies /api and /uploads to the
# backend container, so the browser uses same-origin relative paths.
RUN npm run build

# ── Stage 2: serve ───────────────────────────────────────────────────────────
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
