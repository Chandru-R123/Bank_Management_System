import { accounts, customers, transactions, isAdmin } from '../api';
import type { Account, AccountRequest, Customer } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, openMenu, confirmDialog, withBusy, formatCurrency, formatDay,
  emptyState, errorState, skeletonRows, accountTypeBadge, accountTypeLabel, statusBadge,
  debounce, downloadCsv, parseAmount, errorMessage, ACCOUNT_TYPES,
} from '../utils';
import type { MenuItem } from '../utils';
import { openStatement, txTableHtml } from '../components/tx';
import { openCashModal, openTransferModal, canCredit, canDebit, SAVINGS_MIN_BALANCE } from '../components/money';

export async function renderAccounts(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Accounts</h1>
          <p class="page-desc">Open accounts, move money and manage account status.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="acc-export" disabled>${icon('download', 16)} Export</button>
          <button class="btn btn-secondary" id="acc-transfer">${icon('transfer', 16)} Transfer</button>
          <button class="btn btn-primary" id="acc-add">${icon('plus', 16)} Open account</button>
        </div>
      </div>

      <div class="card">
        <div class="filters">
          <div class="search">
            ${icon('search', 16)}
            <input class="input" id="acc-search" placeholder="Search account number or holder…" autocomplete="off" />
          </div>
          <select class="select" id="acc-type" style="width:auto">
            <option value="">All types</option>
            ${ACCOUNT_TYPES.map((t) => `<option value="${t.value}">${t.label}</option>`).join('')}
          </select>
          <div class="segmented" id="acc-status">
            <button class="active" data-v="">All</button>
            <button data-v="ACTIVE">Active</button>
            <button data-v="FROZEN">Frozen</button>
            <button data-v="CLOSED">Closed</button>
          </div>
          <div class="spacer"></div>
          <span class="text-sm text-muted" id="acc-count"></span>
        </div>
        <div id="acc-content">${skeletonRows(6)}</div>
      </div>
    </div>`;

  let list: Account[] = [];
  let query = '';
  let typeFilter = '';
  let statusFilter = '';

  const content = document.getElementById('acc-content')!;
  const exportBtn = document.getElementById('acc-export') as HTMLButtonElement;

  document.getElementById('acc-add')!.addEventListener('click', () => openAccountForm(null, loadData));
  document.getElementById('acc-transfer')!.addEventListener('click', () =>
    openTransferModal(list, null, loadData, { withOwner: true }));
  document.getElementById('acc-search')!.addEventListener('input', debounce((e: Event) => {
    query = (e.target as HTMLInputElement).value.trim().toLowerCase();
    render();
  }, 150));
  document.getElementById('acc-type')!.addEventListener('change', (e) => {
    typeFilter = (e.target as HTMLSelectElement).value;
    render();
  });
  document.querySelectorAll<HTMLButtonElement>('#acc-status button').forEach((b) => {
    b.addEventListener('click', () => {
      statusFilter = b.dataset.v ?? '';
      document.querySelectorAll('#acc-status button').forEach((x) => x.classList.toggle('active', x === b));
      render();
    });
  });
  exportBtn.addEventListener('click', () => {
    downloadCsv('accounts.csv',
      ['Account ID', 'Account number', 'Type', 'Status', 'Holder', 'Customer ID', 'Balance', 'Opened on'],
      visible().map((a) => [a.accountId, a.accountNumber, accountTypeLabel(a.accountType), a.status,
        a.customerName, a.customerId ?? '', a.balance.toFixed(2), a.createdAt ? formatDay(a.createdAt) : '']));
  });

  await loadData();

  function visible(): Account[] {
    return list.filter((a) => {
      if (typeFilter && a.accountType !== typeFilter) return false;
      if (statusFilter && a.status !== statusFilter) return false;
      if (!query) return true;
      return a.accountNumber.toLowerCase().includes(query) || (a.customerName ?? '').toLowerCase().includes(query);
    });
  }

  async function loadData() {
    if (!document.getElementById('acc-content')) return;
    content.innerHTML = skeletonRows(6);
    try {
      list = await accounts.getAll();
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', loadData);
    }
  }

  function render() {
    const rows = visible();
    const totalBal = rows.filter((a) => a.status !== 'CLOSED').reduce((s, a) => s + a.balance, 0);
    document.getElementById('acc-count')!.textContent =
      `${rows.length} account${rows.length === 1 ? '' : 's'} · ${formatCurrency(totalBal)}`;
    exportBtn.disabled = rows.length === 0;

    if (list.length === 0) {
      content.innerHTML = emptyState({
        icon: 'bank', title: 'No accounts yet', text: 'Open the first account for a customer.',
        action: `<button class="btn btn-primary btn-sm" data-empty-add>${icon('plus', 14)} Open account</button>`,
      });
      content.querySelector('[data-empty-add]')!.addEventListener('click', () => openAccountForm(null, loadData));
      return;
    }
    if (rows.length === 0) {
      content.innerHTML = emptyState({ icon: 'search', title: 'No matches', text: 'Try a different search or filter.' });
      return;
    }

    content.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Account</th><th>Holder</th><th>Type</th><th>Status</th>
            <th class="num">Balance</th><th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${rows.map((a) => `
              <tr class="clickable" data-id="${a.accountId}">
                <td>
                  <div class="cell-primary mono">${esc(a.accountNumber)}</div>
                  <div class="cell-sub">${a.createdAt ? `Opened ${esc(formatDay(a.createdAt))}` : `ID ${a.accountId}`}</div>
                </td>
                <td>${esc(a.customerName ?? '—')}</td>
                <td>${accountTypeBadge(a.accountType)}</td>
                <td>${statusBadge(a.status)}</td>
                <td class="num fw-600">${formatCurrency(a.balance)}</td>
                <td class="actions">
                  <button class="btn-icon" data-act="statement" title="Statement">${icon('file', 16)}</button>
                  <button class="btn-icon" data-act="deposit" title="Deposit" ${canCredit(a) ? '' : 'disabled'}>${icon('arrowIn', 16)}</button>
                  <button class="btn-icon" data-act="withdraw" title="Withdraw" ${canDebit(a) ? '' : 'disabled'}>${icon('arrowOut', 16)}</button>
                  <button class="btn-icon" data-act="more" title="More">${icon('menu', 16)}</button>
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    content.querySelectorAll<HTMLTableRowElement>('tr[data-id]').forEach((tr) => {
      const acc = list.find((a) => a.accountId === Number(tr.dataset.id))!;
      tr.addEventListener('click', (e) => {
        const btn = (e.target as HTMLElement).closest<HTMLButtonElement>('button[data-act]');
        if (!btn) {
          openAccountDrawer(acc, list, loadData);
          return;
        }
        e.stopPropagation();
        switch (btn.dataset.act) {
          case 'statement': openStatement(acc, { showPerformedBy: true }); break;
          case 'deposit':   openCashModal('deposit', [acc], acc.accountId, loadData, { withOwner: true }); break;
          case 'withdraw':  openCashModal('withdraw', [acc], acc.accountId, loadData, { withOwner: true }); break;
          case 'more':      openMenu(btn, accountMenu(acc, list, loadData)); break;
        }
      });
    });
  }
}

