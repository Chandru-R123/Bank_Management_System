import './style.css';
import keycloak from './keycloak';
import {
  customers, getDisplayName, getRoleLabel, getRoles, hasRole,
  isAdmin, isStaff, isTpp, isMaker, isChecker, canReviewKyc,
} from './api';
import { icon } from './icons';
import type { IconName } from './icons';
import { esc, initials, toast, todayLong, errorMessage } from './utils';

// ── Page imports ──────────────────────────────────────────────────────────────

import { renderDashboard }      from './pages/dashboard';
import { renderCustomers }      from './pages/customers';
import { renderAccounts }       from './pages/accounts';
import { renderTransactions }   from './pages/transactions';
import { renderMyAccounts }     from './pages/my-accounts';
import { renderMyTransactions } from './pages/my-transactions';
import { renderProfile }        from './pages/profile';
import { renderBeneficiaries }  from './pages/beneficiaries';
import { renderConsents }       from './pages/consents';
import { renderTppPortal }      from './pages/tpp';
import { renderStaff }          from './pages/staff';
import { renderRequests }       from './pages/requests';
import { renderApprovals }      from './pages/approvals';
import { renderKyc }            from './pages/kyc';
import { renderKycReview }      from './pages/kyc-review';
import { renderAuditLog }       from './pages/audit';

const app = document.getElementById('app')!;

app.innerHTML = `
  <div class="boot">
    <div class="boot-card">
      <div class="brand-mark" style="margin:0 auto;width:52px;height:52px;border-radius:14px">${icon('bank', 26)}</div>
      <div class="boot-spinner"></div>
      <p>Connecting securely…</p>
    </div>
  </div>`;

// ── Route definition ──────────────────────────────────────────────────────────

interface Route {
  title: string;
  crumb: string;
  icon: IconName;
  render: (el: HTMLElement) => Promise<void>;
  /** When truthy, only shown if the condition holds. */
  when?: () => boolean;
}

// ── ADMIN routes — full set ────────────────────────────────────────────────────
const adminRoutes: Record<string, Route> = {
  dashboard:   { title: 'Overview',       crumb: 'Branch performance at a glance',       icon: 'dashboard', render: renderDashboard },
  customers:   { title: 'Customers',      crumb: 'KYC records & relationships',           icon: 'users',     render: renderCustomers },
  accounts:    { title: 'Accounts',       crumb: 'Open, service and close accounts',      icon: 'bank',      render: renderAccounts },
  transactions:{ title: 'Transactions',   crumb: 'Ledger of every movement',              icon: 'receipt',   render: renderTransactions },
  requests:    { title: 'Requests',       crumb: 'Maker–Checker transaction requests',    icon: 'send',      render: renderRequests },
  approvals:   { title: 'Approvals',      crumb: 'Pending Maker requests awaiting action',icon: 'checkCircle',render: renderApprovals },
  'kyc-review':{ title: 'KYC Review',     crumb: 'Review customer identity documents',    icon: 'user',      render: renderKycReview },
  beneficiaries:{ title: 'Beneficiaries', crumb: 'Saved payees of every customer',       icon: 'userPlus',  render: renderBeneficiaries },
  consents:    { title: 'Consents',       crumb: 'Open Banking access requests',          icon: 'shield',    render: renderConsents },
  audit:       { title: 'Audit Log',      crumb: 'Full system audit trail',              icon: 'activity',  render: renderAuditLog },
  staff:       { title: 'Staff',          crumb: 'Employee, maker & checker logins',      icon: 'key',       render: renderStaff },
};

// ── EMPLOYEE routes — customers, accounts (read), KYC review ─────────────────
const employeeRoutes: Record<string, Route> = {
  dashboard:    { title: 'Overview',      crumb: 'Branch performance at a glance',        icon: 'dashboard', render: renderDashboard },
  customers:    { title: 'Customers',     crumb: 'KYC records & relationships',            icon: 'users',     render: renderCustomers },
  accounts:     { title: 'Accounts',      crumb: 'View accounts',                          icon: 'bank',      render: renderAccounts },
  transactions: { title: 'Transactions',  crumb: 'Ledger of every movement',               icon: 'receipt',   render: renderTransactions },
  'kyc-review': { title: 'KYC Review',    crumb: 'Review customer identity documents',     icon: 'user',      render: renderKycReview },
  beneficiaries:{ title: 'Beneficiaries', crumb: 'Saved payees',                           icon: 'userPlus',  render: renderBeneficiaries },
  consents:     { title: 'Consents',      crumb: 'Open Banking access requests',           icon: 'shield',    render: renderConsents },
};

// ── MAKER routes — create requests, view own requests ─────────────────────────
const makerRoutes: Record<string, Route> = {
  dashboard:    { title: 'Overview',      crumb: 'Branch performance at a glance',        icon: 'dashboard', render: renderDashboard },
  customers:    { title: 'Customers',     crumb: 'Customer records',                       icon: 'users',     render: renderCustomers },
  accounts:     { title: 'Accounts',      crumb: 'View accounts',                          icon: 'bank',      render: renderAccounts },
  requests:     { title: 'My Requests',   crumb: 'My Maker–Checker requests',              icon: 'send',      render: renderRequests },
  transactions: { title: 'Transactions',  crumb: 'Ledger',                                 icon: 'receipt',   render: renderTransactions },
  beneficiaries:{ title: 'Beneficiaries', crumb: 'Saved payees',                           icon: 'userPlus',  render: renderBeneficiaries },
};

