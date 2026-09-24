import keycloak from './keycloak';

const BASE = '/api';

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
): Promise<T> {
  const token = await getToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
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

/** ADMIN or EMPLOYEE — can see every customer and account. */
export function isStaff(): boolean {
  return hasRole('ADMIN') || hasRole('EMPLOYEE');
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
  if (hasRole('EMPLOYEE')) return 'Employee';
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
};

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

export const transactions = {
  getAll:       ()                  => request<Transaction[]>('GET', '/transactions'),
  getMy:        ()                  => request<Transaction[]>('GET', '/transactions/my'),
  getByAccount: (accountId: number) => request<Transaction[]>('GET', `/accounts/${accountId}/transactions`),
  getById:      (id: number)        => request<Transaction>  ('GET', `/transactions/${id}`),
};
