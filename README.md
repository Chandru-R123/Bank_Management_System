# State Bank Management System

A full-stack banking application with role-based access control, secured by Keycloak, routed through NGINX.

---

## Architecture

```
                        ┌─────────────────────────────────────┐
                        │     Docker Network: bank-network     │
                        │                                      │
Browser                 │  ┌──────────┐   ┌──────────────┐   │
   │                    │  │ backend  │   │  keycloak    │   │
   │  http://           │  │ :8080    │   │  :8180       │   │
   │  localhost:8080    │  └────┬─────┘   └──────┬───────┘   │
   │                    │       │                 │           │
   ▼                    │  ┌────┴─────────────────┴───────┐   │
┌──────────┐  :8080     │  │         NGINX Gateway        │   │
│ Browser  │───────────►│  │  (React SPA + reverse proxy) │   │
└──────────┘            │  └──────────────────────────────┘   │
                        │                                      │
                        │  ┌──────────┐                       │
                        │  │ postgres │                       │
                        │  │ :5432    │                       │
                        │  └──────────┘                       │
                        └─────────────────────────────────────┘
```

### Routing Table

| Path | Routes To | Internal Service |
|------|-----------|-----------------|
| `http://localhost:8080/` | React SPA | Static files in NGINX |
| `http://localhost:8080/api/*` | Spring Boot | `backend:8080` |
| `http://localhost:8080/auth/*` | Keycloak | `keycloak:8180` |
| `http://localhost:8080/health` | NGINX health check | NGINX itself |

**Only port 8080 is exposed to the host.** All internal service ports are hidden inside the Docker network.

---

## Stack

| Service | Technology | Internal Port |
|---------|------------|---------------|
| NGINX Gateway | nginx:1.27-alpine | 8080 (public) |
| React Frontend | Vite + TypeScript | served by NGINX |
| Spring Boot API | Java 21 + Spring Boot | 8080 (internal) |
| Keycloak IAM | Keycloak 24.0.4 | 8180 (internal) |
| PostgreSQL | postgres:16-alpine | 5432 (internal) |

---

## Quick Start

### Prerequisites
- Docker Desktop running
- Git

### Start the full stack

```bash
cd "bank-management (1)"
docker compose up --build
```

Wait for all services to be ready (~2-3 minutes on first run):
```
bank-postgres  | database system is ready to accept connections
bank-keycloak  | Keycloak 24.0.4 on JVM ... started in ...s
bank-backend   | Started BankManagementApplication in ...s
bank-nginx     | [notice] start worker processes
```

Open **http://localhost:8080** in your browser.

### Subsequent runs (no rebuild)

```bash
docker compose up
```

### Stop

```bash
docker compose down          # stop, keep data
docker compose down -v       # stop, wipe all data (fresh start)
```

---

## First-Time Keycloak Setup

After `docker compose up --build` the realm and roles are auto-imported.  
You need to create users manually once (or re-run the setup script after `down -v`).

### Create users via Keycloak Admin Console

1. Open **http://localhost:8080/auth** → login `admin` / `admin`
2. Switch to realm **bank-management**
3. Create users with these credentials:

| Username | Password | Role | Name |
|----------|----------|------|------|
| `admin-user` | `Admin@1234` | `ADMIN` | Admin User |
| `rahul` | `Customer@1234` | `CUSTOMER` | Rahul Sharma |
| `priya` | `Customer@1234` | `CUSTOMER` | Priya Venkat |
| `arjun` | `Customer@1234` | `CUSTOMER` | Arjun Mehta |

For each user:
- Set **Email Verified** = ON
- Remove all **Required Actions**
- Assign the role under **Role Mappings → Assign Role**

### Link Keycloak UUIDs to customer records

After creating `rahul`, `priya`, `arjun` — copy their UUIDs from Keycloak admin console and run:

```sql
UPDATE customers SET keycloak_sub = '<rahul-uuid>'  WHERE email = 'rahul.sharma@statebank.com';
UPDATE customers SET keycloak_sub = '<priya-uuid>'  WHERE email = 'priya.venkat@statebank.com';
UPDATE customers SET keycloak_sub = '<arjun-uuid>'  WHERE email = 'arjun.mehta@statebank.com';
```

---

## Request Flow

```
Browser → http://localhost:8080/api/customers
              │
              ▼
         NGINX :8080
         location /api/
              │  proxy_pass http://backend/api/
              ▼
         Spring Boot :8080
         @PreAuthorize("hasRole('ADMIN')")
              │  validates JWT via Keycloak JWKS
              ▼
         PostgreSQL :5432
              │
              ▼
         JSON response → NGINX → Browser
```

### Authentication Flow (PKCE)

