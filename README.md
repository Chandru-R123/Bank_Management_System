# State Bank Management System

A full-stack **Mini Banking + Open Banking Consent Management** system demonstrating:

- Role-based access control (ADMIN / EMPLOYEE / MAKER / CHECKER / CUSTOMER / TPP)
- **Maker–Checker dual-control workflow** for staff financial operations
- Manual KYC document review with a real DigiLocker abstraction layer
- Open Banking consent management (UK-style account information)
- Centralised audit logging
- CAPTCHA-protected customer registration
- Keycloak 24 OAuth2/OIDC with PKCE

---

## Architecture

```
Browser
  │
  └─▶ NGINX :8080 (single public entry point)
        ├─ /          → React SPA (Vite + TypeScript, no framework)
        ├─ /api/*     → Spring Boot :8080
        ├─ /auth/*    → Keycloak :8180
        ├─ /mail/     → MailHog (dev email capture)
        └─ /health    → NGINX liveness

Spring Boot ──▶ PostgreSQL 16 (bankdb)
Spring Boot ──▶ Keycloak (Admin REST API — staff management)
Spring Boot ──▶ DigiLocker API (when KYC_PROVIDER=DIGILOCKER)
Spring Boot ──▶ reCAPTCHA/hCaptcha (when CAPTCHA_ENABLED=true)
```

**Only port 8080 is exposed to the host.** All internal service-to-service communication is over the `bank-network` Docker bridge.

---

## Quick Start

```bash
git clone <repo>
cd Bank_Management_System

# First run (or after code changes)
docker compose up --build

# Open in browser
open http://localhost:8080

# Stop (data preserved in volumes)
docker compose down

# Stop and wipe all data
docker compose down -v
```

Demo credentials — all users have been pre-configured in Keycloak:

| Role     | Username       | Password        |
|----------|----------------|-----------------|
| ADMIN    | admin-user     | Admin@1234      |
| EMPLOYEE | employee-demo  | Employee@1234   |
| MAKER    | maker-demo     | Maker@1234      |
| CHECKER  | checker-demo   | Checker@1234    |
| CUSTOMER | meera / karthik / divya / vikram / fatima | Customer@1234 |
| TPP      | fintech-app    | Tpp@1234        |

> **MailHog** (dev SMTP) is at `http://localhost:8080/mail/` — captures all emails from Keycloak.

---

## Roles

| Role     | Capabilities |
|----------|-------------|
| **ADMIN** | Full system access. Staff management, customer management, account lifecycle, freeze/unfreeze/close, KYC review. Direct deposit/withdraw/transfer without Maker-Checker queue. |
| **EMPLOYEE** | Customer management, KYC review (approve/reject). View-only on accounts/transactions. Cannot approve Maker requests. |
| **MAKER** | Creates financial transaction requests (DEPOSIT / WITHDRAW / TRANSFER) which go into a pending queue. Cannot approve own requests. |
| **CHECKER** | Approves or rejects pending Maker requests. Cannot approve requests they created. Cannot self-approve. |
| **CUSTOMER** | Own accounts, direct deposit/withdraw/transfer (with limits). Beneficiaries, Open Banking consents, KYC, profile. |
| **TPP** | Open Banking only — consent-gated read access to customer accounts/transactions. No KYC, no financial operations. |

---

## Maker–Checker Workflow

```
MAKER → POST /api/transaction-requests
           │
           ▼ status = PENDING_APPROVAL  (balance unchanged)
           │
    ┌──────┴──────┐
    │             │
CHECKER      CHECKER
approves     rejects
    │             │
    ▼             ▼
APPROVED      REJECTED
    │
    ▼
PROCESSING
    │
  ┌─┴─┐
  │   │
SUCCESS FAILED
```

**Invariants enforced by the backend (not the frontend):**

1. Creating a request **never changes any account balance**.
2. Balance changes only on transition `PROCESSING → SUCCESS`.
3. `makerUserId != checkerUserId` — self-approval is a 400 error.
4. EMPLOYEE, CUSTOMER, and TPP cannot act as CHECKER.
5. A pessimistic write lock on the request row prevents duplicate concurrent approval.
6. An optimistic version field prevents stale state overwrites.
7. All account business rules (frozen/closed, FD restrictions, minimum balance, daily limits) are re-validated at execution time.

**Request statuses:**

