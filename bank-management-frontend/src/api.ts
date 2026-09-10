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
    throw new Error('Session expired — redirecting to login');
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

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    data = text;
  }

  if (!res.ok) {
    if (res.status === 401) {
      // Token rejected — kick back to Keycloak
      keycloak.login();
    }
    const msg =
      (data as { message?: string })?.message || text || `Error ${res.status}`;
    throw new Error(msg);
  }

  return data as T;
}

// ── Auth helpers (derived from the live Keycloak token) ───────────────────────

/** Role set extracted from realm_access.roles in the Keycloak JWT. */
export function getRoles(): string[] {
  const ra = (keycloak.tokenParsed as Record<string, unknown>)
    ?.realm_access as { roles?: string[] } | undefined;
  return ra?.roles ?? [];
}

export function hasRole(role: string): boolean {
  return getRoles().includes(role);
}

export function getUsername(): string {
  return (
    (keycloak.tokenParsed as Record<string, unknown>)
      ?.preferred_username as string | undefined
  ) ?? 'User';
}

// ── Customers ─────────────────────────────────────────────────────────────────

export interface Customer {
  customerId: number;
  name: string;
  email: string;
  phone: string;
  address: string;
}
export interface CustomerRequest {
  name: string;
  email: string;
  phone: string;
  address: string;
}

export const customers = {
  getAll:   ()                              => request<Customer[]>('GET',    '/customers'),
  getMe:    ()                              => request<Customer>  ('GET',    '/customers/me'),
  getById:  (id: number)                   => request<Customer>  ('GET',    `/customers/${id}`),
  create:   (data: CustomerRequest)        => request<Customer>  ('POST',   '/customers', data),
  update:   (id: number, d: CustomerRequest) => request<Customer>('PUT',    `/customers/${id}`, d),
  delete:   (id: number)                   => request<string>    ('DELETE', `/customers/${id}`),
};

// ── Accounts ──────────────────────────────────────────────────────────────────

export interface Account {
  accountId: number;
  accountNumber: string;
  accountType: string;
  balance: number;
  customerName: string;
}
export interface AccountRequest {
  accountNumber: string;
  accountType: string;
  balance: number;
  customerId: number;
}

export const accounts = {
  getAll:    ()                                          => request<Account[]>('GET',  '/accounts'),
  getMy:     ()                                          => request<Account[]>('GET',  '/accounts/my'),
  getById:   (id: number)                               => request<Account>  ('GET',  `/accounts/${id}`),
  create:    (data: AccountRequest)                     => request<Account>  ('POST', '/accounts', data),
  update:    (id: number, data: AccountRequest)         => request<Account>  ('PUT',  `/accounts/${id}`, data),
  delete:    (id: number)                               => request<string>   ('DELETE', `/accounts/${id}`),
  deposit:   (id: number, amount: number)               => request<Account>  ('POST', `/accounts/${id}/deposit`,  { amount }),
  withdraw:  (id: number, amount: number)               => request<Account>  ('POST', `/accounts/${id}/withdraw`, { amount }),
  transfer:  (fromAccountId: number, toAccountId: number, amount: number) =>
    request<string>('POST', '/accounts/transfer', { fromAccountId, toAccountId, amount }),
};

// ── Transactions ──────────────────────────────────────────────────────────────

export interface Transaction {
  transactionId: number;
  transactionType: string;
  amount: number;
  transactionDate: string;
  accountNumber: string;
}

export const transactions = {
  getAll:       ()                  => request<Transaction[]>('GET', '/transactions'),
  getByAccount: (accountId: number) => request<Transaction[]>('GET', `/accounts/${accountId}/transactions`),
  getById:      (id: number)        => request<Transaction>  ('GET', `/transactions/${id}`),
};
