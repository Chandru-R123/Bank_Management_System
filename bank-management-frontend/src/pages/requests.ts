/**
 * Maker Transaction Requests page.
 *
 * MAKER sees all their own requests and their current status.
 * ADMIN can also see all requests here.
 * Allows creating new DEPOSIT / WITHDRAW / TRANSFER requests (no immediate balance change).
 * Allows cancelling PENDING_APPROVAL requests.
 */
import { accounts, txRequests, isAdmin, isMaker } from '../api';
import type { Account, TxRequest, CreateTxRequest } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, withBusy, formatCurrency, formatDate,
  emptyState, errorState, skeletonRows, errorMessage,
  parseAmount, confirmDialog, downloadCsv, formatDay,
} from '../utils';

// ── Status badge ──────────────────────────────────────────────────────────────

function reqStatusBadge(status: string): string {
  const map: Record<string, string> = {
    PENDING_APPROVAL: 'badge-amber',
    APPROVED:         'badge-blue',
    PROCESSING:       'badge-sky',
    SUCCESS:          'badge-green',
    REJECTED:         'badge-red',
    FAILED:           'badge-red',
    CANCELLED:        'badge-neutral',
    REVERSED:         'badge-violet',
  };
  const cls = map[status] ?? 'badge-neutral';
  const label = status.replace(/_/g, ' ');
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}

function reqTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    DEPOSIT:  icon('arrowIn', 15),
    WITHDRAW: icon('arrowOut', 15),
    TRANSFER: icon('transfer', 15),
  };
  return icons[type] ?? icon('receipt', 15);
}

// ── Main render ───────────────────────────────────────────────────────────────

