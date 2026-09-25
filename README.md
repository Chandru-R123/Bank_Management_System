# State Bank Management System

**Mini Banking / Open Banking Consent Management System**: a full-stack banking application with role-based access control. Keycloak handles authentication, and NGINX is the single entry point.

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
cd Bank_Management_System
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

## Keycloak Setup (automatic)

On first start, Keycloak imports `keycloak/bank-management-realm.json`. The import includes:

- realm roles `ADMIN`, `EMPLOYEE`, `MAKER`, `CHECKER`, `CUSTOMER`, `TPP`
- the SPA client `bank-management-backend` (PKCE)
- **user profile**: the registration page asks for **Mobile number** and **Address**, and both are required. A phone number must be 10–15 digits (optional `+` country code); an address must be 5–255 characters
- token mappers that put `phone` and `postal_address` into the JWT. The backend copies them into the customer record on first login (and the admin sync copies them for users who registered but never logged in)
- ready-to-use demo users:

| Username | Password | Roles | Use it to |
|----------|----------|-------|-----------|
| `admin-user` | `Admin@1234` | ADMIN | Do everything |
| `employee` | `Employee@1234` | EMPLOYEE | View all data, manage customer KYC |
| `maker` | `Maker@1234` | EMPLOYEE, MAKER | Post deposits / withdrawals / transfers on any account |
| `checker` | `Checker@1234` | EMPLOYEE, CHECKER | Delete beneficiaries, revoke consents |
| `rahul`, `priya`, `arjun` | `Customer@1234` | CUSTOMER | Online banking (linked to the seeded customers by email on first login) |
| `fintech-app` | `Tpp@12345` | TPP | Open Banking portal: request consents, read shared data |

New users can also self-register from the sign-in page ("Register"). They get the CUSTOMER role automatically.

### Already have a Keycloak volume from an earlier run?

Keycloak imports the realm **only when it does not exist yet**, so the new fields, roles and users won't appear on an existing volume. Pick one:

```bash
# Option A: re-import (wipes only Keycloak's data; bank data in Postgres is kept)
docker compose down
docker volume ls | grep keycloak_data          # find the exact name
docker volume rm <project>_keycloak_data
docker compose up --build

# Option B: fresh start of everything
docker compose down -v && docker compose up --build
```

Option C is to do it by hand in the Keycloak admin console (`http://localhost:8080/auth`, `admin` / `admin`). In the realm, go to **Realm settings → User profile → Create attribute** and add `phone` and `address`, each with *Required field* on and *Required for* set to *users*. Then add the MAKER / CHECKER / TPP realm roles.

Existing users who are missing phone/address are asked for them at their next login, because Keycloak's *Verify profile* action enforces required fields.

---

## Sample Data

On first start the backend seeds demo data (`SampleDataInitializer`). It runs only once and never blocks startup. Turn it off with `SEED_SAMPLE_DATA=false`.

| Customer | Login / password | Accounts | Highlights |
|----------|------------------|----------|-----------|
| Meera Nair | `meera` / `Customer@1234` | SB-2001-2025 | Salary, rent, transfers; 2 beneficiaries; 1 **authorised** + 1 **pending** consent |
| Karthik Subramanian | `karthik` / `Customer@1234` | SB-2002-2025, CA-2003-2025, SB-2004-2025 (**closed**) | Business current account; consent sharing 2 accounts |
| Divya Krishnan | `divya` / `Customer@1234` | SB-2005-2025, FD-2006-2025 (Fixed Deposit) | **Rejected** consent |
| Vikram Reddy | `vikram` / `Customer@1234` | CA-2007-2025 | **Revoked** consent |
| Fatima Sheikh | `fatima` / `Customer@1234` | SB-2008-2025 (**frozen**) | **Expired** consent |