| Status | Meaning |
|--------|---------|
| `PENDING_APPROVAL` | Created by MAKER, awaiting CHECKER |
| `APPROVED` | CHECKER approved (transitional — immediate execution) |
| `PROCESSING` | Execution in-flight (concurrency guard) |
| `SUCCESS` | Executed — balances changed |
| `REJECTED` | CHECKER rejected with reason |
| `FAILED` | Approved but execution failed (e.g., account frozen between approval and execution) |
| `CANCELLED` | MAKER cancelled before CHECKER acted |
| `REVERSED` | Successfully executed, later reversed by ADMIN |

---

## Customer Banking Flow

Customers retain **full direct banking capability**:

- Deposit to own account
- Withdraw from own account (savings min ₹1,000; daily limit ₹2,00,000)
- Transfer to any account (by number or by saved beneficiary)
- View own transactions and download statements
- Manage saved beneficiaries
- Approve/reject Open Banking consents
- Submit KYC documents

Customer limits:

| Limit | Value |
|-------|-------|
| Max single transaction | ₹10,00,000 |
| Daily debit limit (per account) | ₹2,00,000 |
| Savings minimum balance | ₹1,000 |
| Fixed Deposit | No deposits or withdrawals; funds released on closure |

---

## API Reference

### Base URL
```
http://localhost:8080/api
```
All endpoints except `/health`, `/info`, and `/captcha/config` require a Bearer token.

### Accounts
```
GET    /accounts                    STAFF — all accounts
GET    /accounts/my                 CUSTOMER — own accounts
GET    /accounts/{id}               STAFF
GET    /accounts/lookup?number=     Any role — masked beneficiary lookup
POST   /accounts                    ADMIN — open account
PUT    /accounts/{id}               ADMIN — update type/owner
POST   /accounts/{id}/close         ADMIN
POST   /accounts/{id}/freeze        ADMIN
POST   /accounts/{id}/unfreeze      ADMIN
POST   /accounts/{id}/deposit       CUSTOMER (direct) | MAKER (→ request queue) | ADMIN (direct)
POST   /accounts/{id}/withdraw      CUSTOMER (direct) | MAKER (→ request queue) | ADMIN (direct)
POST   /accounts/transfer           CUSTOMER (direct) | MAKER (→ request queue) | ADMIN (direct)
```

### Maker–Checker Requests
```
POST   /transaction-requests              MAKER/ADMIN — create request
GET    /transaction-requests/my           MAKER — own requests
GET    /transaction-requests/pending      CHECKER/ADMIN — PENDING_APPROVAL only
GET    /transaction-requests              CHECKER/ADMIN — all
GET    /transaction-requests/{id}         MAKER (own) / CHECKER / ADMIN
POST   /transaction-requests/{id}/approve CHECKER/ADMIN — approve + execute
POST   /transaction-requests/{id}/reject  CHECKER/ADMIN — reject with reason
POST   /transaction-requests/{id}/cancel  MAKER (own) / ADMIN — cancel pending
```

### KYC
```
POST   /kyc/start                          CUSTOMER — start KYC
POST   /kyc/documents                      CUSTOMER — upload document (multipart)
POST   /kyc/submit                         CUSTOMER — submit for review
GET    /kyc/my                             CUSTOMER — own KYC status
GET    /kyc/pending                        EMPLOYEE/ADMIN — pending submissions
GET    /kyc/customer/{id}                  EMPLOYEE/ADMIN — customer's KYC
GET    /kyc/documents/{id}/download        EMPLOYEE/ADMIN — secure document stream
POST   /kyc/customer/{id}/approve          EMPLOYEE/ADMIN — approve KYC
POST   /kyc/customer/{id}/reject           EMPLOYEE/ADMIN — reject with reason
```

### Audit Log
```
GET    /audit?page=0&size=50               STAFF — paginated entries
GET    /audit/actor/{userId}               STAFF — by actor
GET    /audit/resource/{type}/{id}         STAFF — by resource
```

### Open Banking
```
GET    /consents                           Scoped by role
POST   /consents                           TPP/ADMIN — create consent request
POST   /consents/{id}/approve              CUSTOMER — approve with accountIds
POST   /consents/{id}/reject               CUSTOMER — reject
POST   /consents/{id}/revoke               CUSTOMER/TPP/ADMIN/CHECKER — revoke
GET    /open-banking/accounts              TPP — shared accounts (x-consent-id header)
GET    /open-banking/accounts/{id}/transactions  TPP — shared transactions
```

