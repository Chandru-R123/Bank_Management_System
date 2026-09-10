import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      // All /api/* requests → Spring Boot (port 8080)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // NOTE: Keycloak (port 8180) is called directly by keycloak-js from the
      // browser — no proxy needed.  The browser redirects to
      // http://localhost:8180 for login, then back to http://localhost:5173.
    },
  },
});
