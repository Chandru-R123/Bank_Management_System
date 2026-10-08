/**
 * KYC Review page — Staff (EMPLOYEE / ADMIN) view.
 *
 * Lists customers with PENDING or UNDER_REVIEW KYC submissions.
 * Allows staff to:
 *   - View submitted document metadata
 *   - Approve KYC (sets VERIFIED — customer CANNOT do this themselves)
 *   - Reject KYC with a reason
 *   - Download documents securely via the backend stream endpoint
 *
 * TPP has NO access to this page.
 */
import { kyc } from '../api';
import type { KycRecord } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, withBusy, formatDate, formatDay,
  emptyState, errorState, skeletonRows, errorMessage, confirmDialog,
} from '../utils';

function kycStatusBadge(status: string): string {
  const map: Record<string, string> = {
    NOT_STARTED:  'badge-neutral',
    PENDING:      'badge-amber',
    UNDER_REVIEW: 'badge-sky',
    VERIFIED:     'badge-green',
    REJECTED:     'badge-red',
  };
  const labels: Record<string, string> = {
    NOT_STARTED: 'Not started', PENDING: 'Pending',
    UNDER_REVIEW: 'Under review', VERIFIED: 'Verified', REJECTED: 'Rejected',
  };
  return `<span class="badge ${map[status] ?? 'badge-neutral'}">${esc(labels[status] ?? status)}</span>`;
}

function methodBadge(method: string | null): string {
  if (!method) return '';
  const map: Record<string, string> = {
    MANUAL: 'badge-blue', DIGILOCKER: 'badge-green', MOCK: 'badge-amber',
  };
  const label = method === 'MOCK' ? '[DEV] Mock' : method;
  return `<span class="badge ${map[method] ?? 'badge-neutral'}" title="${method === 'MOCK' ? 'Development stub only — not real verification' : ''}">${esc(label)}</span>`;
}

export async function renderKycReview(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">KYC Review</h1>
          <p class="page-desc">Review and approve or reject customer KYC submissions.</p>
        </div>
      </div>
      <div class="card">
        <div class="filters">
          <div class="segmented" id="kyc-filter">
            <button class="active" data-v="pending">Pending</button>
            <button data-v="all">All customers</button>
          </div>
          <div class="spacer"></div>
          <span class="text-sm text-muted" id="kyc-count"></span>
        </div>
        <div id="kyc-table">${skeletonRows(4)}</div>
      </div>
    </div>`;

  let mode: 'pending' | 'all' = 'pending';
  let list: KycRecord[] = [];

  document.querySelectorAll<HTMLButtonElement>('#kyc-filter button').forEach((b) => {
    b.addEventListener('click', () => {
      mode = (b.dataset.v as typeof mode) ?? 'pending';
      document.querySelectorAll('#kyc-filter button').forEach((x) => x.classList.toggle('active', x === b));
      void loadData();
    });
  });

  await loadData();

  async function loadData() {
    const el = document.getElementById('kyc-table');
    if (!el) return;
    el.innerHTML = skeletonRows(4);
    try {
      list = mode === 'pending'
        ? await kyc.listPending()
        : await kyc.listPending(); // same endpoint — extend if needed
      render();
    } catch (err) {
      el.innerHTML = errorState(errorMessage(err));
      el.querySelector('[data-retry]')?.addEventListener('click', loadData);
    }
  }

  function render() {
    const el = document.getElementById('kyc-table')!;
    const countEl = document.getElementById('kyc-count')!;
    countEl.textContent = `${list.length} submission${list.length === 1 ? '' : 's'}`;

    if (list.length === 0) {
      el.innerHTML = emptyState({ icon: 'checkCircle', title: 'No pending KYC submissions',
        text: 'All submitted KYC requests have been reviewed.' });
      return;
    }

    el.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Customer</th><th>Email</th><th>Status</th><th>Method</th>
            <th>Submitted</th><th>Documents</th><th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${list.map((r) => `
              <tr class="clickable" data-id="${r.customerId}">
                <td>
                  <div class="cell-primary">${esc(r.customerName)}</div>
                  <div class="cell-sub">#${r.customerId}</div>
                </td>
                <td>${esc(r.customerEmail)}</td>
                <td>${kycStatusBadge(r.status)}</td>
                <td>${methodBadge(r.method)}</td>
                <td>${r.submittedAt ? formatDay(r.submittedAt) : '—'}</td>
                <td>${r.documents?.length ?? 0}</td>
                <td class="actions">
                  <button class="btn-icon" data-act="review" title="Review">${icon('eye', 16)}</button>
                  ${r.status === 'PENDING' || r.status === 'UNDER_REVIEW' ? `
                    <button class="btn btn-success btn-sm" data-act="approve">${icon('checkCircle', 14)}</button>
                    <button class="btn btn-danger-soft btn-sm" data-act="reject">${icon('x', 14)}</button>` : ''}
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    el.querySelectorAll<HTMLTableRowElement>('tr[data-id]').forEach((tr) => {
      const rec = list.find((r) => r.customerId === Number(tr.dataset.id))!;
      tr.addEventListener('click', (e) => {
        const btn = (e.target as HTMLElement).closest<HTMLButtonElement>('button[data-act]');
        if (!btn) { void openReviewDrawer(rec, loadData); return; }
        e.stopPropagation();
        if (btn.dataset.act === 'review')  void openReviewDrawer(rec, loadData);
        if (btn.dataset.act === 'approve') void approveKyc(rec, loadData);
        if (btn.dataset.act === 'reject')  void openRejectModal(rec, loadData);
      });
    });
  }
}