// ── Row menu / admin actions ─────────────────────────────────────────────────

function accountMenu(a: Account, all: Account[], onChange: () => void): (MenuItem | 'divider')[] {
  const items: (MenuItem | 'divider')[] = [
    { label: 'View details', icon: 'eye', onClick: () => openAccountDrawer(a, all, onChange) },
    { label: 'Transfer from this account', icon: 'transfer', disabled: !canDebit(a),
      onClick: () => openTransferModal(all, a.accountId, onChange, { withOwner: true }) },
  ];
  if (isAdmin()) {
    items.push(
      'divider',
      { label: 'Edit account', icon: 'pencil', disabled: a.status === 'CLOSED',
        onClick: () => openAccountForm(a, onChange) },
      a.status === 'FROZEN'
        ? { label: 'Unfreeze account', icon: 'unlock', onClick: () => void unfreeze(a, onChange) }
        : { label: 'Freeze account', icon: 'snowflake', disabled: a.status !== 'ACTIVE',
            onClick: () => void freeze(a, onChange) },
      { label: 'Close account', icon: 'closeCircle', danger: true, disabled: a.status === 'CLOSED',
        onClick: () => void closeAccount(a, onChange) },
    );
  }
  return items;
}

async function freeze(a: Account, onChange: () => void) {
  const ok = await confirmDialog({
    title: `Freeze ${a.accountNumber}?`,
    message: 'No deposits, withdrawals or transfers will be possible until the account is unfrozen.',
    confirmText: 'Freeze account',
    tone: 'danger',
  });
  if (!ok) return;
  try {
    await accounts.freeze(a.accountId);
    toast(`${a.accountNumber} is now frozen`, 'success');
    onChange();
  } catch (err) { toast(errorMessage(err), 'error'); }
}