```
Browser → http://localhost:8080
keycloak-js detects no session
        │
        ▼
Browser redirect → http://localhost:8080/auth/realms/bank-management/...
        │  NGINX proxies → keycloak:8180
        ▼
Keycloak login page
User enters credentials
        │
        ▼
Keycloak issues JWT (realm_access.roles: [ADMIN|CUSTOMER|EMPLOYEE])
        │
        ▼
Browser stores token in memory (keycloak-js)
        │
        ▼
All API calls include  Authorization: Bearer <jwt>
        │  NGINX → Spring Boot validates JWT
        ▼
Role-based access enforced by @PreAuthorize
```

---

## API Endpoints

All endpoints accessible through NGINX at `http://localhost:8080/api/...`

### Authentication — Keycloak
```
POST /auth/realms/bank-management/protocol/openid-connect/token
```

### Customers (ADMIN only)
```
GET    /api/customers
GET    /api/customers/{id}
POST   /api/customers
PUT    /api/customers/{id}
DELETE /api/customers/{id}
```

### Customer Profile (CUSTOMER only)
```
GET    /api/customers/me
```

### Accounts
```
GET    /api/accounts          (ADMIN)
GET    /api/accounts/my       (CUSTOMER — own accounts only)
POST   /api/accounts          (ADMIN)
PUT    /api/accounts/{id}     (ADMIN)
DELETE /api/accounts/{id}     (ADMIN)
POST   /api/accounts/{id}/deposit   (ADMIN + CUSTOMER — owns account)
POST   /api/accounts/{id}/withdraw  (ADMIN + CUSTOMER — owns account)
POST   /api/accounts/transfer       (ADMIN + CUSTOMER — owns source account)
```

### Transactions
```
GET    /api/transactions              (ADMIN)
GET    /api/accounts/{id}/transactions  (ADMIN + CUSTOMER — owns account)
GET    /api/transactions/{id}           (ADMIN)
```

---

## NGINX Logs

```bash
# Access log (live tail)
docker exec bank-nginx tail -f /var/log/nginx/access.log

# Error log
docker exec bank-nginx tail -f /var/log/nginx/error.log

# All container logs
docker compose logs -f nginx
```

---

## Testing APIs

### Get a token

```powershell
$TOKEN = (Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/auth/realms/bank-management/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=bank-management-backend&username=admin-user&password=Admin@1234"
).access_token
```

### Test role enforcement

```powershell
# No token → 401
Invoke-RestMethod http://localhost:8080/api/customers

# ADMIN → 200
Invoke-RestMethod http://localhost:8080/api/customers -H @{Authorization="Bearer $TOKEN"}

# CUSTOMER → admin API → 403
$CUST_TOKEN = (Invoke-RestMethod -Method Post -Uri "..." -Body "...username=rahul...").access_token
Invoke-RestMethod http://localhost:8080/api/customers -H @{Authorization="Bearer $CUST_TOKEN"}
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `docker compose up` fails — port 8080 in use | Another service on 8080 | Stop it or change NGINX port in `docker-compose.yml` |
| Browser shows "Keycloak connection failed" | Stack not fully started | Wait for all 4 containers to show `Up` in `docker ps` |
| Login redirects but returns to blank page | `VITE_KEYCLOAK_URL` wrong | Rebuild: `docker compose up --build` |
| `GET /api/customers` returns 403 | Wrong role assigned in Keycloak | Reassign ADMIN role to admin-user |
| `GET /api/accounts/my` returns 404/empty | `keycloak_sub` not set | Run the SQL UPDATE (see First-Time Setup above) |
| Keycloak `proxy` deprecated warning | KC_PROXY is deprecated | Already fixed — using `KC_PROXY_HEADERS: xforwarded` |

### View all logs

```bash
docker compose logs -f            # all services
docker compose logs -f nginx      # NGINX only
docker compose logs -f backend    # Spring Boot only
docker compose logs -f keycloak   # Keycloak only
docker compose logs -f postgres   # PostgreSQL only
```

### Check container health

```bash
docker ps
```

---

## Development (without Docker)

Run each service locally:

**Terminal 1 — Keycloak**
```powershell
$env:PATH += ';C:\Users\Chandru\AppData\Local\Programs\DockerDesktop\resources\bin'
docker compose up keycloak postgres
```

**Terminal 2 — Spring Boot**
```powershell
cd bank-management
.\mvnw.cmd spring-boot:run
```

**Terminal 3 — Frontend (Vite dev server)**
```powershell
cd bank-management-frontend
npm run dev
```

Open **http://localhost:5173** — `.env.local` sets `VITE_KEYCLOAK_URL=http://localhost:8180` for direct Keycloak access.

---

## Credentials

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| Application | http://localhost:8080 | admin-user | Admin@1234 |
| Application | http://localhost:8080 | rahul | Customer@1234 |
| Keycloak Admin | http://localhost:8080/auth | admin | admin |
| PostgreSQL | localhost:5432 | postgres | bank@123 |
