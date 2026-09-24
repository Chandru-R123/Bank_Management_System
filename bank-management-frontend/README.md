# State Bank — Frontend

Vite + TypeScript single-page app (no framework) for the Mini Banking / Open Banking
Consent Management System. It signs users in with Keycloak (PKCE) and calls the
Spring Boot API with the Keycloak JWT as a bearer token.

## Run

```bash
cp .env.example .env.local   # adjust URLs if needed
npm install
npm run dev                  # http://localhost:5173
npm run build                # type-check (tsc) + production build into dist/
```

In Docker the app is built into the NGINX gateway image and served at
`http://localhost:8080/`.

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_KEYCLOAK_URL` | `http://localhost:8080/auth` | Keycloak base URL |
| `VITE_API_BASE_URL` | `/api` | Backend API base URL |

During `npm run dev`, Vite proxies `/api` and `/auth` (see `vite.config.ts`).

## Screens by role

| Role | Screens |
|------|---------|
| ADMIN / EMPLOYEE / MAKER / CHECKER | Overview, Customers (list, create, edit, detail), Accounts (list, open, detail, statement, freeze / close), Transactions, Beneficiaries, Consents |
| CUSTOMER | My Accounts, Transactions, Beneficiaries (list, add, pay, delete), Connected apps (approve / reject / revoke consents), Profile |
| TPP | Open Banking portal (create consent requests, read shared accounts and transactions) |

Buttons appear only when the role is allowed. The backend enforces the same rules, so hiding a button is not the security boundary.

## Structure

```
src/
  main.ts              app shell, role-based router
  api.ts               typed API client (fetch + bearer token, error mapping)
  keycloak.ts          keycloak-js instance
  utils.ts             escaping, toasts, modals / drawers, formatting, CSV
  icons.ts             inline SVG icons
  components/
    money.ts           deposit / withdraw / transfer flows with receipts
    tx.ts              transaction table, statement drawer, CSV export
  pages/               one file per screen
```

## Error handling

Every API error returns `{ status, message }`. `api.ts` turns it into an `Error`
whose message is shown in a toast or next to the form field that caused it. A `401` response
sends the user back to the Keycloak sign-in page.