It also adds:
- about 40 transactions spread over the last 30 days: opening deposits, deposits, withdrawals, transfers (both legs, with a shared reference) and one closure payout. Running balances are correct, which fills the dashboard cash-flow chart
- 8 beneficiaries, including 2 for `rahul`
- 7 consents from TPP apps (BudgetBuddy, LoanWise Credit, SaveSmart, TaxEase), all visible to the `fintech-app` login, plus a pending request for `rahul`

To see the sample data on an existing database, it seeds automatically as long as `meera.nair@example.com` doesn't exist yet. The sample **logins** need a fresh Keycloak realm import (see *Already have a Keycloak volume?*).

---

## Creating Logins from the Admin Dashboard

### Staff (Employee / Maker / Checker / Admin)
Only ADMIN users see the **Staff** page in the sidebar. There you can:
- **Add staff member**: name, username, work email, phone and roles. For the password, choose either
  - **Email a set-password link** (default): they get an email valid for 24 hours and choose their own password, or
  - **Set a temporary password**: share it with them; they must change it at first sign-in.
- Change roles, email a password reset link, set a temporary password, and disable or enable a login.
- You cannot remove your own ADMIN role or disable yourself.

Staff logins live only in Keycloak. The backend creates them through the Keycloak Admin API.

### Branch customers
Creating a customer on the admin page has **no password field, by design**. Leave
**"Create an online banking login"** ticked and the backend:
1. creates the customer's Keycloak login (username = email, role CUSTOMER)
2. links it to the customer record
3. emails them a **set-your-password** link

For customers created earlier, open the customer and click **Enable online banking**.
For customers who already have online banking, use **Send password reset email**.

After that, **Forgot Password?** on the login page works for them too. Before this, it silently did nothing, because a
branch customer had no Keycloak login to send the email to.

Emails go through Keycloak's SMTP settings. Locally they land in MailHog: **http://localhost:8080/mail/**

