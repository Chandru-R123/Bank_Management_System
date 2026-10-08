/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Keycloak public URL */
  readonly VITE_KEYCLOAK_URL: string;
  /** API base URL override */
  readonly VITE_API_BASE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
