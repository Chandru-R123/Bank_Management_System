import keycloak from './keycloak';

/**
 * API base URL — configurable per environment (Week 3 requirement).
 *   Docker / NGINX gateway:  /api                      (default, same origin)
 *   Direct to Spring Boot:   http://localhost:8081/api (set in .env.local)
 */
const BASE = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');

/**
 * Returns a fresh access token, refreshing silently if it expires within
 * 30 seconds.  Keycloak handles the PKCE / token-refresh mechanics.
 */
async function getToken(): Promise<string> {
  // Refresh if the token expires in < 30 s
  await keycloak.updateToken(30).catch(() => {
    // Refresh failed (session ended) — send user back to Keycloak login
    keycloak.login();
    throw new Error('Your session has expired — redirecting to sign in');
  });
  return keycloak.token!;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  extraHeaders: Record<string, string> = {},
): Promise<T> {
  const token = await getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    ...extraHeaders,
  };

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error('Cannot reach the server. Check your connection and try again.');
  }

  const text = await res.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = text;
  }

  if (!res.ok) {
    if (res.status === 401) {
      // Token rejected — kick back to Keycloak
      keycloak.login();
    }
    throw new Error(extractMessage(data, res.status));
  }

  return data as T;
}

function extractMessage(data: unknown, status: number): string {
  if (data && typeof data === 'object') {
    const d = data as { message?: string; errors?: Record<string, string> };
    if (d.message) return d.message;
    if (d.errors) return Object.values(d.errors).join(' · ');
  }
  if (typeof data === 'string' && data && !data.trim().startsWith('<')) return data;
  if (status === 403) return 'You do not have permission to do this';
  if (status === 404) return 'Not found';
  if (status >= 500) return 'The server had a problem. Please try again shortly.';
  return `Request failed (${status})`;
}

// ── Auth helpers (derived from the live Keycloak token) ───────────────────────

type TokenClaims = Record<string, unknown> & {
  realm_access?: { roles?: string[] };
  preferred_username?: string;
  name?: string;
  given_name?: string;
  email?: string;
};

function claims(): TokenClaims {
  return (keycloak.tokenParsed ?? {}) as TokenClaims;
}

/** Role set extracted from realm_access.roles in the Keycloak JWT. */
export function getRoles(): string[] {
  return claims().realm_access?.roles ?? [];
}

export function hasRole(role: string): boolean {
  return getRoles().includes(role);
}

export function isAdmin(): boolean {
  return hasRole('ADMIN');
}

/** Anyone working at the bank — can see every customer and account. */
export function isStaff(): boolean {
  return ['ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER'].some(hasRole);
}

/** May move money on any account (ADMIN or MAKER). */
export function canTransact(): boolean {
  return hasRole('ADMIN') || hasRole('MAKER');
}

/** May perform verification actions (ADMIN or CHECKER). */
export function isChecker(): boolean {
  return hasRole('ADMIN') || hasRole('CHECKER');
}

/** May create / edit customer KYC records. */
export function canManageCustomers(): boolean {
  return hasRole('ADMIN') || hasRole('EMPLOYEE');
}

/** Third-Party Provider (fintech app) using Open Banking. */
export function isTpp(): boolean {
  return hasRole('TPP') && !isStaff();
}

export function getUsername(): string {
  return claims().preferred_username ?? 'User';
}

/** Full name from the token when available, otherwise the username. */
export function getDisplayName(): string {
  const c = claims();
  return c.name || c.given_name || c.preferred_username || 'User';
}

export function getRoleLabel(): string {
  if (isAdmin()) return 'Administrator';
  const staff = [hasRole('MAKER') && 'Maker', hasRole('CHECKER') && 'Checker'].filter(Boolean);
  if (staff.length) return `Employee · ${staff.join(' & ')}`;
  if (hasRole('EMPLOYEE')) return 'Employee';
  if (isTpp()) return 'Third-party provider';
  return 'Customer';
}

// ── Customers ─────────────────────────────────────────────────────────────────

export interface Customer {
  customerId: number;
  name: string;
  email: string;
  phone: string;
  address: string;
  onlineBanking: boolean;
}
export interface CustomerRequest {
  name: string;
  email: string;
  phone: string;
  address: string;
}
/** Result of an action that may also send an email. */
export interface ActionResult<T> {
  data: T;
  emailSent: boolean;
  message: string;
}

export interface ProfileUpdate {
  phone: string;
  address: string;
}