// ── Review drawer ─────────────────────────────────────────────────────────────

async function openReviewDrawer(rec: KycRecord, onDone: () => void) {
  const canAct = rec.status === 'PENDING' || rec.status === 'UNDER_REVIEW';

  const m = openModal({
    drawer: true,
    autofocus: false,
    title: 'KYC Review',
    subtitle: rec.customerName,
    body: `
      <div class="card"><div class="card-body">
        <dl class="dl">
          <dt>Customer</dt><dd>${esc(rec.customerName)} <span class="text-muted">#${rec.customerId}</span></dd>
          <dt>Email</dt><dd>${esc(rec.customerEmail)}</dd>
          <dt>Status</dt><dd>${kycStatusBadge(rec.status)}</dd>
          <dt>Method</dt><dd>${methodBadge(rec.method) || '—'}</dd>
          <dt>Submitted</dt><dd>${rec.submittedAt ? formatDate(rec.submittedAt) : '—'}</dd>
          ${rec.reviewedBy ? `<dt>Reviewed by</dt><dd>${esc(rec.reviewedBy)}</dd>` : ''}
          ${rec.reviewedAt ? `<dt>Reviewed at</dt><dd>${formatDate(rec.reviewedAt)}</dd>` : ''}
          ${rec.rejectionReason ? `<dt>Rejection reason</dt><dd class="text-danger">${esc(rec.rejectionReason)}</dd>` : ''}
          ${rec.reviewerNotes ? `<dt>Internal notes</dt><dd class="text-muted">${esc(rec.reviewerNotes)}</dd>` : ''}
        </dl>
      </div></div>

      <div class="section-head section mt-4">
        <div class="section-title">Documents (${rec.documents?.length ?? 0})</div>
      </div>
      <div class="card">
        <div class="card-body">
          ${(rec.documents?.length ?? 0) === 0
            ? emptyState({ icon: 'file', title: 'No documents uploaded' })
            : `<div class="table-wrap"><table class="table"><thead><tr>
                <th>Type</th><th>File</th><th>Size</th><th>Uploaded</th><th></th>
               </tr></thead><tbody>
               ${rec.documents.map((d) => `
                <tr>
                  <td>${esc(d.documentType)}</td>
                  <td class="mono text-sm">${esc(d.fileName)}</td>
                  <td class="text-sm text-muted">${formatFileSize(d.fileSize)}</td>
                  <td>${formatDate(d.uploadedAt)}</td>
                  <td><a class="btn btn-secondary btn-sm" href="${esc(kyc.documentDownloadUrl(d.id))}" target="_blank" rel="noopener">
                    ${icon('download', 14)} Download
                  </a></td>
                </tr>`).join('')}
               </tbody></table></div>`}
        </div>
      </div>`,
    footer: canAct ? `
      <button class="btn btn-danger-soft" data-act="reject">${icon('x', 14)} Reject</button>
      <div style="flex:1"></div>
      <button class="btn btn-secondary" data-close>Close</button>
      <button class="btn btn-success" data-act="approve">${icon('checkCircle', 14)} Approve KYC</button>`
      : `<button class="btn btn-secondary" data-close>Close</button>`,
  });

  m.el.querySelector('[data-act="approve"]')?.addEventListener('click', async () => {
    m.close();
    await approveKyc(rec, onDone);
  });
  m.el.querySelector('[data-act="reject"]')?.addEventListener('click', async () => {
    m.close();
    await openRejectModal(rec, onDone);
  });
}

