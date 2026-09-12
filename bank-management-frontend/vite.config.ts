import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      // All /api/* requests → Spring Boot (local dev)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // /auth/* → Keycloak (local dev — mirrors the NGINX gateway path)
      // Only active when VITE_KEYCLOAK_URL is not set to direct Keycloak URL.
      // Kept here so the proxy path works consistently.
      '/auth': {
        target: 'http://localhost:8180',
        changeOrigin: true,
      },
    },
  },
  // Ensure VITE_KEYCLOAK_URL is available at build time for Docker
  // (set via docker-compose build args → passed as --build-arg)
  define: {},
});