export const customers = {
  getAll:    ()                               => request<Customer[]>('GET',    '/customers'),
  getMe:     ()                               => request<Customer>  ('GET',    '/customers/me'),
  updateMe:  (data: ProfileUpdate)            => request<Customer>  ('PUT',    '/customers/me', data),
  getById:   (id: number)                     => request<Customer>  ('GET',    `/customers/${id}`),
  create:    (data: CustomerRequest)          => request<Customer>  ('POST',   '/customers', data),
  update:    (id: number, d: CustomerRequest) => request<Customer>  ('PUT',    `/customers/${id}`, d),
  delete:    (id: number)                     => request<string>    ('DELETE', `/customers/${id}`),
  /** Called after CUSTOMER login to link this KC user to a PostgreSQL row */
  sync:      ()                               => request<Customer>  ('POST',   '/auth/sync'),
  /** Called when staff open Customers/Dashboard — pulls all KC registrations into DB */
  adminSync: ()                               => request<{ synced: number }>('POST', '/admin/sync-customers'),
  /** Create the Keycloak login for a branch customer and email a "set password" link */
  enableOnlineBanking: (id: number)           => request<ActionResult<Customer>>('POST', `/customers/${id}/online-banking`),
  /** Email a "reset your password" link to a customer with online banking */
  sendPasswordEmail:   (id: number)           => request<ActionResult<Customer>>('POST', `/customers/${id}/online-banking/password-email`),
};

// ── Staff (ADMIN) ─────────────────────────────────────────────────────────────

export type StaffRole = 'ADMIN' | 'EMPLOYEE' | 'MAKER' | 'CHECKER';

export interface StaffMember {
  id: string;
  username: string;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phone: string | null;
  enabled: boolean;
  roles: StaffRole[];
  createdTimestamp: number | null;
}
export interface StaffRequest {
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  address?: string;
  roles: StaffRole[];
  /** Temporary password; omit to email a "set password" link instead. */
  password?: string;
}

export const staff = {
  getAll:        ()                                => request<StaffMember[]>('GET', '/admin/staff'),
  create:        (data: StaffRequest)              => request<ActionResult<StaffMember>>('POST', '/admin/staff', data),
  updateRoles:   (id: string, roles: StaffRole[])  => request<StaffMember>('PUT', `/admin/staff/${id}/roles`, { roles }),
  enable:        (id: string)                      => request<StaffMember>('POST', `/admin/staff/${id}/enable`),
  disable:       (id: string)                      => request<StaffMember>('POST', `/admin/staff/${id}/disable`),
  passwordEmail: (id: string)                      => request<ActionResult<StaffMember>>('POST', `/admin/staff/${id}/password-email`),
  setPassword:   (id: string, password: string)    => request<ActionResult<StaffMember>>('PUT', `/admin/staff/${id}/password`, { password }),
};

/** Keycloak user id of the signed-in user ("sub" claim). */
export function getUserId(): string {
  return (keycloak.tokenParsed?.sub as string | undefined) ?? '';
}

// ── Accounts ──────────────────────────────────────────────────────────────────

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  accountId: number;
  accountNumber: string;
  accountType: string;
  balance: number;
  customerId: number | null;
  customerName: string;
  status: AccountStatus;
  createdAt: string | null;
}
export interface AccountRequest {
  accountNumber?: string;
  accountType: string;
  balance?: number;
  customerId: number;
}
export interface AccountLookup {
  accountNumber: string;
  holderName: string;
  accountType: string;
  canReceive: boolean;
}
export interface TransferInput {
  fromAccountId: number;
  toAccountId?: number;
  toAccountNumber?: string;
  amount: number;
  description?: string;
}

export const accounts = {
  getAll:    ()                                  => request<Account[]>('GET',  '/accounts'),
  getMy:     ()                                  => request<Account[]>('GET',  '/accounts/my'),
  getById:   (id: number)                        => request<Account>  ('GET',  `/accounts/${id}`),
  lookup:    (number: string)                    => request<AccountLookup>('GET', `/accounts/lookup?number=${encodeURIComponent(number)}`),
  create:    (data: AccountRequest)              => request<Account>  ('POST', '/accounts', data),
  update:    (id: number, data: AccountRequest)  => request<Account>  ('PUT',  `/accounts/${id}`, data),
  close:     (id: number)                        => request<Account>  ('POST', `/accounts/${id}/close`),
  freeze:    (id: number)                        => request<Account>  ('POST', `/accounts/${id}/freeze`),
  unfreeze:  (id: number)                        => request<Account>  ('POST', `/accounts/${id}/unfreeze`),
  deposit:   (id: number, amount: number, description?: string) =>
    request<Account>('POST', `/accounts/${id}/deposit`, { amount, description }),
  withdraw:  (id: number, amount: number, description?: string) =>
    request<Account>('POST', `/accounts/${id}/withdraw`, { amount, description }),
  transfer:  (input: TransferInput)              => request<Transaction>('POST', '/accounts/transfer', input),
};