export async function renderRequests(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Transaction Requests</h1>
          <p class="page-desc">Create deposit, withdrawal and transfer requests for Checker approval.</p>
        </div>
        <div class="page-actions">
          ${isMaker() ? `<button class="btn btn-primary" id="req-new">${icon('plus', 16)} New request</button>` : ''}
        </div>
      </div>

      <div class="kpi-grid kpi-grid-4" id="req-stats"></div>

      <div class="card mt-4">
        <div class="filters">
          <div class="segmented" id="req-filter">
            <button class="active" data-v="">All</button>
            <button data-v="PENDING_APPROVAL">Pending</button>
            <button data-v="SUCCESS">Success</button>
            <button data-v="REJECTED">Rejected</button>
            <button data-v="CANCELLED">Cancelled</button>
          </div>
          <div class="spacer"></div>
          <button class="btn btn-secondary btn-sm" id="req-export" disabled>${icon('download', 14)} Export</button>
        </div>
        <div id="req-table">${skeletonRows(5)}</div>
      </div>
    </div>`;

  let list: TxRequest[] = [];
  let filter = '';

  document.getElementById('req-new')?.addEventListener('click', () => openNewRequestModal(loadData));
  document.querySelectorAll<HTMLButtonElement>('#req-filter button').forEach((b) => {
    b.addEventListener('click', () => {
      filter = b.dataset.v ?? '';
      document.querySelectorAll('#req-filter button').forEach((x) => x.classList.toggle('active', x === b));
      render();
    });
  });
  document.getElementById('req-export')?.addEventListener('click', () => exportCsv(visible()));

  await loadData();

  async function loadData() {
    if (!document.getElementById('req-table')) return;
    document.getElementById('req-table')!.innerHTML = skeletonRows(5);
    try {
      // ADMIN sees all; MAKER sees own
      list = isAdmin()
        ? await txRequests.getAll()
        : await txRequests.getMy();
      renderStats();
      render();
    } catch (err) {
      document.getElementById('req-table')!.innerHTML = errorState(errorMessage(err));
      document.getElementById('req-table')!.querySelector('[data-retry]')?.addEventListener('click', loadData);
    }
  }

  function visible(): TxRequest[] {
    if (!filter) return list;
    return list.filter((r) => r.status === filter);
  }

  function renderStats() {
    const el = document.getElementById('req-stats');
    if (!el) return;
    const pending  = list.filter((r) => r.status === 'PENDING_APPROVAL').length;
    const success  = list.filter((r) => r.status === 'SUCCESS').length;
    const rejected = list.filter((r) => r.status === 'REJECTED').length;
    const total    = list.length;
    el.innerHTML = `
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Total</span><span class="kpi-icon">${icon('receipt', 18)}</span></div>
        <div class="kpi-value">${total}</div>
      </div>
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Pending approval</span><span class="kpi-icon tone-amber">${icon('clock', 18)}</span></div>
        <div class="kpi-value text-warn">${pending}</div>
      </div>
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Executed</span><span class="kpi-icon tone-green">${icon('checkCircle', 18)}</span></div>
        <div class="kpi-value text-success">${success}</div>
      </div>
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Rejected</span><span class="kpi-icon tone-red">${icon('alert', 18)}</span></div>
        <div class="kpi-value text-danger">${rejected}</div>
      </div>`;
  }

  function render() {
    const el = document.getElementById('req-table')!;
    const rows = visible();
    const exportBtn = document.getElementById('req-export') as HTMLButtonElement;
    if (exportBtn) exportBtn.disabled = rows.length === 0;

    if (list.length === 0) {
      el.innerHTML = emptyState({ icon: 'receipt', title: 'No requests yet',
        text: isMaker() ? 'Create a deposit, withdrawal or transfer request — a Checker will approve it.' : 'No requests found.' });
      return;
    }
    if (rows.length === 0) {
      el.innerHTML = emptyState({ icon: 'search', title: 'No matches', text: 'Try a different filter.' });
      return;
    }

    el.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Reference</th><th>Type</th><th>Account</th>
            <th class="num">Amount</th><th>Status</th><th>Created</th><th>Maker</th><th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${rows.map((r) => `
              <tr class="clickable" data-id="${r.id}">
                <td><div class="cell-primary mono">${esc(r.requestRef)}</div></td>
                <td><span class="tx-icon">${reqTypeIcon(r.requestType)}</span> ${esc(r.requestType)}</td>
                <td>
                  <div class="cell-primary mono">${esc(r.fromAccountNumber)}</div>
                  ${r.toAccountNumber ? `<div class="cell-sub">→ ${esc(r.toAccountNumber)}</div>` : ''}
                </td>
                <td class="num fw-600">${formatCurrency(r.amount)}</td>
                <td>${reqStatusBadge(r.status)}</td>
                <td>${formatDate(r.createdAt)}</td>
                <td>${esc(r.makerUsername)}</td>
                <td class="actions">
                  <button class="btn-icon" data-act="view" title="View">${icon('eye', 16)}</button>
                  ${r.status === 'PENDING_APPROVAL' && (isAdmin() || r.makerUsername === getUsername())
                    ? `<button class="btn-icon danger" data-act="cancel" title="Cancel">${icon('x', 16)}</button>` : ''}
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    el.querySelectorAll<HTMLTableRowElement>('tr[data-id]').forEach((tr) => {
      const req = list.find((r) => r.id === Number(tr.dataset.id))!;
      tr.addEventListener('click', (e) => {
        const btn = (e.target as HTMLElement).closest<HTMLButtonElement>('button[data-act]');
        if (!btn) { openRequestDetail(req, loadData); return; }
        e.stopPropagation();
        if (btn.dataset.act === 'view')   openRequestDetail(req, loadData);
        if (btn.dataset.act === 'cancel') void cancelRequest(req, loadData);
      });
    });
  }

  function getUsername(): string {
    return (window as unknown as { keycloak?: { tokenParsed?: { preferred_username?: string } } })
      .keycloak?.tokenParsed?.preferred_username ?? '';
  }
}

// ── Create new request modal ──────────────────────────────────────────────────

