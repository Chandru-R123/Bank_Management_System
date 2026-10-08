/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Google reCAPTCHA v2 site key — public, baked into bundle at build time */
  readonly VITE_CAPTCHA_SITE_KEY: string;
  /** Keycloak public URL */
  readonly VITE_KEYCLOAK_URL: string;
  /** API base URL override */
  readonly VITE_API_BASE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
