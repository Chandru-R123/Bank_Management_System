import './style.css';
import keycloak from './keycloak';
import { hasRole, getUsername } from './api';
import { toast } from './utils';
import { renderDashboard }    from './pages/dashboard';
import { renderCustomers }    from './pages/customers';
import { renderAccounts }     from './pages/accounts';
import { renderTransactions } from './pages/transactions';
import { renderMyAccounts }   from './pages/my-accounts';

const app = document.getElementById('app')!;

// ── Bootstrap ─────────────────────────────────────────────────────────────────
//
//  keycloak.init() redirects to Keycloak login if the user has no session.
//  After a successful login Keycloak redirects back here with a code that
//  keycloak-js exchanges for tokens transparently (PKCE).
//
keycloak
  .init({
    onLoad:           'login-required',   // redirect immediately if not logged in
    checkLoginIframe: false,              // avoid iframe issues with some browsers
    pkceMethod:       'S256',
  })
  .then((authenticated) => {
    if (!authenticated) {
      // Should not happen with login-required, but guard anyway
      keycloak.login();
      return;
    }

    // Set up silent token refresh 60 s before expiry
    setInterval(() => {
      keycloak.updateToken(60).catch(() => {
        console.warn('Token refresh failed — logging out');
        keycloak.logout();
      });
    }, 30_000);

    // Start the SPA router
    window.addEventListener('hashchange', route);
    route();
  })
  .catch((err) => {
    console.error('Keycloak init failed', err);
    app.innerHTML = `
      <div style="padding:40px;color:red;font-family:monospace">
        <h2>Keycloak connection failed</h2>
        <p>Make sure the stack is running: <strong>docker compose up --build</strong></p>
        <p>Then open: <strong>http://localhost:8080</strong></p>
        <pre>${String(err)}</pre>
      </div>`;
  });

// ── Logout ────────────────────────────────────────────────────────────────────

function logout() {
  keycloak.logout({ redirectUri: window.location.origin + '/' });
}

// ── Shell ─────────────────────────────────────────────────────────────────────

const pageTitles: Record<string, string> = {
  dashboard:    'Dashboard',
  customers:    'Customers',
  accounts:     'Accounts',
  transactions: 'Transactions',
  'my-accounts': 'My Accounts',
};

function buildShell(activePage: string): HTMLElement {
  const isAdmin    = hasRole('ADMIN');
  const isEmployee = hasRole('EMPLOYEE');
  const username   = getUsername();

  // Determine the displayed role badge
  const roleBadge = isAdmin ? 'ADMIN' : isEmployee ? 'EMPLOYEE' : 'CUSTOMER';

  const adminNav = `
    <a href="#dashboard"    class="${activePage === 'dashboard'    ? 'active' : ''}">📊 Dashboard</a>
    <a href="#customers"    class="${activePage === 'customers'    ? 'active' : ''}">👥 Customers</a>
    <a href="#accounts"     class="${activePage === 'accounts'     ? 'active' : ''}">🏦 Accounts</a>
    <a href="#transactions" class="${activePage === 'transactions' ? 'active' : ''}">📋 Transactions</a>
  `;

  const employeeNav = `
    <a href="#customers"    class="${activePage === 'customers'    ? 'active' : ''}">👥 Customers</a>
    <a href="#accounts"     class="${activePage === 'accounts'     ? 'active' : ''}">🏦 Accounts</a>
    <a href="#transactions" class="${activePage === 'transactions' ? 'active' : ''}">📋 Transactions</a>
  `;

  const customerNav = `
    <a href="#my-accounts" class="${activePage === 'my-accounts' ? 'active' : ''}">🏦 My Accounts</a>
  `;

  const nav = isAdmin ? adminNav : isEmployee ? employeeNav : customerNav;

  const shell = document.createElement('div');
  shell.className = 'app-shell';
  shell.innerHTML = `
    <aside class="sidebar">
      <div class="sidebar-header">
        <h2>🏦 State Bank</h2>
        <small>Coimbatore, Tamil Nadu</small>
      </div>
      <nav>${nav}</nav>
      <div class="sidebar-footer">
        <div style="font-size:0.8125rem;color:#94a3b8;margin-bottom:8px">
          <span class="badge badge-blue" style="font-size:0.7rem">${roleBadge}</span>
          &nbsp;${username}
        </div>
        <button class="btn btn-outline btn-sm" id="logout-btn"
                style="width:100%;color:#e2e8f0;border-color:#475569">
          Logout
        </button>
      </div>
    </aside>
    <div class="main-content">
      <div class="topbar">
        <h1 id="topbar-title">${pageTitles[activePage] ?? activePage}</h1>
      </div>
      <div id="page-content"></div>
    </div>
  `;

  shell.querySelector('#logout-btn')!.addEventListener('click', logout);
  return shell;
}

// ── Router ────────────────────────────────────────────────────────────────────

async function route() {
  const hash = (window.location.hash || '#').replace('#', '') || defaultPage();

  // Enforce role-based default pages
  if (!hash || hash === 'login' || hash === 'register') {
    window.location.hash = `#${defaultPage()}`;
    return;
  }

  // Guard: customers can only see my-accounts
  if (
    !hasRole('ADMIN') &&
    !hasRole('EMPLOYEE') &&
    ['dashboard', 'customers', 'accounts', 'transactions'].includes(hash)
  ) {
    window.location.hash = '#my-accounts';
    return;
  }

  // Guard: admin/employee cannot access my-accounts
  if ((hasRole('ADMIN') || hasRole('EMPLOYEE')) && hash === 'my-accounts') {
    window.location.hash = '#dashboard';
    return;
  }

  app.innerHTML = '';
  const shell = buildShell(hash);
  app.appendChild(shell);
  const content = document.getElementById('page-content')!;

  try {
    switch (hash) {
      case 'dashboard':
        await renderDashboard(content);
        break;
      case 'customers':
        await renderCustomers(content);
        break;
      case 'accounts':
        await renderAccounts(content);
        break;
      case 'transactions':
        await renderTransactions(content);
        break;
      case 'my-accounts':
        await renderMyAccounts(content);
        break;
      default:
        content.innerHTML =
          '<div class="page"><div class="empty">Page not found.</div></div>';
    }
  } catch (err: unknown) {
    console.error('Page render error', err);
    toast((err as Error).message || 'Failed to load page', 'error');
  }
}

function defaultPage(): string {
  if (hasRole('ADMIN') || hasRole('EMPLOYEE')) return 'dashboard';
  return 'my-accounts';
}