### Customers
```
GET    /customers                          STAFF
GET    /customers/me                       CUSTOMER
PUT    /customers/me                       CUSTOMER — phone/address only
POST   /customers                          ADMIN/EMPLOYEE
PUT    /customers/{id}                     ADMIN/EMPLOYEE
DELETE /customers/{id}                     ADMIN
POST   /customers/{id}/online-banking      ADMIN/EMPLOYEE — enable Keycloak login
```

### CAPTCHA
```
GET    /captcha/config                     Public — returns { enabled, siteKey }
POST   /captcha/verify                     Authenticated — validates token
```

---

## KYC Verification

### Manual KYC (default: `KYC_PROVIDER=MANUAL`)

1. Customer starts KYC → uploads IDENTITY, ADDRESS, PHOTOGRAPH documents
2. Customer submits → status `PENDING`
3. Employee/ADMIN reviews documents via `/api/kyc/pending`
4. Employee approves → `VERIFIED` | rejects with reason → `REJECTED`
5. Customer can resubmit after rejection

**Customer cannot self-verify.** VERIFIED status is set only by staff.

Documents are stored in a persistent volume (`kyc_uploads`) outside the container image. TPP has no access to KYC records.

### DigiLocker Integration (`KYC_PROVIDER=DIGILOCKER`)

Real government DigiLocker OAuth2 integration. Requires official API partner registration.

Required environment variables:
```
DIGILOCKER_CLIENT_ID
DIGILOCKER_CLIENT_SECRET    # NEVER committed — set as environment variable
DIGILOCKER_REDIRECT_URI
```

**Important:** `DigiLocker Verified` is shown ONLY after a successful DigiLocker API callback. The system fails loudly if credentials are missing — it does not silently fall back to Mock.

### Mock KYC (`KYC_PROVIDER=MOCK`)

Development/testing stub only. Auto-verifies without documents. Clearly labelled `[DEV] Mock Verification` in all UIs and audit logs. **Must not be used in production.**

---

## CAPTCHA

Supports Google reCAPTCHA v2/v3 and hCaptcha.

```
CAPTCHA_ENABLED=true
CAPTCHA_SITE_KEY=<your-public-key>    # Frontend only
CAPTCHA_SECRET_KEY=<your-secret-key>  # NEVER committed
```

When `CAPTCHA_ENABLED=false` (default), validation is skipped — safe for development and testing.

---

## Open Banking

Consent lifecycle:
```
AWAITING_AUTHORISATION → AUTHORISED → REVOKED
                       → REJECTED
                       → EXPIRED (lazy, on first read after expiry)
```

Permissions: `READ_ACCOUNTS`, `READ_BALANCES`, `READ_TRANSACTIONS`

TPP access is denied when:
- Consent does not exist
- Consent is expired, revoked, or rejected
- Required permission was not granted
- Account is not in the consent's shared set

---

## Audit Log

Every significant action writes an entry to the `audit_logs` table. Entries are append-only and never deleted.

Fields: `id`, `action`, `actorUserId`, `actorUsername`, `actorRole`, `resourceType`, `resourceId`, `status`, `remarks`, `timestamp`, `requestId` (Maker-Checker ref), `httpRequestId` (gateway X-Request-ID correlation).

**Never stored in audit:** passwords, OTPs, access tokens, refresh tokens, CVV, client secrets, DigiLocker secrets, document file contents.

---

## Docker & Persistence

```yaml
# Data volumes (persist through docker compose down)
postgres_data   # All banking data
keycloak_data   # Keycloak configuration
kyc_uploads     # KYC documents (mounted at /app/kyc-uploads in backend)
```

**Do not use `docker compose down -v`** in production — this deletes all banking data.

The backend is stateless. Restarting it does not affect PostgreSQL data.

Schema is managed by `spring.jpa.hibernate.ddl-auto=update` — Hibernate safely adds columns on startup.

---

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `SEED_SAMPLE_DATA` | `true` | Seed demo customers/accounts/transactions on first start |
| `BACKFILL_CUSTOMER_LOGINS` | `true` | At startup, give every customer with an email a Keycloak login so "Forgot password" can email them |
| `KYC_PROVIDER` | `MANUAL` | `MANUAL` \| `DIGILOCKER` \| `MOCK` |
| `KYC_UPLOAD_DIR` | `./kyc-uploads` | Document storage path |
| `DIGILOCKER_CLIENT_ID` | — | DigiLocker OAuth client id |
| `DIGILOCKER_CLIENT_SECRET` | — | **Secret** — never commit |
| `DIGILOCKER_REDIRECT_URI` | — | DigiLocker OAuth callback URL |
| `CAPTCHA_ENABLED` | `false` | Enable CAPTCHA validation |
| `CAPTCHA_SITE_KEY` | — | Public key (frontend widget) |
| `CAPTCHA_SECRET_KEY` | — | **Secret** — never commit |
| `CAPTCHA_MIN_SCORE` | `0.5` | reCAPTCHA v3 minimum score |
| `BANK_SAVINGS_MIN_BALANCE` | `1000` | Savings account minimum balance (₹) |
| `BANK_MAX_TXN_AMOUNT` | `1000000` | Customer max single transaction (₹) |
| `BANK_DAILY_DEBIT_LIMIT` | `200000` | Customer daily debit limit per account (₹) |

