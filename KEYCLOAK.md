# Keycloak Integration — State Bank Management System

## Architecture overview

```
Browser (Vite :5173)
  │
  │  1. keycloak-js redirects to Keycloak login page
  ▼
Keycloak (:8180)   ← Docker
  │
  │  2. Issues signed JWT (realm_access.roles inside)
  ▼
Browser
  │
  │  3. Bearer <JWT> on every API call
  ▼
Spring Boot (:8080)
  │
  │  4. NimbusJwtDecoder validates signature via JWKS URI
  │  5. KeycloakRoleConverter maps realm_access.roles → ROLE_*
  │  6. @PreAuthorize("hasRole('ADMIN')") etc. enforced
  ▼
PostgreSQL (:5432)
```

Passwords are stored **only** in Keycloak. PostgreSQL holds customer data and
the `keycloak_sub` column that links a Customer row to its Keycloak user UUID.

---

## 1 — Start Keycloak

### Prerequisites
- Docker Desktop running

### Start

```powershell
# From the project root (bank-management (1)/)
docker compose up -d
```

Keycloak will:
- Start on **http://localhost:8180**
- Auto-import `keycloak/bank-management-realm.json` on first start
- Create the realm, client, and roles automatically

Admin console: **http://localhost:8180** → username `admin` password `admin`

### Stop / restart

```powershell
docker compose down          # stop (data preserved in named volume)
docker compose down -v       # stop AND wipe all Keycloak data (fresh start)
```

---

## 2 — Realm is already configured

The import file creates:

| Item | Value |
|------|-------|
| Realm | `bank-management` |
| Client | `bank-management-backend` |
| Client type | Public (PKCE S256) |
| Redirect URIs | `http://localhost:5173/*`, `http://localhost:4173/*` |
| Realm roles | `ADMIN`, `EMPLOYEE`, `CUSTOMER` |

You do **not** need to create these manually.

---

## 3 — Create test users

Open **http://localhost:8180** → Administration Console → realm `bank-management`.

### 3a — Admin user

1. Left menu → **Users** → **Add user**
2. Username: `admin-user`  Email: `admin@statebank.com`  → **Create**
3. Tab **Credentials** → Set password `Admin@1234` → turn off *Temporary*
4. Tab **Role mappings** → **Assign role** → filter by realm → select **ADMIN**

### 3b — Customer users (Rahul, Priya, Arjun)

Repeat for each:

| Username | Email | Password | Role |
|----------|-------|----------|------|
| rahul | rahul.sharma@statebank.com | Customer@1234 | CUSTOMER |
| priya | priya.venkat@statebank.com | Customer@1234 | CUSTOMER |
| arjun | arjun.mehta@statebank.com  | Customer@1234 | CUSTOMER |

### 3c — Employee user

| Username | Email | Password | Role |
|----------|-------|----------|------|
| emp1 | emp1@statebank.com | Employee@1234 | EMPLOYEE |

---

## 4 — Link Keycloak UUIDs to Customer rows

After creating the customer users, copy their Keycloak UUID and update the
`keycloak_sub` column in PostgreSQL so `GET /api/customers/me` and account
ownership checks work.

### Get the UUID

Keycloak admin console → **Users** → click the user → copy the **ID** field
(format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).

### Update the database

```sql
UPDATE customers
SET keycloak_sub = 'PASTE-RAHUL-UUID-HERE'
WHERE email = 'rahul.sharma@statebank.com';

UPDATE customers
SET keycloak_sub = 'PASTE-PRIYA-UUID-HERE'
WHERE email = 'priya.venkat@statebank.com';

UPDATE customers
SET keycloak_sub = 'PASTE-ARJUN-UUID-HERE'
WHERE email = 'arjun.mehta@statebank.com';
```

> **Tip:** For new customers registering themselves, the backend can store the
> `sub` from the JWT at first login. The current flow requires admin to link
> them — a self-service endpoint can be added later.

---

## 5 — Run Spring Boot

Keycloak must be running first (Spring Boot fetches the JWKS on startup).

```powershell
cd bank-management
.\mvnw.cmd spring-boot:run
```

Spring Boot starts on **http://localhost:8080**.

---

## 6 — Run the frontend

```powershell
cd bank-management-frontend
npm run dev
```

Open **http://localhost:5173** — you are immediately redirected to Keycloak
login. After login, Keycloak redirects back and the app loads based on your
role.

| Role | Landing page |
|------|-------------|
| ADMIN | Dashboard (customers, accounts, transactions) |
| EMPLOYEE | Dashboard (customers, accounts, transactions) |
| CUSTOMER | My Accounts |

---

## 7 — API testing with curl

### Get a token

```powershell
$TOKEN = (Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8180/realms/bank-management/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=bank-management-backend&username=admin-user&password=Admin@1234" `
).access_token

echo $TOKEN
```

> The `password` grant is enabled on the client for testing. Use PKCE in
> production browsers.

---

## 8 — Security test matrix

### Test 1 — No token → 401

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/customers" -Method Get
# Expected: 401 Unauthorized
```

### Test 2 — Valid CUSTOMER token → allowed customer APIs

```powershell
$TOKEN = (Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8180/realms/bank-management/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=bank-management-backend&username=rahul&password=Customer@1234" `
).access_token

# My accounts — allowed
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/accounts/my" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
# Expected: 200 with Rahul's accounts
```

### Test 3 — CUSTOMER → ADMIN API → 403

```powershell
# Use the CUSTOMER token from Test 2
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/customers" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
# Expected: 403 Forbidden
```

### Test 4 — ADMIN token → admin API → allowed

```powershell
$ADMIN_TOKEN = (Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8180/realms/bank-management/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=bank-management-backend&username=admin-user&password=Admin@1234" `
).access_token

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/customers" `
  -Headers @{ Authorization = "Bearer $ADMIN_TOKEN" }
# Expected: 200 with full customer list
```

### Test 5 — Customer accessing another customer's account → 403

```powershell
# Use Rahul's CUSTOMER token
# Try to access Priya's account (accountId = 2)
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/accounts/2/transactions" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
# Expected: 403 Forbidden
```

---

## 9 — Token anatomy

A decoded Keycloak access token looks like:

```json
{
  "sub": "a1b2c3d4-...",          ← stored in customers.keycloak_sub
  "preferred_username": "rahul",
  "email": "rahul.sharma@statebank.com",
  "realm_access": {
    "roles": ["CUSTOMER", "default-roles-bank-management", "offline_access"]
  },
  "exp": 1234567890,
  "iss": "http://localhost:8180/realms/bank-management"
}
```

`KeycloakRoleConverter` reads `realm_access.roles`, strips the built-in
Keycloak roles, and maps the remainder to `ROLE_CUSTOMER`, `ROLE_ADMIN`, etc.

---

## 10 — Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Spring Boot fails to start with `Connection refused` | Keycloak not running | `docker compose up -d` first |
| `401` on every request | Token issued by wrong realm/client | Check `issuer-uri` in `application.properties` |
| `403` for admin user | Role not assigned in Keycloak | Assign `ADMIN` role in Keycloak admin console |
| `GET /api/customers/me` returns 404 | `keycloak_sub` not set in DB | Run the SQL UPDATE in section 4 |
| Keycloak login page shows blank | Browser blocks `localhost:8180` iframe | Disable `checkLoginIframe` (already done in `main.ts`) |
| Token expired mid-session | `updateToken` call failed | Page will auto-redirect to Keycloak login |
