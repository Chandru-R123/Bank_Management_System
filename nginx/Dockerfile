# =============================================================================
#  NGINX Gateway — builds frontend and embeds static files
#
#  Stage 1: Build React/Vite app
#  Stage 2: NGINX with static files + gateway config
# =============================================================================

# ── Stage 1: Build frontend ───────────────────────────────────────────────────
FROM node:20-alpine AS frontend-builder

WORKDIR /app

COPY bank-management-frontend/package.json bank-management-frontend/package-lock.json ./
RUN npm ci

COPY bank-management-frontend/ .

# Build with the Docker-time Keycloak URL (through NGINX /auth/)
ARG VITE_KEYCLOAK_URL=http://localhost:8080/auth
ENV VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL}

RUN npm run build
# Output: /app/dist

# ── Stage 2: NGINX with static files + gateway config ────────────────────────
FROM nginx:1.27-alpine AS production

# Copy gateway NGINX config
COPY nginx/nginx.conf /etc/nginx/nginx.conf

# Copy built frontend into NGINX document root
COPY --from=frontend-builder /app/dist /usr/share/nginx/html

# Expose the single public port
EXPOSE 8080