---

## Testing

### Backend

```bash
# Run tests (uses H2 in-memory database — no PostgreSQL needed)
./mvnw test
```

Test coverage includes:
- JWT validation (valid, invalid, expired)
- Role-based 401/403 responses
- Customer ownership enforcement
- Maker request creation (balance unchanged)
- Checker approval (balance changes once)
- Self-approval rejection
- Employee/Customer cannot approve
- Duplicate approval rejection
- Rejected request cannot execute
- Financial limits and business rules
- Frozen/closed account restrictions
- KYC self-verify prevention
- Concurrent approval safety

### Postman

Import `postman/State-Bank.postman_collection.json` into Postman.

1. Set environment variable `baseUrl = http://localhost:8080`
2. Run folder **02 Auth — Get Tokens** first to populate tokens
3. Run remaining folders in order using the Collection Runner
4. Folder **06 Maker–Checker Workflow** covers the full approval lifecycle

---

## Project Structure

```
├── src/main/java/com/chandru/bankmanagement/
│   ├── config/          # Security, data initialisation
│   ├── controller/      # REST endpoints
│   ├── dto/             # Request/response DTOs
│   ├── entity/          # JPA entities
│   ├── exception/       # Domain exceptions + global handler
│   ├── repository/      # Spring Data repositories
│   ├── security/        # Actor, Roles, SecurityUtils, KeycloakRoleConverter
│   └── service/         # Business logic
│       └── kyc/         # KYC provider abstraction
├── bank-management-frontend/
│   └── src/
│       ├── pages/       # Dashboard, accounts, requests, approvals, kyc, audit, …
│       ├── components/  # money.ts, tx.ts
│       ├── api.ts       # Typed API client
│       ├── main.ts      # Router + shell
│       └── utils.ts     # UI helpers
├── keycloak/            # Realm import JSON
├── nginx/               # Gateway config + Dockerfile
├── postman/             # API test collection
└── docker-compose.yml
```

---

## Known Limitations

1. **DigiLocker** integration is structurally complete (OAuth2 URL construction, callback handler) but requires official API partner credentials from the Government of India. The TODO markers in `DigiLockerKycVerificationService.java` must be completed with the bank's issued credentials.

2. **CAPTCHA** requires registration with Google reCAPTCHA or hCaptcha to obtain keys. `CAPTCHA_ENABLED=false` by default for local development.

3. **KYC document storage** uses the local filesystem in the container volume. For production, migrate `storeFile()` in `KycService.java` to Azure Blob Storage or AWS S3 using the existing `storageReference` abstraction.

4. **No Flyway/Liquibase migrations** — schema is managed by `ddl-auto=update`. Suitable for development; production deployments should adopt proper migration tooling.

5. **Password/MFA** is fully delegated to Keycloak. Forgot password, password reset, MFA, session management, and device tracking are handled by the Keycloak admin console and the account portal at `/auth/realms/bank-management/account`.

   Keycloak's **Forgot password** only emails users that exist in Keycloak (and shows the same success message when nobody matches). So every customer gets a login: new branch customers get one when they are created, and `CustomerLoginBackfill` links or creates logins for existing customers at startup (no password, so they set it through Forgot password; demo customers get `Customer@1234`). Changing a customer's email also changes their login email. Check the backend log for `Login backfill: N logins created, N linked, N skipped`.

---

## Security Notes

- The backend is the **final security boundary** — every endpoint validates JWT, role, ownership, and business rules independently of the frontend.
- Client-supplied `customerId`, `ownerId`, `makerId`, `checkerId`, and role values are **never trusted** — all identity is extracted from the JWT.
- KYC documents are never publicly accessible — download requires staff authentication.
- Audit logs never contain passwords, tokens, or secrets.
- The DigiLocker client secret and CAPTCHA secret key must be supplied as environment variables and must never be committed to source control.
