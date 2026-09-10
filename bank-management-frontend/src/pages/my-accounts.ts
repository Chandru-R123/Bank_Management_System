import { accounts, transactions, customers } from '../api';
import type { Account, Transaction, Customer } from '../api';
import { toast, showModal, formatCurrency, formatDate } from '../utils';

export async function renderMyAccounts(container: HTMLElement) {
  const username = localStorage.getItem('username') || 'Customer';

  container.innerHTML = `
    <div class="page">

      <!-- Welcome Banner -->
      <div class="welcome-banner">
        <div>
          <div class="welcome-greeting">Good day, ${username} 👋</div>
          <div class="welcome-sub">Here's an overview of your accounts</div>
        </div>
        <div class="welcome-date">${new Date().toLocaleDateString('en-IN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</div>
      </div>

      <!-- Summary Stats -->
      <div class="stat-grid" id="customer-stats">
        <div class="card stat-card">
          <div class="stat-icon stat-icon-blue">🏦</div>
          <div>
            <div class="card-title">Total Accounts</div>
            <div class="card-value" id="stat-total-accounts">—</div>
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon stat-icon-green">💰</div>
          <div>
            <div class="card-title">Total Balance</div>
            <div class="card-value" id="stat-total-balance">—</div>
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon stat-icon-purple">📈</div>
          <div>
            <div class="card-title">Total Credits</div>
            <div class="card-value text-success" id="stat-credits">—</div>
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon stat-icon-red">📉</div>
          <div>
            <div class="card-title">Total Debits</div>
            <div class="card-value text-danger" id="stat-debits">—</div>
          </div>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="section-title">Quick Actions</div>
      <div class="quick-actions" id="quick-actions">
        <div class="loading">Loading accounts...</div>
      </div>

      <!-- My Account Cards -->
      <div class="section-title">My Accounts</div>
      <div id="account-cards"><div class="loading">Loading...</div></div>

      <!-- Recent Transactions -->
      <div class="section-title" style="margin-top:24px">Recent Transactions</div>
      <div class="card" id="recent-tx-card">
        <div class="loading">Loading...</div>
      </div>

    </div>
  `;

  await loadAll();

  async function loadAll() {
    try {
      // Fetch customer profile, accounts, and transactions in parallel
      const [profileResult, acctList] = await Promise.all([
        customers.getMe().catch(() => null as Customer | null),
        accounts.getMy(),
      ]);

      // Update the greeting with the real customer name if available
      if (profileResult) {
        const greetingEl = document.querySelector('.welcome-greeting');
        if (greetingEl) {
          greetingEl.textContent = `Good day, ${profileResult.name} 👋`;
        }
      }

      const txArrays = await Promise.allSettled(
        acctList.map(a => transactions.getByAccount(a.accountId))
      );
      const allTx = txArrays.flatMap(r => r.status === 'fulfilled' ? r.value : []);

      renderStats(acctList, allTx);
      renderQuickActions(acctList);
      renderAccountCards(acctList);
      renderRecentTransactions(allTx);
    } catch (err: unknown) {
      document.getElementById('account-cards')!.innerHTML =
        `<div class="card"><div class="empty text-danger">${(err as Error).message}</div></div>`;
    }
  }

  function renderStats(acctList: Account[], allTx: Transaction[]) {
    document.getElementById('stat-total-accounts')!.textContent = String(acctList.length);
    const total = acctList.reduce((s, a) => s + a.balance, 0);
    document.getElementById('stat-total-balance')!.textContent = formatCurrency(total);

    const credits = allTx
      .filter(t => isCredit(t.transactionType))
      .reduce((s, t) => s + t.amount, 0);
    const debits = allTx
      .filter(t => !isCredit(t.transactionType))
      .reduce((s, t) => s + t.amount, 0);

    document.getElementById('stat-credits')!.textContent = formatCurrency(credits);
    document.getElementById('stat-debits')!.textContent = formatCurrency(debits);
  }

  function renderQuickActions(acctList: Account[]) {
    const el = document.getElementById('quick-actions')!;
    if (acctList.length === 0) {
      el.innerHTML = '<p class="text-muted">No accounts available.</p>';
      return;
    }

    el.innerHTML = `
      <div class="qa-card" id="qa-deposit">
        <div class="qa-icon" style="background:#d1fae5;color:#065f46">⬇️</div>
        <div class="qa-label">Deposit</div>
        <div class="qa-sub">Add funds to account</div>
      </div>
      <div class="qa-card" id="qa-withdraw">
        <div class="qa-icon" style="background:#fee2e2;color:#991b1b">⬆️</div>
        <div class="qa-label">Withdraw</div>
        <div class="qa-sub">Take out funds</div>
      </div>
      <div class="qa-card" id="qa-transfer">
        <div class="qa-icon" style="background:#dbeafe;color:#1e40af">🔄</div>
        <div class="qa-label">Transfer</div>
        <div class="qa-sub">Move between accounts</div>
      </div>
      <div class="qa-card" id="qa-history">
        <div class="qa-icon" style="background:#f3e8ff;color:#6b21a8">📋</div>
        <div class="qa-label">History</div>
        <div class="qa-sub">View transactions</div>
      </div>
    `;

    el.querySelector('#qa-deposit')!.addEventListener('click', () =>
      openPickAccountModal('Deposit', acctList, loadAll));
    el.querySelector('#qa-withdraw')!.addEventListener('click', () =>
      openPickAccountModal('Withdraw', acctList, loadAll));
    el.querySelector('#qa-transfer')!.addEventListener('click', () =>
      openTransferModal(acctList[0], acctList, loadAll));
    el.querySelector('#qa-history')!.addEventListener('click', () =>
      openPickHistoryModal(acctList));
  }

  function renderAccountCards(acctList: Account[]) {
    const el = document.getElementById('account-cards')!;
    if (acctList.length === 0) {
      el.innerHTML = '<div class="card"><div class="empty">No accounts linked to your profile.</div></div>';
      return;
    }

    el.innerHTML = `<div class="account-grid">${acctList.map(a => `
      <div class="account-card">
        <div class="account-card-top">
          <div>
            <div class="account-card-label">Account Number</div>
            <div class="account-card-number">${a.accountNumber}</div>
          </div>
          <span class="badge ${accountTypeBadge(a.accountType)}">${a.accountType}</span>
        </div>
        <div class="account-card-balance">
          <div class="account-card-label">Available Balance</div>
          <div class="account-card-amount">${formatCurrency(a.balance)}</div>
        </div>
        <div class="account-card-holder">
          <span>👤</span> ${a.customerName}
        </div>
        <div class="account-card-actions">
          <button class="btn btn-success btn-sm" data-deposit="${a.accountId}">Deposit</button>
          <button class="btn btn-danger btn-sm" data-withdraw="${a.accountId}">Withdraw</button>
          <button class="btn btn-primary btn-sm" data-transfer="${a.accountId}">Transfer</button>
          <button class="btn btn-outline btn-sm" data-history="${a.accountId}">History</button>
        </div>
      </div>
    `).join('')}</div>`;

    el.querySelectorAll('[data-deposit]').forEach(btn => {
      const id = parseInt((btn as HTMLElement).dataset.deposit!);
      const acc = acctList.find(a => a.accountId === id)!;
      btn.addEventListener('click', () => openTransactionModal('Deposit', acc, loadAll));
    });
    el.querySelectorAll('[data-withdraw]').forEach(btn => {
      const id = parseInt((btn as HTMLElement).dataset.withdraw!);
      const acc = acctList.find(a => a.accountId === id)!;
      btn.addEventListener('click', () => openTransactionModal('Withdraw', acc, loadAll));
    });
    el.querySelectorAll('[data-transfer]').forEach(btn => {
      const id = parseInt((btn as HTMLElement).dataset.transfer!);
      const acc = acctList.find(a => a.accountId === id)!;
      btn.addEventListener('click', () => openTransferModal(acc, acctList, loadAll));
    });
    el.querySelectorAll('[data-history]').forEach(btn => {
      const id = parseInt((btn as HTMLElement).dataset.history!);
      btn.addEventListener('click', () => openHistory(id));
    });
  }

  function renderRecentTransactions(allTx: Transaction[]) {
    const card = document.getElementById('recent-tx-card')!;
    const recent = [...allTx]
      .sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime())
      .slice(0, 8);

    if (recent.length === 0) {
      card.innerHTML = '<div class="empty">No transactions yet.</div>';
      return;
    }

    card.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead><tr>
            <th>Account</th><th>Type</th><th>Amount</th><th>Date & Time</th>
          </tr></thead>
          <tbody>
            ${recent.map(t => `
              <tr>
                <td><code style="font-size:0.8125rem">${t.accountNumber}</code></td>
                <td>
                  <span class="tx-type-dot ${isCredit(t.transactionType) ? 'dot-green' : 'dot-red'}"></span>
                  ${t.transactionType}
                </td>
                <td class="${isCredit(t.transactionType) ? 'text-success' : 'text-danger'}" style="font-weight:600">
                  ${isCredit(t.transactionType) ? '+' : '-'}${formatCurrency(t.amount)}
                </td>
                <td class="text-muted" style="font-size:0.8125rem">${formatDate(t.transactionDate)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  }
}

// ── Helpers ──────────────────────────────────────────────

function isCredit(type: string) {
  const t = type.toUpperCase();
  return t.includes('DEPOSIT') || t.includes('CREDIT') || t.includes('TRANSFER_IN');
}

function accountTypeBadge(type: string) {
  if (type === 'SAVINGS') return 'badge-green';
  if (type === 'CURRENT') return 'badge-blue';
  return 'badge-yellow';
}

// ── Modals ────────────────────────────────────────────────

function openPickAccountModal(
  type: 'Deposit' | 'Withdraw',
  acctList: Account[],
  onDone: () => void
) {
  const overlay = showModal(`
    <div class="modal-header">
      <h3>Select Account to ${type}</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label>Account</label>
        <select id="pick-account">
          ${acctList.map(a => `<option value="${a.accountId}">${a.accountNumber} — ${formatCurrency(a.balance)}</option>`).join('')}
        </select>
      </div>
      <div class="form-group">
        <label>Amount (₹)</label>
        <input id="pick-amount" type="number" min="1" step="0.01" placeholder="Enter amount" />
      </div>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="cancel-modal">Cancel</button>
      <button class="btn ${type === 'Deposit' ? 'btn-success' : 'btn-danger'} btn-sm" id="confirm-pick">${type}</button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#cancel-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#confirm-pick')!.addEventListener('click', async () => {
    const id = parseInt((document.getElementById('pick-account') as HTMLSelectElement).value);
    const amount = parseFloat((document.getElementById('pick-amount') as HTMLInputElement).value);
    if (!amount || amount <= 0) { toast('Enter a valid amount', 'error'); return; }
    try {
      if (type === 'Deposit') await accounts.deposit(id, amount);
      else await accounts.withdraw(id, amount);
      toast(`${type} successful`, 'success');
      overlay.remove();
      onDone();
    } catch (err: unknown) { toast((err as Error).message, 'error'); }
  });
}