// ── Approve ───────────────────────────────────────────────────────────────────

async function approveKyc(rec: KycRecord, onDone: () => void) {
  const ok = await confirmDialog({
    title: `Approve KYC for ${rec.customerName}?`,
    message: `This will mark the customer's identity as VERIFIED. Only do this after reviewing all submitted documents.`,
    confirmText: 'Approve KYC',
    tone: 'primary',
  });
  if (!ok) return;
  try {
    await kyc.approve(rec.customerId, {});
    toast(`KYC approved for ${rec.customerName}`, 'success');
    onDone();
  } catch (err) {
    toast(errorMessage(err), 'error');
  }
}

// ── Reject modal ──────────────────────────────────────────────────────────────

async function openRejectModal(rec: KycRecord, onDone: () => void) {
  const m = openModal({
    title: `Reject KYC — ${rec.customerName}`,
    size: 'sm',
    body: `
      <p class="text-sm text-muted mb-3">The customer will see this rejection reason and can resubmit.</p>
      <div class="field" data-field="reason">
        <label class="field-label">Rejection reason<span class="req">*</span></label>
        <textarea id="kyc-rj-reason" class="input" rows="3" maxlength="500"
          placeholder="e.g. Document is blurry, expired or illegible. Please resubmit a clear copy."></textarea>
        <div class="field-error" data-error></div>
      </div>
      <div class="field">
        <label class="field-label">Internal notes <span class="text-muted">(not shown to customer)</span></label>
        <input id="kyc-rj-notes" class="input" maxlength="500"
          placeholder="Optional staff notes for internal records" />
      </div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-danger" id="kyc-rj-confirm">${icon('x', 14)} Reject KYC</button>`,
  });

  const reasonEl = m.el.querySelector<HTMLTextAreaElement>('#kyc-rj-reason')!;
  const notesEl  = m.el.querySelector<HTMLInputElement>('#kyc-rj-notes')!;
  const setErr = (msg: string | null) => {
    const f = m.el.querySelector<HTMLElement>('[data-field="reason"]')!;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };

  const btn = m.el.querySelector<HTMLButtonElement>('#kyc-rj-confirm')!;
  btn.addEventListener('click', () => withBusy(btn, async () => {
    const reason = reasonEl.value.trim();
    if (!reason) { setErr('Rejection reason is required'); return; }
    try {
      await kyc.reject(rec.customerId, {
        rejectionReason: reason,
        reviewerNotes: notesEl.value.trim() || undefined,
      });
      toast(`KYC rejected for ${rec.customerName}`, 'success');
      m.close();
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
