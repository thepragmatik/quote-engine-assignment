# ==============================================================================
# Frontend UI & Gateway — Multi-Stage Container Build (Node 20 -> Nginx Alpine)
# ==============================================================================
#
# Context & Rationale:
#   Builds the React/TypeScript/Vite Single Page Application (SPA) and serves it
#   via a hardened Nginx web server that acts as a same-origin reverse proxy for `/api/`.
#
# Architectural Controls:
#   1. Complete Elimination of Node.js Runtime:
#      The heavy Node.js environment, npm toolchains, and `node_modules/` reside
#      ONLY in the build stage. The final runtime image contains only static web
#      assets and Alpine Nginx (~25MB), eliminating entire classes of Node.js CVEs.
#   2. Non-Root Security (UID 10001):
#      Creates unprivileged user `appuser` (UID 10001), moves all Nginx cache, PID,
#      and temp directories to writable user paths, and drops root privileges.
# ==============================================================================

# Stage 1: Build & Bundle Static SPA Assets
FROM node:20-bookworm AS build
WORKDIR /app

# Cache package dependencies
COPY package*.json ./
RUN npm ci

# Compile TypeScript and bundle production assets with Vite
COPY . .
RUN npm run build

# Stage 2: Hardened Nginx Runtime
FROM nginx:1.27-alpine

# Provision non-root appuser and prepare unprivileged Nginx directories
RUN adduser -u 10001 -D appuser \
 && mkdir -p /var/cache/nginx /var/run /tmp/nginx \
 && chown -R appuser /var/cache/nginx /var/run /tmp/nginx

# Drop root privileges
USER 10001

# Copy compiled static assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Deploy custom Nginx server and master process configurations
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-main.conf /etc/nginx/nginx.conf

# Declare unprivileged gateway port
EXPOSE 3000
