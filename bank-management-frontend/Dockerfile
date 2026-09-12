# =============================================================================
#  Frontend — Multi-stage build
#  Stage 1: Build Vite/TypeScript app
#  Stage 2: Serve static files via NGINX
#            (the main NGINX gateway handles routing — this image is only
#             used as a static file source copied into the gateway image)
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies first (layer-cached unless package.json changes)
COPY package.json package-lock.json ./
RUN npm ci

# Copy source and build
COPY . .
RUN npm run build
# Output: /app/dist

# ── Stage 2: Serve ────────────────────────────────────────────────────────────
# We use a minimal nginx image just to hold the static files.
# The main gateway nginx.conf will serve them from /usr/share/nginx/html.
FROM nginx:1.27-alpine AS production

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Minimal nginx.conf just for the static-file container
# (the gateway NGINX is the one actually routing traffic)
RUN echo 'server { \
    listen 80; \
    root /usr/share/nginx/html; \
    index index.html; \
    location / { try_files $uri $uri/ /index.html; } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80