// ── CHECKER routes — pending approvals, history ───────────────────────────────
const checkerRoutes: Record<string, Route> = {
  dashboard:    { title: 'Overview',      crumb: 'Branch performance at a glance',        icon: 'dashboard', render: renderDashboard },
  approvals:    { title: 'Approvals',     crumb: 'Pending Maker requests',                 icon: 'checkCircle',render: renderApprovals },
  transactions: { title: 'Transactions',  crumb: 'Executed transactions',                  icon: 'receipt',   render: renderTransactions },
  accounts:     { title: 'Accounts',      crumb: 'View accounts',                          icon: 'bank',      render: renderAccounts },
  customers:    { title: 'Customers',     crumb: 'Customer records',                       icon: 'users',     render: renderCustomers },
  audit:        { title: 'Audit Log',     crumb: 'System audit trail',                     icon: 'activity',  render: renderAuditLog },
  beneficiaries:{ title: 'Beneficiaries', crumb: 'Saved payees',                           icon: 'userPlus',  render: renderBeneficiaries },
  consents:     { title: 'Consents',      crumb: 'Open Banking access requests',           icon: 'shield',    render: renderConsents },
};

// ── CUSTOMER routes — own banking ─────────────────────────────────────────────
const customerRoutes: Record<string, Route> = {
  home:              { title: 'My Accounts',    crumb: 'Balances & quick actions',         icon: 'wallet',   render: renderMyAccounts },
  'my-transactions': { title: 'Transactions',   crumb: 'Your account activity',            icon: 'receipt',  render: renderMyTransactions },
  beneficiaries:     { title: 'Beneficiaries',  crumb: 'People you pay',                   icon: 'userPlus', render: renderBeneficiaries },
  apps:              { title: 'Connected apps', crumb: 'Open Banking consents',            icon: 'shield',   render: renderConsents },
  kyc:               { title: 'KYC',            crumb: 'Identity verification',            icon: 'user',     render: renderKyc },
  profile:           { title: 'Profile',        crumb: 'Contact details & security',       icon: 'user',     render: renderProfile },
};

// ── TPP routes ────────────────────────────────────────────────────────────────
const tppRoutes: Record<string, Route> = {
  portal: { title: 'Open Banking', crumb: 'Consents & account information', icon: 'shield', render: renderTppPortal },
};

/** Resolve the correct route table for the current user. */
function routes(): Record<string, Route> {
  if (isTpp()) return tppRoutes;
  if (!isStaff()) return customerRoutes;

  // Staff: build composite based on roles
  if (isAdmin()) return adminRoutes;

  // Composite staff routes: merge by role priority
  const combined: Record<string, Route> = { ...employeeRoutes };
  if (isMaker() && !isChecker())   Object.assign(combined, makerRoutes);
  if (isChecker() && !isMaker())   Object.assign(combined, checkerRoutes);
  if (isMaker() && isChecker()) {
    // Has both roles — show all staff routes except admin-only staff management
    Object.assign(combined, makerRoutes, checkerRoutes);
  }
  if (canReviewKyc() && !combined['kyc-review']) {
    combined['kyc-review'] = adminRoutes['kyc-review'];
  }
  return combined;
}

function defaultPage(): string {
  if (isTpp())   return 'portal';
  if (!isStaff()) return 'home';
  if (isChecker() && !isMaker()) return 'approvals';
  if (isMaker() && !isChecker()) return 'requests';
  return 'dashboard';
}

// ── Bootstrap ─────────────────────────────────────────────────────────────────

keycloak
  .init({ onLoad: 'login-required', checkLoginIframe: false, pkceMethod: 'S256' })
  .then(async (authenticated) => {
    if (!authenticated) { keycloak.login(); return; }

    setInterval(() => {
      keycloak.updateToken(60).catch(() => {
        console.warn('Token refresh failed — logging out');
        keycloak.logout();
      });
    }, 30_000);
    watchRoleChanges();

    if (hasRole('CUSTOMER') && !isStaff() && !isTpp()) {
      try { await customers.sync(); } catch (err) {
        console.warn('Customer sync failed (non-fatal):', err);
      }
    }

    buildShell();
    window.addEventListener('hashchange', route);
    await route();
  })
  .catch((err) => {
    console.error('Keycloak init failed', err);
    app.innerHTML = `
      <div class="boot">
        <div class="boot-card">
          <div class="brand-mark" style="margin:0 auto;width:52px;height:52px;border-radius:14px">${icon('bank', 26)}</div>
          <h2 style="margin-top:18px">We couldn't reach the sign-in service</h2>
          <p>Make sure the stack is running (<code>docker compose up --build</code>) and open <strong>http://localhost:8080</strong>.</p>
          <button class="btn btn-primary mt-4" onclick="location.reload()">Try again</button>
          <pre>${esc(String(err?.error ?? err))}</pre>
        </div>
      </div>`;
  });