// ── Transactions ──────────────────────────────────────────────────────────────

export interface Transaction {
  transactionId: number;
  transactionType: string;
  amount: number;
  transactionDate: string;
  accountId: number | null;
  accountNumber: string;
  balanceAfter: number | null;
  description: string | null;
  counterpartyAccountNumber: string | null;
  referenceId: string | null;
  performedBy: string | null;
}

export interface PostTransaction {
  accountId?: number;
  type: 'DEPOSIT' | 'WITHDRAW';
  amount: number;
  description?: string;
}

export const transactions = {
  post:         (data: PostTransaction) => request<Transaction>('POST', '/transactions', data),
  getAll:       ()                  => request<Transaction[]>('GET', '/transactions'),
  getMy:        ()                  => request<Transaction[]>('GET', '/transactions/my'),
  getByAccount: (accountId: number) => request<Transaction[]>('GET', `/accounts/${accountId}/transactions`),
  getById:      (id: number)        => request<Transaction>  ('GET', `/transactions/${id}`),
};

// ── Beneficiaries ─────────────────────────────────────────────────────────────

export interface Beneficiary {
  beneficiaryId: number;
  nickname: string;
  accountNumber: string;
  holderName: string;
  accountType: string | null;
  canReceive: boolean;
  customerId: number;
  customerName: string;
  createdAt: string | null;
}
export interface BeneficiaryRequest {
  nickname: string;
  accountNumber: string;
  customerId?: number;
}

export const beneficiaries = {
  getAll:  ()                         => request<Beneficiary[]>('GET', '/beneficiaries'),
  create:  (data: BeneficiaryRequest) => request<Beneficiary>  ('POST', '/beneficiaries', data),
  delete:  (id: number)               => request<unknown>      ('DELETE', `/beneficiaries/${id}`),
};

// ── Open Banking consents ─────────────────────────────────────────────────────

export type ConsentStatus = 'AWAITING_AUTHORISATION' | 'AUTHORISED' | 'REJECTED' | 'REVOKED' | 'EXPIRED';
export type ConsentPermission = 'READ_ACCOUNTS' | 'READ_BALANCES' | 'READ_TRANSACTIONS';

export interface Consent {
  consentId: string;
  status: ConsentStatus;
  customerId: number;
  customerName: string;
  tppUsername: string;
  tppName: string;
  purpose: string;
  permissions: ConsentPermission[];
  accounts: { accountId: number; accountNumber: string; accountType: string }[];
  createdAt: string;
  expiresAt: string;
  statusUpdatedAt: string | null;
  statusUpdatedBy: string | null;
}
export interface ConsentRequest {
  customerEmail: string;
  permissions: ConsentPermission[];
  purpose: string;
  validityDays: number;
  tppName?: string;
}
export interface OpenBankingAccount {
  accountId: number;
  accountNumber: string;
  accountType: string;
  status: string;
  holderName: string;
  balance: number | null;
  currency: string;
}

export const consents = {
  getAll:   ()                                => request<Consent[]>('GET', '/consents'),
  create:   (data: ConsentRequest)            => request<Consent>  ('POST', '/consents', data),
  approve:  (id: string, accountIds: number[]) => request<Consent> ('POST', `/consents/${encodeURIComponent(id)}/approve`, { accountIds }),
  reject:   (id: string)                      => request<Consent>  ('POST', `/consents/${encodeURIComponent(id)}/reject`),
  revoke:   (id: string)                      => request<Consent>  ('POST', `/consents/${encodeURIComponent(id)}/revoke`),
};

/** Account Information APIs — every call carries the consent id header. */
export const openBanking = {
  accounts: (consentId: string) =>
    request<OpenBankingAccount[]>('GET', '/open-banking/accounts', undefined, { 'x-consent-id': consentId }),
  transactions: (consentId: string, accountId: number) =>
    request<Transaction[]>('GET', `/open-banking/accounts/${accountId}/transactions`, undefined, { 'x-consent-id': consentId }),
};
