import { customers, accounts, transactions } from '../api';
import { formatCurrency, formatDate } from '../utils';

export async function renderDashboard(container: HTMLElement) {
  const username = localStorage.getItem('username') || 'Admin';

  container.innerHTML = `
    <div class="page">

      <!-- Welcome Banner -->
      <div class="welcome-banner">
        <div>
          <div class="welcome-greeting">Welcome back, ${username} 👋</div>
          <div class="welcome-sub">Here's what's happening at State Bank today</div>
        </div>
        <div class="welcome-date">${new Date().toLocaleDateString('en-IN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</div>
      </div>

      <!-- Stat Cards -->
      <div class="stat-grid">
        <div class="card stat-card">
          <div class="stat-icon stat-icon-blue">👥</div>
          <div>
            <div class="card-title">Total Customers</div>
            <div class="card-value" id="stat-customers">—</div>
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon stat-icon-green">🏦</div>
          <div>
            <div class="card-title">Total Accounts</div>
            <div class="card-value" id="stat-accounts">—</div>
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon stat-icon-purple">💰</div>
          <div>
            <div class="card-title">Total Balance</div>
            <div class="card-value" id="stat-balance">—</div>
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon stat-icon-red">📋</div>
          <div>
            <div class="card-title">Transactions</div>
            <div class="card-value" id="stat-tx">—</div>
          </div>
        </div>
      </div>

      <!-- Two column layout -->
      <div class="dash-grid">

        <!-- Recent Transactions -->
        <div class="card dash-card">
          <div class="dash-card-title">Recent Transactions</div>
          <div id="recent-tx"><div class="loading">Loading...</div></div>
        </div>

        <!-- Account Overview -->
        <div class="card dash-card">
          <div class="dash-card-title">Account Overview</div>
          <div id="account-overview"><div class="loading">Loading...</div></div>
        </div>

      </div>

      <!-- Customer List -->
      <div class="card" style="margin-top:16px">
        <div class="dash-card-title">Customers</div>
        <div id="customer-list"><div class="loading">Loading...</div></div>
      </div>

    </div>
  `;

  // Sync Keycloak registrations into DB before loading stats
  customers.adminSync().catch(() => null);

  const [custResult, acctResult, txResult] = await Promise.allSettled([
    customers.getAll(),
    accounts.getAll(),
    transactions.getAll(),
  ]);

  // Stats
  if (custResult.status === 'fulfilled') {
    document.getElementById('stat-customers')!.textContent = String(custResult.value.length);
  } else {
    document.getElementById('stat-customers')!.textContent = 'Err';
    document.getElementById('stat-customers')!.style.color = 'var(--danger, red)';
    document.getElementById('stat-customers')!.title = (custResult.reason as Error)?.message || 'Failed';
  }
  if (acctResult.status === 'fulfilled') {
    const total = acctResult.value.reduce((s, a) => s + a.balance, 0);
    document.getElementById('stat-accounts')!.textContent = String(acctResult.value.length);
    document.getElementById('stat-balance')!.textContent = formatCurrency(total);
  }
  if (txResult.status === 'fulfilled') {
    document.getElementById('stat-tx')!.textContent = String(txResult.value.length);
  }

  // Recent transactions
  const txEl = document.getElementById('recent-tx')!;
  if (txResult.status === 'fulfilled') {
    const recent = [...txResult.value]
      .sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime())
      .slice(0, 8);
    if (recent.length === 0) {
      txEl.innerHTML = '<div class="empty">No transactions yet.</div>';
    } else {
      txEl.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead><tr><th>Account</th><th>Type</th><th>Amount</th><th>Date</th></tr></thead>
            <tbody>
              ${recent.map(t => `
                <tr>
                  <td><code style="font-size:0.8rem">${t.accountNumber}</code></td>
                  <td><span class="badge ${txBadge(t.transactionType)}">${t.transactionType}</span></td>
                  <td class="${isCredit(t.transactionType) ? 'text-success' : 'text-danger'}" style="font-weight:600">
                    ${isCredit(t.transactionType) ? '+' : '-'}${formatCurrency(t.amount)}
                  </td>
                  <td class="text-muted" style="font-size:0.8rem">${formatDate(t.transactionDate)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }
  } else {
    txEl.innerHTML = '<div class="empty text-danger">Failed to load.</div>';
  }

  // Account overview
  const acctEl = document.getElementById('account-overview')!;
  if (acctResult.status === 'fulfilled') {
    const list = acctResult.value;
    if (list.length === 0) {
      acctEl.innerHTML = '<div class="empty">No accounts yet.</div>';
    } else {
      acctEl.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead><tr><th>Account No.</th><th>Type</th><th>Customer</th><th>Balance</th></tr></thead>
            <tbody>
              ${list.slice(0, 8).map(a => `
                <tr>
                  <td><code style="font-size:0.8rem">${a.accountNumber}</code></td>
                  <td><span class="badge ${acctBadge(a.accountType)}">${a.accountType}</span></td>
                  <td>${a.customerName}</td>
                  <td class="text-success" style="font-weight:600">${formatCurrency(a.balance)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
        ${list.length > 8 ? `<div style="padding:10px;text-align:center;font-size:0.8125rem;color:var(--muted)">+${list.length - 8} more · <a href="#accounts" style="color:var(--primary)">View all</a></div>` : ''}
      `;
    }
  } else {
    acctEl.innerHTML = '<div class="empty text-danger">Failed to load.</div>';
  }

  // Customer list
  const custEl = document.getElementById('customer-list')!;
  if (custResult.status === 'fulfilled') {
    const list = custResult.value;
    if (list.length === 0) {
      custEl.innerHTML = '<div class="empty">No customers yet.</div>';
    } else {
      custEl.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Phone</th><th>Address</th></tr></thead>
            <tbody>
              ${list.map(c => `
                <tr>
                  <td class="text-muted">#${c.customerId}</td>
                  <td style="font-weight:600">${c.name}</td>
                  <td class="text-muted">${c.email}</td>
                  <td>${c.phone}</td>
                  <td class="text-muted">${c.address}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
        <div style="padding:12px 0 0;display:flex;justify-content:flex-end">
          <a href="#customers" class="btn btn-outline btn-sm">Manage Customers →</a>
        </div>
      `;
    }
  } else {
    const errMsg = (custResult.reason as Error)?.message || 'Unknown error';
    custEl.innerHTML = `<div class="empty text-danger">Failed to load customers: ${errMsg}</div>`;
  }
}

function isCredit(type: string) {
  return type.toUpperCase().includes('DEPOSIT') || type.toUpperCase().includes('CREDIT') || type.toUpperCase().includes('TRANSFER_IN');
}

function txBadge(type: string) {
  if (type.includes('DEPOSIT') || type.includes('CREDIT') || type.includes('TRANSFER_IN')) return 'badge-green';
  if (type.includes('WITHDRAW') || type.includes('DEBIT')) return 'badge-red';
  return 'badge-yellow';
}

function acctBadge(type: string) {
  if (type === 'SAVINGS') return 'badge-green';
  if (type === 'CURRENT') return 'badge-blue';
  return 'badge-yellow';
}
