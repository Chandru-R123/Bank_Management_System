import Keycloak from 'keycloak-js';

/**
 * Single Keycloak instance shared across the whole app.
 *
 * In Docker/production the browser talks to Keycloak through the NGINX
 * gateway on port 8080 at the /auth/ path:
 *
 *   Browser → http://localhost:8080/auth/
 *          → NGINX → keycloak:8180/auth/
 *
 * For local Vite dev (npm run dev), Keycloak is still reachable directly
 * on port 8180 via the VITE_KEYCLOAK_URL env variable, which defaults to
 * the NGINX-proxied URL so it works in both environments without code changes.
 *
 * VITE_KEYCLOAK_URL is injected at build time:
 *   - Docker build:  http://localhost:8080/auth  (default — NGINX gateway)
 *   - Local dev:     http://localhost:8180        (set in .env.local)
 */
const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8080/auth';

const keycloak = new Keycloak({
  url:      keycloakUrl,
  realm:    'bank-management',
  clientId: 'bank-management-backend',
});

export default keycloak;