async function unfreeze(a: Account, onChange: () => void) {
  try {
    await accounts.unfreeze(a.accountId);
    toast(`${a.accountNumber} is active again`, 'success');
    onChange();
  } catch (err) { toast(errorMessage(err), 'error'); }
}

async function closeAccount(a: Account, onChange: () => void) {
  const ok = await confirmDialog({
    title: `Close ${a.accountNumber}?`,
    message: a.balance > 0
      ? `The remaining balance of ${formatCurrency(a.balance)} will be paid out to ${a.customerName} and recorded as a closure payout. Closed accounts cannot be reopened.`
      : 'Closed accounts cannot be reopened. The transaction history is kept for records.',
    confirmText: 'Close account',
    tone: 'danger',
  });
  if (!ok) return;
  try {
    await accounts.close(a.accountId);
    toast(`${a.accountNumber} has been closed`, 'success');
    onChange();
  } catch (err) { toast(errorMessage(err), 'error'); }
}

// ── Account drawer ───────────────────────────────────────────────────────────

function openAccountDrawer(a: Account, all: Account[], onChange: () => void) {
  const admin = isAdmin();
  const m = openModal({
    drawer: true,
    autofocus: false,
    title: 'Account details',
    subtitle: a.accountNumber,
    body: `
      <div class="bank-card type-${esc(a.accountType)} status-${esc(a.status)}">
        <div class="bank-card-top">
          <div>
            <div class="bank-card-type">${esc(accountTypeLabel(a.accountType))}</div>
            <div class="bank-card-number">${esc(a.accountNumber)}</div>
          </div>
          <span class="badge">${esc(a.status)}</span>
        </div>
        <div>
          <div class="bank-card-balance-label">Balance</div>
          <div class="bank-card-balance">${formatCurrency(a.balance)}</div>
          <div class="text-sm" style="opacity:.75;margin-top:4px">${esc(a.customerName ?? '')}</div>
        </div>
      </div>

      <div class="action-grid mt-4">
        <button class="action-tile" data-a="deposit" ${canCredit(a) ? '' : 'disabled'}>${icon('arrowIn', 18)}Deposit</button>
        <button class="action-tile" data-a="withdraw" ${canDebit(a) ? '' : 'disabled'}>${icon('arrowOut', 18)}Withdraw</button>
        <button class="action-tile" data-a="transfer" ${canDebit(a) ? '' : 'disabled'}>${icon('transfer', 18)}Transfer</button>
        <button class="action-tile" data-a="statement">${icon('file', 18)}Statement</button>
      </div>

      ${a.status === 'FROZEN' ? `<div class="callout callout-info mt-4">${icon('snowflake', 16)}<div>This account is frozen. All debits and credits are blocked.</div></div>` : ''}
      ${a.status === 'CLOSED' ? `<div class="callout callout-warn mt-4">${icon('info', 16)}<div>This account is closed. History is retained for records.</div></div>` : ''}
      ${a.accountType === 'FIXED_DEPOSIT' && a.status === 'ACTIVE' ? `<div class="callout callout-info mt-4">${icon('lock', 16)}<div>Fixed Deposit — no top-ups or withdrawals. Funds are released when the deposit is closed.</div></div>` : ''}

      <div class="card mt-4"><div class="card-body">
        <dl class="dl">
          <dt>Account ID</dt><dd>${a.accountId}</dd>
          <dt>Holder</dt><dd>${esc(a.customerName ?? '—')}${a.customerId ? ` <span class="text-muted">· #${a.customerId}</span>` : ''}</dd>
          <dt>Type</dt><dd>${accountTypeBadge(a.accountType)}</dd>
          <dt>Status</dt><dd>${statusBadge(a.status)}</dd>
          <dt>Opened</dt><dd>${a.createdAt ? esc(formatDay(a.createdAt)) : '—'}</dd>
        </dl>
      </div></div>

      <div class="section-head section"><div class="section-title">Recent activity</div></div>
      <div class="card" data-recent>${skeletonRows(4)}</div>`,
    footer: admin && a.status !== 'CLOSED' ? `
      <button class="btn btn-danger-soft" data-a="close">${icon('closeCircle', 16)} Close account</button>
      <div style="flex:1"></div>
      ${a.status === 'FROZEN'
        ? `<button class="btn btn-secondary" data-a="unfreeze">${icon('unlock', 16)} Unfreeze</button>`
        : `<button class="btn btn-secondary" data-a="freeze">${icon('snowflake', 16)} Freeze</button>`}
      <button class="btn btn-primary" data-a="edit">${icon('pencil', 16)} Edit</button>`
      : '<button class="btn btn-secondary" data-close>Close</button>',
  });

  const done = () => { m.close(); onChange(); };
  m.el.querySelectorAll<HTMLButtonElement>('[data-a]').forEach((b) => {
    b.addEventListener('click', () => {
      switch (b.dataset.a) {
        case 'deposit':   openCashModal('deposit', [a], a.accountId, done, { withOwner: true }); break;
        case 'withdraw':  openCashModal('withdraw', [a], a.accountId, done, { withOwner: true }); break;
        case 'transfer':  openTransferModal(all, a.accountId, done, { withOwner: true }); break;
        case 'statement': openStatement(a, { showPerformedBy: true }); break;
        case 'edit':      m.close(); openAccountForm(a, onChange); break;
        case 'freeze':    void freeze(a, done); break;
        case 'unfreeze':  void unfreeze(a, done); break;
        case 'close':     void closeAccount(a, done); break;
      }
    });
  });

  const recent = m.el.querySelector<HTMLElement>('[data-recent]')!;
  transactions.getByAccount(a.accountId)
    .then((list) => {
      recent.innerHTML = list.length
        ? txTableHtml(list.slice(0, 8), { showBalance: true, compact: true })
        : emptyState({ title: 'No activity yet' });
    })
    .catch((err) => { recent.innerHTML = errorState(errorMessage(err)); });
}

// ── Open / edit form ─────────────────────────────────────────────────────────

export async function openAccountForm(existing: Account | null, onSave: () => void, presetCustomerId?: number) {
  const isEdit = existing !== null;

  let custList: Customer[] = [];
  try {
    custList = (await customers.getAll()).sort((x, y) => x.name.localeCompare(y.name));
  } catch (err) {
    toast(`Couldn't load customers: ${errorMessage(err)}`, 'error');
    return;
  }
  if (custList.length === 0) {
    toast('Create a customer first — every account needs an owner.', 'info');
    return;
  }

  const selectedCustomer = existing?.customerId ?? presetCustomerId ?? null;
  const m = openModal({
    title: isEdit ? 'Edit account' : 'Open a new account',
    subtitle: isEdit ? existing.accountNumber : 'The opening deposit is recorded as the first transaction.',
    body: `
      <form class="form-grid" novalidate>
        <div class="field" data-field="customer">
          <label class="field-label" for="af-customer">Account holder<span class="req">*</span></label>
          <select id="af-customer" class="select">
            ${selectedCustomer === null ? '<option value="">Select a customer…</option>' : ''}
            ${custList.map((c) => `<option value="${c.customerId}" ${c.customerId === selectedCustomer ? 'selected' : ''}>${esc(c.name)} · #${c.customerId} · ${esc(c.email)}</option>`).join('')}
          </select>
          <div class="field-error" data-error></div>
        </div>

        <div class="form-row">
          <div class="field">
            <label class="field-label" for="af-type">Account type<span class="req">*</span></label>
            <select id="af-type" class="select" ${isEdit && existing.accountType === 'FIXED_DEPOSIT' ? 'disabled' : ''}>
              ${ACCOUNT_TYPES
                .filter((t) => !isEdit || existing.accountType === 'FIXED_DEPOSIT' || t.value !== 'FIXED_DEPOSIT')
                .map((t) => `<option value="${t.value}" ${(existing?.accountType ?? 'SAVINGS') === t.value ? 'selected' : ''}>${t.label}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="af-number">Account number</label>
            <input id="af-number" class="input mono" maxlength="30" placeholder="Auto-generated" value="${esc(existing?.accountNumber ?? '')}" ${isEdit ? 'disabled' : ''} style="text-transform:uppercase" />
          </div>
        </div>

        ${isEdit ? `
          <div class="summary-box">
            <div><div class="label">Current balance</div><div class="value">${formatCurrency(existing.balance)}</div></div>
            <div class="text-sm text-muted" style="text-align:right;max-width:220px">Balances change only through deposits, withdrawals and transfers.</div>
          </div>` : `
          <div class="field" data-field="amount">
            <label class="field-label" for="af-balance">Opening deposit</label>
            <div class="input-group">
              <span class="input-prefix">₹</span>
              <input id="af-balance" class="input" inputmode="decimal" placeholder="0.00" autocomplete="off" />
            </div>
            <div class="field-hint" data-type-hint></div>
            <div class="field-error" data-error></div>
          </div>`}
      </form>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>${isEdit ? 'Save changes' : 'Open account'}</button>`,
  });

  const root = m.el;
  const typeEl = root.querySelector<HTMLSelectElement>('#af-type')!;
  const custEl = root.querySelector<HTMLSelectElement>('#af-customer')!;
  const balanceEl = root.querySelector<HTMLInputElement>('#af-balance');
  const numberEl = root.querySelector<HTMLInputElement>('#af-number')!;
  const hintEl = root.querySelector<HTMLElement>('[data-type-hint]');

  const setErr = (field: string, msg: string | null) => {
    const f = root.querySelector<HTMLElement>(`[data-field="${field}"]`);
    if (!f) return;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };

  const updateHint = () => {
    if (!hintEl || !balanceEl) return;
    const t = typeEl.value;
    hintEl.textContent = t === 'SAVINGS'
      ? `Savings accounts require a minimum balance of ${formatCurrency(SAVINGS_MIN_BALANCE)}.`
      : t === 'FIXED_DEPOSIT'
        ? 'Fixed Deposits are funded once at opening — no later top-ups or withdrawals.'
        : 'Current accounts have no minimum balance.';
    if (t === 'SAVINGS' && !balanceEl.value) balanceEl.value = String(SAVINGS_MIN_BALANCE);
  };
  updateHint();
  typeEl.addEventListener('change', updateHint);
  custEl.addEventListener('change', () => setErr('customer', null));
  balanceEl?.addEventListener('input', () => setErr('amount', null));

  const save = root.querySelector<HTMLButtonElement>('[data-save]')!;
  save.addEventListener('click', () => withBusy(save, async () => {
    const customerId = Number(custEl.value);
    if (!customerId) { setErr('customer', 'Choose the account holder'); return; }
    const accountType = typeEl.value;

    const data: AccountRequest = { accountType, customerId };
    if (isEdit) {
      data.accountNumber = existing.accountNumber;
    } else {
      const raw = balanceEl!.value.trim();
      const amount = /^0*(\.0{0,2})?$/.test(raw) ? 0 : parseAmount(raw);
      if (amount === null) { setErr('amount', 'Enter a valid amount (max 2 decimals)'); return; }
      if (accountType === 'SAVINGS' && amount < SAVINGS_MIN_BALANCE) {
        setErr('amount', `Minimum opening balance for Savings is ${formatCurrency(SAVINGS_MIN_BALANCE)}`); return;
      }
      if (accountType === 'FIXED_DEPOSIT' && amount <= 0) {
        setErr('amount', 'A Fixed Deposit needs an opening amount'); return;
      }
      data.balance = amount;
      const num = numberEl.value.trim().toUpperCase();
      if (num) data.accountNumber = num;
    }

    try {
      if (isEdit) {
        await accounts.update(existing.accountId, data);
        toast('Account updated', 'success');
      } else {
        const created = await accounts.create(data);
        toast(`Account ${created.accountNumber} opened for ${created.customerName}`, 'success');
      }
      m.close();
      onSave();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}
