import keycloak from './keycloak';

/**
 * API base URL — configurable per environment.
 *   Docker / NGINX gateway:  /api                      (default, same origin)
 *   Direct to Spring Boot:   http://localhost:8081/api (set in .env.local)
 */
const BASE = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');

async function getToken(): Promise<string> {
  await keycloak.updateToken(30).catch(() => {
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
  try { data = text ? JSON.parse(text) : undefined; } catch { data = text; }
  if (!res.ok) {
    if (res.status === 401) keycloak.login();
    throw new Error(extractMessage(data, res.status));
  }
  return data as T;
}

/** Multipart/form-data upload — skips Content-Type so browser sets the boundary. */
async function upload<T>(path: string, formData: FormData): Promise<T> {
  const token = await getToken();
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
  } catch {
    throw new Error('Cannot reach the server. Check your connection and try again.');
  }
  const text = await res.text();
  let data: unknown;
  try { data = text ? JSON.parse(text) : undefined; } catch { data = text; }
  if (!res.ok) throw new Error(extractMessage(data, res.status));
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

// ── Auth helpers ──────────────────────────────────────────────────────────────

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

export function getRoles(): string[] {
  return claims().realm_access?.roles ?? [];
}

export function hasRole(role: string): boolean {
  return getRoles().includes(role);
}

export function isAdmin(): boolean { return hasRole('ADMIN'); }

/** Anyone working at the bank. */
export function isStaff(): boolean {
  return ['ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER'].some(hasRole);
}

/** MAKER role (ADMIN also satisfies). Direct financial operations now go through Maker-Checker. */
export function isMaker(): boolean {
  return hasRole('ADMIN') || hasRole('MAKER');
}

/** Pure MAKER without CHECKER — routes deposit/withdraw/transfer to request queue. */
export function isPureMaker(): boolean {
  return hasRole('MAKER') && !hasRole('CHECKER') && !hasRole('ADMIN');
}

/** CHECKER role (ADMIN also satisfies). Approves/rejects Maker requests. */
export function isChecker(): boolean {
  return hasRole('ADMIN') || hasRole('CHECKER');
}

/** May move money directly (ADMIN or CUSTOMER on own account).
 *  @deprecated Use isMaker()/isPureMaker() for staff logic; kept for customer account page */
export function canTransact(): boolean {
  return hasRole('ADMIN') || hasRole('MAKER') || hasRole('CUSTOMER');
}

/** ADMIN or EMPLOYEE — customer management, KYC review. */
export function canManageCustomers(): boolean {
  return hasRole('ADMIN') || hasRole('EMPLOYEE');
}

export function canReviewKyc(): boolean {
  return hasRole('ADMIN') || hasRole('EMPLOYEE');
}

/** Third-Party Provider. */
export function isTpp(): boolean {
  return hasRole('TPP') && !isStaff();
}

export function getUsername(): string {
  return claims().preferred_username ?? 'User';
}

export function getUserId(): string {
  return (keycloak.tokenParsed?.sub as string | undefined) ?? '';
}

export function getDisplayName(): string {
  const c = claims();
  return c.name || c.given_name || c.preferred_username || 'User';
}

export function getRoleLabel(): string {
  if (isAdmin()) return 'Administrator';
  const parts: string[] = [];
  if (hasRole('MAKER'))   parts.push('Maker');
  if (hasRole('CHECKER')) parts.push('Checker');
  if (parts.length) return `Employee · ${parts.join(' & ')}`;
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
  sync:      ()                               => request<Customer>  ('POST',   '/auth/sync'),
  adminSync: ()                               => request<{ synced: number }>('POST', '/admin/sync-customers'),
  enableOnlineBanking: (id: number)           => request<ActionResult<Customer>>('POST', `/customers/${id}/online-banking`),
  sendPasswordEmail:   (id: number)           => request<ActionResult<Customer>>('POST', `/customers/${id}/online-banking/password-email`),
};

// ── Staff ─────────────────────────────────────────────────────────────────────

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
    request<unknown>('POST', `/accounts/${id}/deposit`, { amount, description }),
  withdraw:  (id: number, amount: number, description?: string) =>
    request<unknown>('POST', `/accounts/${id}/withdraw`, { amount, description }),
  transfer:  (input: TransferInput) => request<unknown>('POST', '/accounts/transfer', input),
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
  post:         (data: PostTransaction) => request<unknown>('POST', '/transactions', data),
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
  getAll:   ()                                 => request<Consent[]>('GET', '/consents'),
  create:   (data: ConsentRequest)             => request<Consent>  ('POST', '/consents', data),
  approve:  (id: string, accountIds: number[]) => request<Consent>  ('POST', `/consents/${encodeURIComponent(id)}/approve`, { accountIds }),
  reject:   (id: string)                       => request<Consent>  ('POST', `/consents/${encodeURIComponent(id)}/reject`),
  revoke:   (id: string)                       => request<Consent>  ('POST', `/consents/${encodeURIComponent(id)}/revoke`),
};

export const openBanking = {
  accounts:     (consentId: string) =>
    request<OpenBankingAccount[]>('GET', '/open-banking/accounts', undefined, { 'x-consent-id': consentId }),
  transactions: (consentId: string, accountId: number) =>
    request<Transaction[]>('GET', `/open-banking/accounts/${accountId}/transactions`, undefined, { 'x-consent-id': consentId }),
};

// ── Maker–Checker Transaction Requests ────────────────────────────────────────

export type TxRequestStatus =
  | 'PENDING_APPROVAL' | 'APPROVED' | 'PROCESSING'
  | 'SUCCESS' | 'REJECTED' | 'FAILED' | 'CANCELLED' | 'REVERSED';
export type TxRequestType = 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER';

export interface TxRequest {
  id: number;
  requestRef: string;
  requestType: TxRequestType;
  status: TxRequestStatus;
  fromAccountId: number;
  fromAccountNumber: string;
  fromAccountType: string;
  toAccountId: number | null;
  toAccountNumber: string | null;
  amount: number;
  description: string | null;
  makerUserId: string;
  makerUsername: string;
  checkerUserId: string | null;
  checkerUsername: string | null;
  createdAt: string;
  approvedAt: string | null;
  rejectedAt: string | null;
  executedAt: string | null;
  rejectionReason: string | null;
  remarks: string | null;
  transactionId: number | null;
  balanceAfter: number | null;
}
export interface CreateTxRequest {
  requestType: TxRequestType;
  fromAccountId: number;
  toAccountId?: number;
  toAccountNumber?: string;
  amount: number;
  description?: string;
  remarks?: string;
}
export interface CheckerAction {
  rejectionReason?: string;
  remarks?: string;
}

export const txRequests = {
  create:     (data: CreateTxRequest) => request<TxRequest>   ('POST', '/transaction-requests', data),
  getMy:      ()                       => request<TxRequest[]> ('GET',  '/transaction-requests/my'),
  getPending: ()                       => request<TxRequest[]> ('GET',  '/transaction-requests/pending'),
  getAll:     ()                       => request<TxRequest[]> ('GET',  '/transaction-requests'),
  getById:    (id: number)             => request<TxRequest>   ('GET',  `/transaction-requests/${id}`),
  approve:    (id: number, data?: CheckerAction) =>
    request<TxRequest>('POST', `/transaction-requests/${id}/approve`, data ?? {}),
  reject:     (id: number, data: CheckerAction) =>
    request<TxRequest>('POST', `/transaction-requests/${id}/reject`, data),
  cancel:     (id: number)             => request<TxRequest>   ('POST', `/transaction-requests/${id}/cancel`),
};

// ── KYC ───────────────────────────────────────────────────────────────────────

export type KycStatus = 'NOT_STARTED' | 'PENDING' | 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED';
export type KycMethod = 'MANUAL' | 'DIGILOCKER' | 'MOCK';
export type KycDocumentType = 'IDENTITY' | 'ADDRESS' | 'PHOTOGRAPH' | 'OTHER';

export interface KycDocument {
  id: number;
  documentType: KycDocumentType;
  fileName: string;
  contentType: string;
  fileSize: number;
  status: string;
  uploadedAt: string;
}
export interface KycRecord {
  id: number | null;
  customerId: number;
  customerName: string;
  customerEmail: string;
  status: KycStatus;
  method: KycMethod | null;
  initiatedAt: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  verifiedAt: string | null;
  reviewedBy: string | null;
  rejectionReason: string | null;
  reviewerNotes: string | null;
  documents: KycDocument[];
}
export interface KycInitiationResult {
  redirectUrl: string | null;
  message: string;
  providerRef: string | null;
}
export interface KycReviewRequest {
  rejectionReason?: string;
  reviewerNotes?: string;
}

export const kyc = {
  start:       ()                                      => request<KycInitiationResult>('POST', '/kyc/start'),
  submit:      ()                                      => request<KycRecord>           ('POST', '/kyc/submit'),
  getMy:       ()                                      => request<KycRecord>           ('GET',  '/kyc/my'),
  uploadDocument: (type: KycDocumentType, file: File)  => {
    const fd = new FormData();
    fd.append('type', type);
    fd.append('file', file);
    return upload<KycDocument>('/kyc/documents', fd);
  },
  // Staff endpoints
  listPending:     ()                              => request<KycRecord[]>('GET',  '/kyc/pending'),
  getForCustomer:  (customerId: number)            => request<KycRecord>  ('GET',  `/kyc/customer/${customerId}`),
  approve:         (customerId: number, data?: KycReviewRequest) =>
    request<KycRecord>('POST', `/kyc/customer/${customerId}/approve`, data ?? {}),
  reject:          (customerId: number, data: KycReviewRequest) =>
    request<KycRecord>('POST', `/kyc/customer/${customerId}/reject`, data),
  /** Returns a download URL (backend streams the file — not a direct storage URL). */
  documentDownloadUrl: (docId: number) => `${BASE}/kyc/documents/${docId}/download`,
};

// ── Audit Log ─────────────────────────────────────────────────────────────────

export interface AuditLog {
  id: number;
  action: string;
  actorUserId: string;
  actorUsername: string;
  actorRole: string;
  resourceType: string | null;
  resourceId: string | null;
  status: string | null;
  remarks: string | null;
  timestamp: string;
  requestId: string | null;
  httpRequestId: string | null;
}

export const audit = {
  getAll:      (page = 0, size = 50) => request<AuditLog[]>('GET', `/audit?page=${page}&size=${size}`),
  getByActor:  (userId: string)       => request<AuditLog[]>('GET', `/audit/actor/${encodeURIComponent(userId)}`),
  getByResource: (type: string, id: string) =>
    request<AuditLog[]>('GET', `/audit/resource/${encodeURIComponent(type)}/${encodeURIComponent(id)}`),
};

// ── CAPTCHA ───────────────────────────────────────────────────────────────────

export interface CaptchaConfig {
  enabled: boolean;
  siteKey: string;
}

export const captcha = {
  getConfig: () => fetch(`${BASE}/captcha/config`).then((r) => r.json() as Promise<CaptchaConfig>),
  verify:    (token: string) => request<{ valid: boolean; message: string }>('POST', '/captcha/verify', { token }),
};
