/**
 * Checker Approvals page.
 *
 * CHECKER and ADMIN see all PENDING_APPROVAL requests and can approve or reject.
 * The Approve button is hidden for requests the current user created (self-approval
 * is enforced by the backend, but also hidden in the UI as a usability guard).
 */
import { txRequests, getUserId, getUsername } from '../api';
import type { TxRequest, CheckerAction } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, withBusy, formatCurrency, formatDate,
  emptyState, errorState, skeletonRows, errorMessage, confirmDialog,
} from '../utils';

function reqStatusBadge(status: string): string {
  const map: Record<string, string> = {
    PENDING_APPROVAL: 'badge-amber',
    SUCCESS:          'badge-green',
    REJECTED:         'badge-red',
    FAILED:           'badge-red',
  };
  return `<span class="badge ${map[status] ?? 'badge-neutral'}">${esc(status.replace(/_/g, ' '))}</span>`;
}

function reqTypeIcon(type: string): string {
  const m: Record<string, string> = {
    DEPOSIT: icon('arrowIn', 15), WITHDRAW: icon('arrowOut', 15), TRANSFER: icon('transfer', 15),
  };
  return m[type] ?? icon('receipt', 15);
}

export async function renderApprovals(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Pending Approvals</h1>
          <p class="page-desc">Review and approve or reject Maker transaction requests.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="ap-show-all">${icon('list', 16)} All requests</button>
        </div>
      </div>

      <div class="callout callout-info" style="margin-bottom:16px">
        ${icon('shield', 16)}
        <div>You cannot approve requests that you created. The backend enforces this independently of this UI.</div>
      </div>

      <div id="ap-pending-section">
        <div class="section-head">
          <div class="section-title">Awaiting your action</div>
        </div>
        <div class="card" id="ap-pending">${skeletonRows(4)}</div>
      </div>

      <div id="ap-all-section" style="display:none">
        <div class="section-head" style="margin-top:24px">
          <div class="section-title">All requests</div>
          <button class="btn btn-ghost btn-sm" id="ap-show-pending">${icon('chevronLeft', 14)} Pending only</button>
        </div>
        <div class="card" id="ap-all">${skeletonRows(6)}</div>
      </div>
    </div>`;

  let pending: TxRequest[] = [];
  let allList: TxRequest[] = [];

  document.getElementById('ap-show-all')!.addEventListener('click', async () => {
    document.getElementById('ap-pending-section')!.style.display = 'none';
    document.getElementById('ap-all-section')!.style.display = '';
    if (allList.length === 0) await loadAll();
  });
  document.getElementById('ap-show-pending')!.addEventListener('click', () => {
    document.getElementById('ap-all-section')!.style.display = 'none';
    document.getElementById('ap-pending-section')!.style.display = '';
  });

  await loadPending();

  async function loadPending() {
    const el = document.getElementById('ap-pending')!;
    el.innerHTML = skeletonRows(4);
    try {
      pending = await txRequests.getPending();
      renderPending();
    } catch (err) {
      el.innerHTML = errorState(errorMessage(err));
      el.querySelector('[data-retry]')?.addEventListener('click', loadPending);
    }
  }

  async function loadAll() {
    const el = document.getElementById('ap-all')!;
    if (!el) return;
    el.innerHTML = skeletonRows(6);
    try {
      allList = await txRequests.getAll();
      renderAll();
    } catch (err) {
      el.innerHTML = errorState(errorMessage(err));
      el.querySelector('[data-retry]')?.addEventListener('click', loadAll);
    }
  }

  function renderPending() {
    const el = document.getElementById('ap-pending')!;
    if (pending.length === 0) {
      el.innerHTML = emptyState({ icon: 'checkCircle', title: 'No pending requests',
        text: 'All Maker requests have been actioned. Check back later.' });
      return;
    }
    el.innerHTML = renderTable(pending, true);
    attachHandlers(el, pending, () => { void loadPending(); });
  }

  function renderAll() {
    const el = document.getElementById('ap-all')!;
    if (!el) return;
    if (allList.length === 0) {
      el.innerHTML = emptyState({ icon: 'receipt', title: 'No requests found' });
      return;
    }
    el.innerHTML = renderTable(allList, false);
    attachHandlers(el, allList, () => { void loadAll(); void loadPending(); });
  }

  function renderTable(list: TxRequest[], showActions: boolean): string {
    const myId = getUserId();
    const myUsername = getUsername();
    return `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Reference</th><th>Type</th><th>Account</th>
            <th class="num">Amount</th><th>Status</th><th>Maker</th><th>Created</th>
            ${showActions ? '<th class="actions">Actions</th>' : ''}
          </tr></thead>
          <tbody>
            ${list.map((r) => {
              // Self-approval guard: hide Approve if maker == current checker
              const isSelf = r.makerUserId === myId || r.makerUsername === myUsername;
              const canAct = showActions && r.status === 'PENDING_APPROVAL';
              return `
              <tr class="clickable" data-id="${r.id}">
                <td><div class="cell-primary mono">${esc(r.requestRef)}</div></td>
                <td>${reqTypeIcon(r.requestType)} ${esc(r.requestType)}</td>
                <td>
                  <div class="cell-primary mono">${esc(r.fromAccountNumber)}</div>
                  ${r.toAccountNumber ? `<div class="cell-sub">→ ${esc(r.toAccountNumber)}</div>` : ''}
                </td>
                <td class="num fw-600">${formatCurrency(r.amount)}</td>
                <td>${reqStatusBadge(r.status)}</td>
                <td>${esc(r.makerUsername)}${isSelf ? ' <span class="badge badge-neutral" title="You created this request">you</span>' : ''}</td>
                <td>${formatDate(r.createdAt)}</td>
                ${showActions ? `<td class="actions">
                  ${canAct && !isSelf
                    ? `<button class="btn btn-success btn-sm" data-act="approve" title="Approve">${icon('checkCircle', 14)} Approve</button>
                       <button class="btn btn-danger-soft btn-sm" data-act="reject" title="Reject">${icon('x', 14)} Reject</button>`
                    : canAct && isSelf
                      ? `<span class="text-muted text-sm">Cannot self-approve</span>`
                      : `<button class="btn-icon" data-act="view" title="View">${icon('eye', 14)}</button>`}
                </td>` : ''}
              </tr>`;
            }).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function attachHandlers(container: HTMLElement, list: TxRequest[], reload: () => void) {
    container.querySelectorAll<HTMLTableRowElement>('tr[data-id]').forEach((tr) => {
      const req = list.find((r) => r.id === Number(tr.dataset.id))!;
      tr.addEventListener('click', (e) => {
        const btn = (e.target as HTMLElement).closest<HTMLButtonElement>('button[data-act]');
        if (!btn) { openDetailDrawer(req, reload); return; }
        e.stopPropagation();
        if (btn.dataset.act === 'view')    openDetailDrawer(req, reload);
        if (btn.dataset.act === 'approve') void approveRequest(req, reload);
        if (btn.dataset.act === 'reject')  void openRejectModal(req, reload);
      });
    });
  }
}

// ── Approve ───────────────────────────────────────────────────────────────────

async function approveRequest(req: TxRequest, onDone: () => void) {
  const ok = await confirmDialog({
    title: `Approve ${req.requestRef}?`,
    message: `This will immediately execute the ${req.requestType.toLowerCase()} of ${new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(req.amount)} on account ${req.fromAccountNumber}. The balance will change.`,
    confirmText: 'Approve & execute',
    tone: 'primary',
  });
  if (!ok) return;
  try {
    await txRequests.approve(req.id, {});
    toast(`Request ${req.requestRef} approved and executed`, 'success');
    onDone();
  } catch (err) {
    toast(errorMessage(err), 'error');
  }
}

// ── Reject modal ──────────────────────────────────────────────────────────────

async function openRejectModal(req: TxRequest, onDone: () => void) {
  const m = openModal({
    title: 'Reject request',
    subtitle: req.requestRef,
    size: 'sm',
    body: `
      <p class="text-sm text-muted mb-3">Provide a reason for rejecting this request. The Maker will see this reason.</p>
      <div class="field" data-field="reason">
        <label class="field-label" for="rj-reason">Rejection reason<span class="req">*</span></label>
        <textarea id="rj-reason" class="input" rows="3" maxlength="500" placeholder="e.g. Amount exceeds authorised daily limit"></textarea>
        <div class="field-error" data-error></div>
      </div>
      <div class="field">
        <label class="field-label" for="rj-remarks">Internal remarks</label>
        <input id="rj-remarks" class="input" maxlength="255" placeholder="Optional notes (not shown to Maker)" />
      </div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-danger" id="rj-confirm">${icon('x', 14)} Reject</button>`,
  });

  const reasonEl  = m.el.querySelector<HTMLTextAreaElement>('#rj-reason')!;
  const remarksEl = m.el.querySelector<HTMLInputElement>('#rj-remarks')!;
  const setErr = (msg: string | null) => {
    const f = m.el.querySelector<HTMLElement>('[data-field="reason"]')!;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };

  const btn = m.el.querySelector<HTMLButtonElement>('#rj-confirm')!;
  btn.addEventListener('click', () => withBusy(btn, async () => {
    const reason = reasonEl.value.trim();
    if (!reason) { setErr('Rejection reason is required'); return; }
    try {
      const action: CheckerAction = { rejectionReason: reason, remarks: remarksEl.value.trim() || undefined };
      await txRequests.reject(req.id, action);
      toast(`Request ${req.requestRef} rejected`, 'success');
      m.close();
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}

// ── Detail drawer ─────────────────────────────────────────────────────────────

function openDetailDrawer(req: TxRequest, _onDone: () => void) {
  const fmt = (v: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(v);
  openModal({
    drawer: true,
    autofocus: false,
    title: 'Request details',
    subtitle: req.requestRef,
    body: `
      <div class="card"><div class="card-body">
        <dl class="dl">
          <dt>Reference</dt><dd class="mono">${esc(req.requestRef)}</dd>
          <dt>Type</dt><dd>${esc(req.requestType)}</dd>
          <dt>Status</dt><dd>${reqStatusBadge(req.status)}</dd>
          <dt>Amount</dt><dd class="fw-600">${fmt(req.amount)}</dd>
          <dt>From</dt><dd class="mono">${esc(req.fromAccountNumber)}</dd>
          ${req.toAccountNumber ? `<dt>To</dt><dd class="mono">${esc(req.toAccountNumber)}</dd>` : ''}
          <dt>Description</dt><dd>${esc(req.description ?? '—')}</dd>
          <dt>Maker</dt><dd>${esc(req.makerUsername)}</dd>
          <dt>Created</dt><dd>${formatDate(req.createdAt)}</dd>
          ${req.checkerUsername ? `<dt>Checker</dt><dd>${esc(req.checkerUsername)}</dd>` : ''}
          ${req.approvedAt ? `<dt>Approved</dt><dd>${formatDate(req.approvedAt)}</dd>` : ''}
          ${req.rejectedAt ? `<dt>Rejected</dt><dd>${formatDate(req.rejectedAt)}</dd>` : ''}
          ${req.rejectionReason ? `<dt>Rejection reason</dt><dd class="text-danger">${esc(req.rejectionReason)}</dd>` : ''}
          ${req.remarks ? `<dt>Remarks</dt><dd>${esc(req.remarks)}</dd>` : ''}
          ${req.balanceAfter !== null ? `<dt>Balance after</dt><dd>${fmt(req.balanceAfter)}</dd>` : ''}
        </dl>
      </div></div>`,
    footer: `<button class="btn btn-secondary" data-close>Close</button>`,
  });
}