async function openNewRequestModal(onDone: () => void) {
  let acctList: Account[] = [];
  try {
    acctList = await accounts.getAll();
  } catch (err) {
    toast(`Could not load accounts: ${errorMessage(err)}`, 'error');
    return;
  }

  const activeAccounts = acctList.filter((a) => a.status === 'ACTIVE' && a.accountType !== 'FIXED_DEPOSIT');
  if (activeAccounts.length === 0) {
    toast('No active accounts available for transactions', 'info');
    return;
  }

  const acctOptions = (exclude?: number) => activeAccounts
    .filter((a) => a.accountId !== exclude)
    .map((a) => `<option value="${a.accountId}">${esc(a.accountNumber)} — ${esc(a.customerName)} (${esc(a.accountType)})</option>`)
    .join('');

  const m = openModal({
    title: 'New Transaction Request',
    subtitle: 'This creates a request for Checker approval — no balance change yet.',
    size: 'md',
    body: `
      <form class="form-grid" novalidate>
        <div class="callout callout-info">
          ${icon('info', 16)}
          <div>This request will go to a Checker for approval before execution. Your balance will not change until approved.</div>
        </div>

        <div class="field" data-field="type">
          <label class="field-label">Request type<span class="req">*</span></label>
          <div class="segmented" id="rq-type">
            <button type="button" class="active" data-t="DEPOSIT">${icon('arrowIn', 14)} Deposit</button>
            <button type="button" data-t="WITHDRAW">${icon('arrowOut', 14)} Withdraw</button>
            <button type="button" data-t="TRANSFER">${icon('transfer', 14)} Transfer</button>
          </div>
        </div>

        <div class="field" data-field="from">
          <label class="field-label" for="rq-from">Account<span class="req">*</span></label>
          <select id="rq-from" class="select">
            <option value="">Select account…</option>
            ${acctOptions()}
          </select>
          <div class="field-error" data-error></div>
        </div>

        <div class="field" data-field="to" id="rq-to-field" style="display:none">
          <label class="field-label" for="rq-to">Destination account<span class="req">*</span></label>
          <select id="rq-to" class="select">
            <option value="">Select destination…</option>
            ${acctOptions()}
          </select>
          <div class="field-error" data-error></div>
        </div>

        <div class="field" data-field="amount">
          <label class="field-label" for="rq-amount">Amount<span class="req">*</span></label>
          <div class="input-group">
            <span class="input-prefix">₹</span>
            <input id="rq-amount" class="input" inputmode="decimal" placeholder="0.00" autocomplete="off" />
          </div>
          <div class="field-error" data-error></div>
        </div>

        <div class="field">
          <label class="field-label" for="rq-desc">Description</label>
          <input id="rq-desc" class="input" maxlength="255" placeholder="Optional — shown on the transaction" />
        </div>

        <div class="field">
          <label class="field-label" for="rq-remarks">Remarks for Checker</label>
          <input id="rq-remarks" class="input" maxlength="255" placeholder="Optional notes for the Checker" />
        </div>
      </form>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" id="rq-submit">${icon('send', 14)} Submit for approval</button>`,
  });

  let reqType: 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER' = 'DEPOSIT';
  const fromEl   = m.el.querySelector<HTMLSelectElement>('#rq-from')!;
  const toField  = m.el.querySelector<HTMLElement>('#rq-to-field')!;
  const toEl     = m.el.querySelector<HTMLSelectElement>('#rq-to')!;
  const amtEl    = m.el.querySelector<HTMLInputElement>('#rq-amount')!;
  const descEl   = m.el.querySelector<HTMLInputElement>('#rq-desc')!;
  const remEl    = m.el.querySelector<HTMLInputElement>('#rq-remarks')!;

  const setErr = (field: string, msg: string | null) => {
    const f = m.el.querySelector<HTMLElement>(`[data-field="${field}"]`);
    if (!f) return;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };

  m.el.querySelectorAll<HTMLButtonElement>('#rq-type button').forEach((b) => {
    b.addEventListener('click', () => {
      reqType = (b.dataset.t ?? 'DEPOSIT') as typeof reqType;
      m.el.querySelectorAll('#rq-type button').forEach((x) => x.classList.toggle('active', x === b));
      toField.style.display = reqType === 'TRANSFER' ? '' : 'none';
      // Re-populate destination without the selected source
      if (reqType === 'TRANSFER') {
        toEl.innerHTML = `<option value="">Select destination…</option>${acctOptions(Number(fromEl.value) || undefined)}`;
      }
    });
  });

  fromEl.addEventListener('change', () => {
    setErr('from', null);
    if (reqType === 'TRANSFER') {
      toEl.innerHTML = `<option value="">Select destination…</option>${acctOptions(Number(fromEl.value))}`;
    }
  });
  toEl.addEventListener('change', () => setErr('to', null));
  amtEl.addEventListener('input', () => setErr('amount', null));

  const submitBtn = m.el.querySelector<HTMLButtonElement>('#rq-submit')!;
  submitBtn.addEventListener('click', () => withBusy(submitBtn, async () => {
    const fromId = Number(fromEl.value);
    if (!fromId) { setErr('from', 'Select an account'); return; }

    const amount = parseAmount(amtEl.value.trim());
    if (!amount) { setErr('amount', 'Enter a valid amount (up to 2 decimal places)'); return; }

    const data: CreateTxRequest = {
      requestType: reqType,
      fromAccountId: fromId,
      amount,
      description: descEl.value.trim() || undefined,
      remarks: remEl.value.trim() || undefined,
    };

    if (reqType === 'TRANSFER') {
      const toId = Number(toEl.value);
      if (!toId) { setErr('to', 'Select destination account'); return; }
      data.toAccountId = toId;
    }

    try {
      const created = await txRequests.create(data);
      toast(`Request ${created.requestRef} submitted — awaiting Checker approval`, 'success');
      m.close();
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}

// ── Request detail drawer ─────────────────────────────────────────────────────

function openRequestDetail(req: TxRequest, onDone: () => void) {
  const canCancel = req.status === 'PENDING_APPROVAL';
  const m = openModal({
    drawer: true,
    autofocus: false,
    title: 'Request details',
    subtitle: req.requestRef,
    body: `
      <div class="card">
        <div class="card-body">
          <dl class="dl">
            <dt>Reference</dt><dd class="mono">${esc(req.requestRef)}</dd>
            <dt>Type</dt><dd>${esc(req.requestType)}</dd>
            <dt>Status</dt><dd>${reqStatusBadge(req.status)}</dd>
            <dt>Amount</dt><dd class="fw-600">${formatCurrency(req.amount)}</dd>
            <dt>From account</dt><dd class="mono">${esc(req.fromAccountNumber)} <span class="text-muted">(${esc(req.fromAccountType)})</span></dd>
            ${req.toAccountNumber ? `<dt>To account</dt><dd class="mono">${esc(req.toAccountNumber)}</dd>` : ''}
            <dt>Description</dt><dd>${esc(req.description ?? '—')}</dd>
            <dt>Maker</dt><dd>${esc(req.makerUsername)}</dd>
            <dt>Created</dt><dd>${formatDate(req.createdAt)}</dd>
            ${req.checkerUsername ? `<dt>Checker</dt><dd>${esc(req.checkerUsername)}</dd>` : ''}
            ${req.approvedAt ? `<dt>Approved</dt><dd>${formatDate(req.approvedAt)}</dd>` : ''}
            ${req.rejectedAt ? `<dt>Rejected</dt><dd>${formatDate(req.rejectedAt)}</dd>` : ''}
            ${req.executedAt ? `<dt>Executed</dt><dd>${formatDate(req.executedAt)}</dd>` : ''}
            ${req.rejectionReason ? `<dt>Rejection reason</dt><dd class="text-danger">${esc(req.rejectionReason)}</dd>` : ''}
            ${req.remarks ? `<dt>Remarks</dt><dd>${esc(req.remarks)}</dd>` : ''}
            ${req.balanceAfter !== null ? `<dt>Balance after</dt><dd>${formatCurrency(req.balanceAfter)}</dd>` : ''}
            ${req.transactionId ? `<dt>Transaction ID</dt><dd>#${req.transactionId}</dd>` : ''}
          </dl>
        </div>
      </div>`,
    footer: canCancel
      ? `<button class="btn btn-danger-soft" data-cancel>Cancel request</button><div style="flex:1"></div><button class="btn btn-secondary" data-close>Close</button>`
      : `<button class="btn btn-secondary" data-close>Close</button>`,
  });

  m.el.querySelector('[data-cancel]')?.addEventListener('click', async () => {
    m.close();
    await cancelRequest(req, onDone);
  });
}

async function cancelRequest(req: TxRequest, onDone: () => void) {
  const ok = await confirmDialog({
    title: `Cancel ${req.requestRef}?`,
    message: 'The pending request will be cancelled. No balances will change.',
    confirmText: 'Cancel request',
    tone: 'danger',
  });
  if (!ok) return;
  try {
    await txRequests.cancel(req.id);
    toast('Request cancelled', 'success');
    onDone();
  } catch (err) {
    toast(errorMessage(err), 'error');
  }
}

function exportCsv(rows: TxRequest[]) {
  downloadCsv('requests.csv',
    ['Ref', 'Type', 'Status', 'From Account', 'To Account', 'Amount', 'Maker', 'Checker', 'Created', 'Executed'],
    rows.map((r) => [
      r.requestRef, r.requestType, r.status, r.fromAccountNumber,
      r.toAccountNumber ?? '', r.amount.toFixed(2),
      r.makerUsername, r.checkerUsername ?? '',
      formatDay(r.createdAt), r.executedAt ? formatDay(r.executedAt) : '',
    ]));
}