function openTransactionModal(type: 'Deposit' | 'Withdraw', account: Account, onDone: () => void) {
  const overlay = showModal(`
    <div class="modal-header">
      <h3>${type} Funds</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body">
      <div class="tx-account-info">
        <div class="tx-account-num">${account.accountNumber}</div>
        <div class="tx-account-bal">Balance: <strong>${formatCurrency(account.balance)}</strong></div>
      </div>
      <div class="form-group" style="margin-top:16px">
        <label>Amount (₹)</label>
        <input id="tx-amount" type="number" min="1" step="0.01" placeholder="0.00" class="amount-input" />
      </div>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="cancel-modal">Cancel</button>
      <button class="btn ${type === 'Deposit' ? 'btn-success' : 'btn-danger'} btn-sm" id="confirm-tx">
        ${type === 'Deposit' ? '⬇ Deposit' : '⬆ Withdraw'}
      </button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#cancel-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#confirm-tx')!.addEventListener('click', async () => {
    const amount = parseFloat((document.getElementById('tx-amount') as HTMLInputElement).value);
    if (!amount || amount <= 0) { toast('Enter a valid amount', 'error'); return; }
    try {
      if (type === 'Deposit') await accounts.deposit(account.accountId, amount);
      else await accounts.withdraw(account.accountId, amount);
      toast(`${type} of ${formatCurrency(amount)} successful`, 'success');
      overlay.remove();
      onDone();
    } catch (err: unknown) { toast((err as Error).message, 'error'); }
  });
}

function openTransferModal(fromAccount: Account, allAccounts: Account[], onDone: () => void) {
  const others = allAccounts.filter(a => a.accountId !== fromAccount.accountId);
  const overlay = showModal(`
    <div class="modal-header">
      <h3>Transfer Money</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label>From Account</label>
        <select id="transfer-from">
          ${allAccounts.map(a => `<option value="${a.accountId}" ${a.accountId === fromAccount.accountId ? 'selected' : ''}>${a.accountNumber} — ${formatCurrency(a.balance)}</option>`).join('')}
        </select>
      </div>
      <div class="form-group">
        <label>To Account</label>
        <select id="transfer-to">
          <option value="">-- Select destination --</option>
          ${others.map(a => `<option value="${a.accountId}">${a.accountNumber} (${a.customerName})</option>`).join('')}
        </select>
      </div>
      <div class="form-group">
        <label>Amount (₹)</label>
        <input id="transfer-amount" type="number" min="1" step="0.01" placeholder="0.00" class="amount-input" />
      </div>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="cancel-modal">Cancel</button>
      <button class="btn btn-primary btn-sm" id="confirm-transfer">🔄 Transfer</button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#cancel-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#confirm-transfer')!.addEventListener('click', async () => {
    const fromId = parseInt((document.getElementById('transfer-from') as HTMLSelectElement).value);
    const toId = parseInt((document.getElementById('transfer-to') as HTMLSelectElement).value);
    const amount = parseFloat((document.getElementById('transfer-amount') as HTMLInputElement).value);
    if (!toId) { toast('Select a destination account', 'error'); return; }
    if (fromId === toId) { toast('Cannot transfer to the same account', 'error'); return; }
    if (!amount || amount <= 0) { toast('Enter a valid amount', 'error'); return; }
    try {
      await accounts.transfer(fromId, toId, amount);
      toast(`Transferred ${formatCurrency(amount)} successfully`, 'success');
      overlay.remove();
      onDone();
    } catch (err: unknown) { toast((err as Error).message, 'error'); }
  });
}