API:
```
GET  /api/admin/staff                              (ADMIN)
POST /api/admin/staff                              (ADMIN)  { username, firstName, lastName, email, phone, address?, roles[], password? }
PUT  /api/admin/staff/{id}/roles                   (ADMIN)  { roles[] }
POST /api/admin/staff/{id}/enable | /disable       (ADMIN)
POST /api/admin/staff/{id}/password-email          (ADMIN)
PUT  /api/admin/staff/{id}/password                (ADMIN)  { password }   temporary
POST /api/customers/{id}/online-banking            (ADMIN, EMPLOYEE)
POST /api/customers/{id}/online-banking/password-email (ADMIN, EMPLOYEE)
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

## Roles

| Role | Can do |
|------|--------|
| `ADMIN` | Everything, including open / edit / freeze / close accounts and delete customers |
| `EMPLOYEE` | View all customers, accounts, transactions, beneficiaries and consents; create / edit customers |
| `MAKER` | Post transactions (deposit / withdraw / transfer) on **any** account |
| `CHECKER` | Delete beneficiaries, revoke consents |
| `CUSTOMER` | Own accounts only: transactions, statements, beneficiaries, approve / reject / revoke consents, update own phone / address |
| `TPP` | Third-Party Provider: create consent requests and read the data customers share (`/api/open-banking/**`) |

These match the PDF's Week 4 example rules: `POST /api/accounts` needs ADMIN, `POST /api/transactions` needs MAKER (or a customer on their own account), and `DELETE /api/beneficiaries/{id}` needs ADMIN / CHECKER (or the customer who owns it).
Staff and TPP users also inherit CUSTOMER through Keycloak's default roles, but they are never synced as customers.

---

## Business Rules

| Rule | Detail |
|------|--------|
| Money precision | All amounts are `BigDecimal` with at most 2 decimal places |
| Concurrency | Every balance change locks the account row (`SELECT … FOR UPDATE`); transfers lock both rows in id order, so they can't deadlock |
| Audit trail | Every balance change writes a transaction with the balance after it, a reference ID, remarks and the Keycloak username of whoever did it |
| No silent edits | `PUT /api/accounts/{id}` can't change the balance or the account number |
| Account status | `ACTIVE` → `FROZEN` (no debits or credits) → `CLOSED` (the remaining balance is paid out as `CLOSURE_PAYOUT`; history is kept) |
| Savings minimum | Savings accounts must keep ₹1,000 (`bank.savings.minimum-balance`), including at opening |
| Fixed Deposit | Funded once at opening. No top-ups, withdrawals or outgoing transfers; the money is released when the deposit is closed |
| Customer limits | ₹10,00,000 per transaction (`bank.customer.max-transaction-amount`) and ₹2,00,000 of withdrawals + outgoing transfers per account per day (`bank.customer.daily-debit-limit`) |
| Opening deposit | Recorded as an `OPENING_DEPOSIT` transaction; the account number is generated automatically if left blank |
| Customer deletion | Only customers who never had an account can be deleted |
| Beneficiary check | `GET /api/accounts/lookup?number=` returns a masked holder name (e.g. "Priya V.") before a transfer |

All limits can be changed with environment variables. See `application.properties`.

---

## API Endpoints

All endpoints are reachable through NGINX at `http://localhost:8080`. Creating something returns **201**, a validation or business-rule error returns **400**, a missing token **401**, the wrong role **403**, and a missing record **404**.

### Health & info (public)
```
GET /health          NGINX gateway health
GET /api/health      Spring Boot + database connectivity
GET /api/info        service metadata
```

### Authentication — Keycloak
```
POST /auth/realms/bank-management/protocol/openid-connect/token
```

### Customers
```
GET    /api/customers                 (STAFF)
GET    /api/customers/{id}            (STAFF)
POST   /api/customers                 (ADMIN, EMPLOYEE)
PUT    /api/customers/{id}            (ADMIN, EMPLOYEE)
DELETE /api/customers/{id}            (ADMIN — only if the customer never had an account)
GET    /api/customers/me              (CUSTOMER)
PUT    /api/customers/me              (CUSTOMER — phone & address only)
POST   /api/auth/sync                 (CUSTOMER — link Keycloak login to customer record)
POST   /api/admin/sync-customers      (STAFF — pull Keycloak self-registrations)
```

### Accounts
```
GET    /api/accounts                  (STAFF)
GET    /api/accounts/{accountId}      (STAFF)
GET    /api/accounts/my               (CUSTOMER)
GET    /api/accounts/lookup?number=   (any bank user — beneficiary verification)
POST   /api/accounts                  (ADMIN — open account)
PUT    /api/accounts/{id}             (ADMIN — type / owner only)
POST   /api/accounts/{id}/freeze      (ADMIN)
POST   /api/accounts/{id}/unfreeze    (ADMIN)
POST   /api/accounts/{id}/close       (ADMIN — pays out remaining balance)
DELETE /api/accounts/{id}             (ADMIN — same as close; never hard-deleted)
POST   /api/accounts/{id}/deposit     (MAKER, ADMIN, owning CUSTOMER)  { amount, description? }
POST   /api/accounts/{id}/withdraw    (MAKER, ADMIN, owning CUSTOMER)  { amount, description? }
POST   /api/accounts/transfer         (MAKER, ADMIN, owning CUSTOMER)  { fromAccountId, toAccountId | toAccountNumber, amount, description? }
```

### Transactions
```
POST   /api/transactions                     (MAKER, ADMIN, owning CUSTOMER) { accountId, type: DEPOSIT|WITHDRAW, amount, description? }
POST   /api/accounts/{accountId}/transactions (MAKER, ADMIN, owning CUSTOMER) { type, amount, description? }
GET    /api/accounts/{accountId}/transactions (STAFF, or the owning CUSTOMER)
GET    /api/transactions                     (STAFF — newest first)
GET    /api/transactions/my                  (CUSTOMER)
GET    /api/transactions/{id}                (STAFF)
```

### Beneficiaries
```
POST   /api/beneficiaries        (CUSTOMER for self; ADMIN / EMPLOYEE with customerId)  { nickname, accountNumber, customerId? }
GET    /api/beneficiaries        (CUSTOMER: own · STAFF: all)
DELETE /api/beneficiaries/{id}   (ADMIN, CHECKER, or the owning CUSTOMER)
```
Rules: the payee must be an existing State Bank account that isn't closed. It can't be the customer's own account, and each account can be saved only once per customer.

### Open Banking consents
```
POST   /api/consents               (TPP, ADMIN)   { customerEmail, permissions[], purpose, validityDays?, tppName? }
GET    /api/consents               (STAFF: all · TPP: own requests · CUSTOMER: own)
GET    /api/consents/{id}
POST   /api/consents/{id}/approve  (CUSTOMER)     { accountIds[] }
POST   /api/consents/{id}/reject   (CUSTOMER)
POST   /api/consents/{id}/revoke   (CUSTOMER, the TPP, ADMIN, CHECKER)

GET    /api/open-banking/accounts                         (TPP)  header x-consent-id
GET    /api/open-banking/accounts/{accountId}/transactions (TPP)  header x-consent-id
```
Lifecycle: `AWAITING_AUTHORISATION → AUTHORISED → REVOKED`, or `→ REJECTED`. Any open consent becomes `EXPIRED` after `expiresAt`.
Permissions: `READ_ACCOUNTS` (always included), `READ_BALANCES`, `READ_TRANSACTIONS`. A TPP can read only the accounts the customer selected, only while the consent is authorised, and only with the permissions it was granted.

Errors always return `{ "status": …, "message": "…" }`.

---

## Postman

Import `postman/State-Bank.postman_collection.json` and run it with the **Collection Runner**, in folder order:

1. Health & info
2. Keycloak tokens for every role (saved as collection variables)
3. Security checks: 401 without a token, 403 with the wrong role
4. Customers → 5. Accounts → 6. Transactions → 7. Beneficiaries → 8. Open Banking consent flow

Each request has tests on the expected status code.

---

## NGINX Logs

NGINX generates an `X-Request-ID` for every `/api` call. It forwards the id to Spring Boot (which prints it on every log line as `[rid:…]`) and returns it to the client, so one request can be traced through both.

```bash
# Standard access log
docker exec bank-nginx tail -f /var/log/nginx/access.log

# Gateway log: request id, upstream, request / upstream time
docker exec bank-nginx tail -f /var/log/nginx/gateway.log

# Error log
docker exec bank-nginx tail -f /var/log/nginx/error.log

# Match a gateway line to backend logs
docker compose logs backend | grep "rid:<request-id>"
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
| `GET /api/accounts/my` returns 404/empty | Login not linked to a customer yet | Sign in once through the app (or call `POST /api/auth/sync`). The login is linked by email |
| Register page has no Mobile number / Address fields | Realm was imported before this change | Re-import the realm (see *Already have a Keycloak volume?*) |
| MAKER can't see the Deposit button / EMPLOYEE gets 403 on transactions | Working as designed | Money movements need the MAKER (or ADMIN) role |
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

---

## Training Program Checklist

| Week | Requirement | Where |
|------|-------------|-------|
| 1 | `GET /health`, `GET /api/info`, PostgreSQL connected | `InfoController`, `/api/health` checks the DB |
| 2 | Customer / Account / Transaction / Beneficiary CRUD, validation, error handling, Postman | `controller/`, `GlobalExceptionHandler`, `postman/` |
| 3 | Customer, account, transaction and beneficiary screens; API errors in the UI; env-based API URL | `bank-management-frontend/` (`VITE_API_BASE_URL`), frontend README |
| 4 | Keycloak realm, client, users and roles; JWT validation; role rules; 401 vs 403 | `keycloak/bank-management-realm.json`, `security/Roles.java` |
| 5 | NGINX as the single entry point, gateway headers, logs, Docker Compose for the full stack | `nginx/nginx.conf`, `docker-compose.yml` |
| 6 | Capstone: consent create / approve / reject, Open Banking data access | `ConsentService`, `/api/consents`, `/api/open-banking`, Connected apps + TPP portal screens |