// ── Live role changes ─────────────────────────────────────────────────────────

function roleSignature(): string {
  return [...getRoles()].sort().join(',');
}

/**
 * Menu, pages and buttons (deposit / withdraw / transfer …) all follow the
 * roles in the access token. Tokens live an hour, so when an admin changes
 * this user's roles on the Staff page nothing would change until then.
 * Refresh the token every minute and whenever the tab gets focus; if the
 * roles differ, rebuild the shell and re-render the page.
 */
function watchRoleChanges() {
  let current = roleSignature();
  keycloak.onAuthRefreshSuccess = () => {
    const next = roleSignature();
    if (next === current) return;
    current = next;
    buildShell();
    void route();
    toast('Your access was updated by an administrator', 'info');
  };

  let lastForced = 0;
  const forceRefresh = () => {
    if (Date.now() - lastForced < 10_000) return;
    lastForced = Date.now();
    // Failures are ignored here; the 30-second check above handles ended sessions
    keycloak.updateToken(-1).catch(() => undefined);
  };
  setInterval(forceRefresh, 60_000);
  window.addEventListener('focus', forceRefresh);
}

// ── Shell ─────────────────────────────────────────────────────────────────────

function buildShell() {
  const name = getDisplayName();
  const nav = Object.entries(routes())
    .map(([key, r]) =>
      `<a href="#${key}" class="nav-link" data-route="${key}">${icon(r.icon, 18)}<span>${r.title}</span></a>`)
    .join('');

  const brandSub = isTpp() ? 'Developer Portal' : isStaff() ? 'Branch Console' : 'Online Banking';

  app.innerHTML = `
    <div class="app-shell">
      <aside class="sidebar" aria-label="Main navigation">
        <div class="sidebar-brand">
          <div class="brand-mark">${icon('bank', 20)}</div>
          <div>
            <div class="brand-name">State Bank</div>
            <div class="brand-sub">${esc(brandSub)}</div>
          </div>
        </div>
        <nav class="sidebar-nav">
          <div class="nav-label">${isStaff() ? 'Operations' : isTpp() ? 'Open Banking' : 'Banking'}</div>
          ${nav}
        </nav>
        <div class="sidebar-footer">
          <div class="user-card">
            <div class="avatar">${esc(initials(name))}</div>
            <div class="user-card-info">
              <div class="user-card-name" title="${esc(name)}">${esc(name)}</div>
              <div class="user-card-role">${esc(getRoleLabel())}</div>
            </div>
            <button class="btn-signout" id="logout-btn" title="Sign out" aria-label="Sign out">${icon('logout', 18)}</button>
          </div>
        </div>
      </aside>
      <div class="sidebar-backdrop" id="sidebar-backdrop"></div>
      <div class="main">
        <header class="topbar">
          <button class="btn-icon topbar-menu" id="menu-btn" aria-label="Open menu">${icon('menu', 20)}</button>
          <div>
            <div class="topbar-title" id="topbar-title"></div>
            <div class="topbar-crumb" id="topbar-crumb"></div>
          </div>
          <div class="topbar-spacer"></div>
          <div class="topbar-date">${icon('calendar', 14)} ${esc(todayLong())}</div>
          <button class="btn-icon" id="refresh-btn" title="Refresh" aria-label="Refresh">${icon('refresh', 18)}</button>
        </header>
        <main id="page-content"></main>
      </div>
    </div>`;

  const shell = app.querySelector<HTMLElement>('.app-shell')!;
  const closeNav = () => shell.classList.remove('nav-open');
  document.getElementById('menu-btn')!.addEventListener('click', () => shell.classList.add('nav-open'));
  document.getElementById('sidebar-backdrop')!.addEventListener('click', closeNav);
  shell.querySelectorAll('.nav-link').forEach((a) => a.addEventListener('click', closeNav));
  document.getElementById('logout-btn')!.addEventListener('click', () => {
    keycloak.logout({ redirectUri: window.location.origin + '/' });
  });
  document.getElementById('refresh-btn')!.addEventListener('click', () => void route());
}

// ── Router ────────────────────────────────────────────────────────────────────

let renderSeq = 0;

async function route() {
  const key = window.location.hash.replace(/^#/, '');
  const table = routes();

  if (!table[key]) {
    window.location.replace(`#${defaultPage()}`);
    return;
  }

  const r = table[key];
  document.title = `${r.title} · State Bank`;
  document.getElementById('topbar-title')!.textContent = r.title;
  document.getElementById('topbar-crumb')!.textContent = r.crumb;
  document.querySelectorAll<HTMLElement>('.nav-link').forEach((a) => {
    a.classList.toggle('active', a.dataset.route === key);
  });

  const content = document.getElementById('page-content')!;
  const seq = ++renderSeq;
  content.innerHTML = '';
  window.scrollTo({ top: 0 });

  try {
    await r.render(content);
  } catch (err: unknown) {
    if (seq !== renderSeq) return;
    console.error('Page render error', err);
    toast(errorMessage(err) || 'Failed to load page', 'error');
  }
}