function openPickHistoryModal(acctList: Account[]) {
  const overlay = showModal(`
    <div class="modal-header">
      <h3>View Transaction History</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label>Select Account</label>
        <select id="history-account">
          ${acctList.map(a => `<option value="${a.accountId}">${a.accountNumber}</option>`).join('')}
        </select>
      </div>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="cancel-modal">Cancel</button>
      <button class="btn btn-primary btn-sm" id="view-history">View History</button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#cancel-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#view-history')!.addEventListener('click', () => {
    const id = parseInt((document.getElementById('history-account') as HTMLSelectElement).value);
    overlay.remove();
    openHistory(id);
  });
}

async function openHistory(accountId: number) {
  const overlay = showModal(`
    <div class="modal-header">
      <h3>Transaction History</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body" style="padding:0;max-height:420px;overflow-y:auto">
      <div id="history-content" style="padding:16px"><div class="loading">Loading...</div></div>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="close-history">Close</button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#close-history')!.addEventListener('click', () => overlay.remove());

  const content = document.getElementById('history-content')!;
  try {
    const list = await transactions.getByAccount(accountId);
    if (list.length === 0) { content.innerHTML = '<div class="empty">No transactions yet.</div>'; return; }

    const sorted = [...list].sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime());
    content.innerHTML = `
      <table style="width:100%;border-collapse:collapse;font-size:0.875rem">
        <thead><tr style="background:var(--bg)">
          <th style="padding:10px;text-align:left;border-bottom:1px solid var(--border)">Type</th>
          <th style="padding:10px;text-align:left;border-bottom:1px solid var(--border)">Amount</th>
          <th style="padding:10px;text-align:left;border-bottom:1px solid var(--border)">Date</th>
        </tr></thead>
        <tbody>
          ${sorted.map(t => `
            <tr style="border-bottom:1px solid var(--border)">
              <td style="padding:10px">
                <div style="display:flex;align-items:center;gap:8px">
                  <span style="font-size:1.1rem">${isCredit(t.transactionType) ? '⬇️' : '⬆️'}</span>
                  <span class="badge ${isCredit(t.transactionType) ? 'badge-green' : 'badge-red'}">${t.transactionType}</span>
                </div>
              </td>
              <td style="padding:10px;font-weight:600" class="${isCredit(t.transactionType) ? 'text-success' : 'text-danger'}">
                ${isCredit(t.transactionType) ? '+' : '-'}${formatCurrency(t.amount)}
              </td>
              <td style="padding:10px;color:var(--muted);font-size:0.8125rem">${formatDate(t.transactionDate)}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;
  } catch (err: unknown) {
    content.innerHTML = `<div class="empty text-danger">${(err as Error).message}</div>`;
  }
}
