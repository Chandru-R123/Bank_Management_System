/**
 * KYC page — Customer view.
 *
 * Allows the customer to:
 *   1. Start KYC (initiates the process)
 *   2. Upload identity, address, photograph documents
 *   3. Submit for staff review
 *   4. View their current KYC status and document list
 *   5. Resubmit after rejection
 *
 * Customer CANNOT self-verify — VERIFIED status is set only by staff.
 */
import { kyc } from '../api';
import type { KycRecord, KycDocumentType } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, withBusy, formatDate, formatDay,
  emptyState, errorState, skeletonRows, errorMessage, confirmDialog,
} from '../utils';

const DOC_TYPES: { value: KycDocumentType; label: string; hint: string }[] = [
  { value: 'IDENTITY',    label: 'Identity document',
    hint: 'Aadhaar card, PAN card, Passport, Voter ID or Driving License' },
  { value: 'ADDRESS',     label: 'Address proof',
    hint: 'Utility bill, bank statement or rental agreement (not older than 3 months)' },
  { value: 'PHOTOGRAPH',  label: 'Recent photograph',
    hint: 'Clear passport-size photo, JPEG or PNG, white background preferred' },
];

function kycStatusBadge(status: string): string {
  const map: Record<string, string> = {
    NOT_STARTED:  'badge-neutral',
    PENDING:      'badge-amber',
    UNDER_REVIEW: 'badge-sky',
    VERIFIED:     'badge-green',
    REJECTED:     'badge-red',
  };
  const labels: Record<string, string> = {
    NOT_STARTED: 'Not started', PENDING: 'Pending review',
    UNDER_REVIEW: 'Under review', VERIFIED: 'Verified', REJECTED: 'Rejected',
  };
  return `<span class="badge ${map[status] ?? 'badge-neutral'}">${esc(labels[status] ?? status)}</span>`;
}

export async function renderKyc(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">KYC Verification</h1>
          <p class="page-desc">Complete your Know Your Customer verification to access all banking services.</p>
        </div>
      </div>
      <div id="kyc-content">${skeletonRows(3)}</div>
    </div>`;

  await loadKyc();

  async function loadKyc() {
    const el = document.getElementById('kyc-content');
    if (!el) return;
    el.innerHTML = skeletonRows(3);
    try {
      const record = await kyc.getMy();
      render(record);
    } catch (err) {
      el.innerHTML = errorState(errorMessage(err));
      el.querySelector('[data-retry]')?.addEventListener('click', loadKyc);
    }
  }

  function render(record: KycRecord) {
    const el = document.getElementById('kyc-content')!;
    const status = record.status;
    const isVerified   = status === 'VERIFIED';
    const isPending    = status === 'PENDING' || status === 'UNDER_REVIEW';
    const isRejected   = status === 'REJECTED';
    const isNotStarted = status === 'NOT_STARTED';
    const canUpload    = !isVerified && !isPending;
    const canSubmit    = !isVerified && !isPending && (record.documents?.length ?? 0) > 0;
    const canStart     = isNotStarted || isRejected;

    el.innerHTML = `
      <!-- Status card -->
      <div class="card">
        <div class="card-body">
          <div style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
            <div class="avatar avatar-lg" style="background:${isVerified ? 'var(--green-100)' : isPending ? 'var(--amber-100)' : isRejected ? 'var(--red-100)' : 'var(--neutral-100)'}">
              ${icon(isVerified ? 'checkCircle' : isPending ? 'clock' : isRejected ? 'alert' : 'user', 24)}
            </div>
            <div style="flex:1">
              <div class="section-title">KYC Status: ${kycStatusBadge(status)}</div>
              ${record.method ? `<div class="text-sm text-muted mt-1">Method: ${esc(record.method)}</div>` : ''}
              ${record.initiatedAt ? `<div class="text-sm text-muted">Started: ${formatDay(record.initiatedAt)}</div>` : ''}
              ${record.submittedAt ? `<div class="text-sm text-muted">Submitted: ${formatDate(record.submittedAt)}</div>` : ''}
              ${record.verifiedAt  ? `<div class="text-sm text-muted">Verified: ${formatDate(record.verifiedAt)}</div>` : ''}
            </div>
          </div>

          ${isRejected && record.rejectionReason ? `
            <div class="callout callout-danger mt-3">
              ${icon('alert', 16)}
              <div><strong>Rejected: </strong>${esc(record.rejectionReason)}</div>
            </div>` : ''}

          ${isPending ? `
            <div class="callout callout-info mt-3">
              ${icon('clock', 16)}
              <div>Your documents are submitted and awaiting review by our team. This usually takes 1–2 business days.</div>
            </div>` : ''}

          ${isVerified ? `
            <div class="callout callout-success mt-3" style="border-color:var(--green-200);background:var(--green-50)">
              ${icon('checkCircle', 16)}
              <div>Your identity has been verified. You have full access to all banking services.</div>
            </div>` : ''}
        </div>
      </div>

      ${canStart ? `
      <!-- Start / Restart KYC -->
      <div class="card mt-4">
        <div class="card-body">
          <div class="section-title">${isRejected ? 'Resubmit KYC' : 'Start KYC verification'}</div>
          <p class="text-sm text-muted mt-1">
            ${isRejected
              ? 'Your previous submission was rejected. Please start a new KYC process and re-upload your documents.'
              : 'To verify your identity, please start the process and upload your documents.'}
          </p>
          <button class="btn btn-primary mt-3" id="kyc-start">${icon('user', 16)} ${isRejected ? 'Start new KYC' : 'Begin KYC'}</button>
        </div>
      </div>` : ''}

      ${!isNotStarted && !isVerified ? `
      <!-- Documents section -->
      <div class="section-head section mt-4">
        <div class="section-title">Documents</div>
        ${canUpload ? `<button class="btn btn-secondary btn-sm" id="kyc-upload">${icon('upload', 14)} Upload document</button>` : ''}
      </div>
      <div class="card">
        <div class="card-body" id="kyc-docs">
          ${(record.documents?.length ?? 0) === 0
            ? emptyState({ icon: 'file', title: 'No documents uploaded yet',
                text: 'Upload at least one identity document and one address proof.' })
            : `<div class="table-wrap"><table class="table"><thead><tr>
                <th>Type</th><th>File</th><th>Size</th><th>Status</th><th>Uploaded</th>
               </tr></thead><tbody>
               ${record.documents.map((d) => `
                <tr>
                  <td>${esc(d.documentType)}</td>
                  <td class="mono text-sm">${esc(d.fileName)}</td>
                  <td class="text-sm text-muted">${formatFileSize(d.fileSize)}</td>
                  <td><span class="badge badge-neutral">${esc(d.status)}</span></td>
                  <td>${formatDate(d.uploadedAt)}</td>
                </tr>`).join('')}
               </tbody></table></div>`}
        </div>
      </div>

      ${canSubmit ? `
      <div class="card mt-3">
        <div class="card-body" style="display:flex;align-items:center;gap:16px;flex-wrap:wrap">
          <div style="flex:1">
            <div class="fw-600">Ready to submit?</div>
            <div class="text-sm text-muted">Once submitted, a bank employee will review your documents. You cannot add more documents until the review is complete.</div>
          </div>
          <button class="btn btn-primary" id="kyc-submit">${icon('send', 14)} Submit for review</button>
        </div>
      </div>` : ''}` : ''}`;

    // Wire events
    document.getElementById('kyc-start')?.addEventListener('click', async () => {
      const btn = document.getElementById('kyc-start') as HTMLButtonElement;
      btn.disabled = true;
      btn.textContent = 'Starting…';
      try {
        const result = await kyc.start();
        toast(result.message, 'success');
        if (result.redirectUrl) {
          window.open(result.redirectUrl, '_blank');
        }
        await loadKyc();
      } catch (err) {
        toast(errorMessage(err), 'error');
        btn.disabled = false;
        btn.innerHTML = `${icon('user', 16)} ${isRejected ? 'Start new KYC' : 'Begin KYC'}`;
      }
    });

    document.getElementById('kyc-upload')?.addEventListener('click', () => openUploadModal(loadKyc));

    document.getElementById('kyc-submit')?.addEventListener('click', async () => {
      const ok = await confirmDialog({
        title: 'Submit KYC for review?',
        message: 'Once submitted, you cannot add more documents until the review is complete. A bank employee will review within 1–2 business days.',
        confirmText: 'Submit for review',
      });
      if (!ok) return;
      const btn = document.getElementById('kyc-submit') as HTMLButtonElement;
      if (btn) { btn.disabled = true; btn.textContent = 'Submitting…'; }
      try {
        await kyc.submit();
        toast('KYC submitted for review. You will be notified once reviewed.', 'success');
        await loadKyc();
      } catch (err) {
        toast(errorMessage(err), 'error');
        if (btn) { btn.disabled = false; btn.innerHTML = `${icon('send', 14)} Submit for review`; }
      }
    });
  }
}

// ── Upload document modal ─────────────────────────────────────────────────────

function openUploadModal(onDone: () => void) {
  const m = openModal({
    title: 'Upload KYC document',
    size: 'sm',
    body: `
      <div class="field" data-field="type">
        <label class="field-label">Document type<span class="req">*</span></label>
        <select id="doc-type" class="select">
          <option value="">Select type…</option>
          ${DOC_TYPES.map((t) => `<option value="${t.value}">${esc(t.label)}</option>`).join('')}
        </select>
        <div class="field-hint" id="doc-type-hint"></div>
        <div class="field-error" data-error></div>
      </div>
      <div class="field" data-field="file">
        <label class="field-label">File<span class="req">*</span></label>
        <input type="file" id="doc-file" class="input" accept=".jpg,.jpeg,.png,.heic,.pdf" />
        <div class="field-hint">Accepted: JPEG, PNG, HEIC, PDF — max 10 MB</div>
        <div class="field-error" data-error></div>
      </div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" id="doc-upload">${icon('upload', 14)} Upload</button>`,
  });

  const typeEl = m.el.querySelector<HTMLSelectElement>('#doc-type')!;
  const fileEl = m.el.querySelector<HTMLInputElement>('#doc-file')!;
  const hintEl = m.el.querySelector<HTMLElement>('#doc-type-hint')!;

  const setErr = (field: string, msg: string | null) => {
    const f = m.el.querySelector<HTMLElement>(`[data-field="${field}"]`);
    if (!f) return;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };

  typeEl.addEventListener('change', () => {
    setErr('type', null);
    const t = DOC_TYPES.find((d) => d.value === typeEl.value);
    hintEl.textContent = t?.hint ?? '';
  });
  fileEl.addEventListener('change', () => setErr('file', null));

  const btn = m.el.querySelector<HTMLButtonElement>('#doc-upload')!;
  btn.addEventListener('click', () => withBusy(btn, async () => {
    const docType = typeEl.value as KycDocumentType;
    if (!docType) { setErr('type', 'Select document type'); return; }
    const file = fileEl.files?.[0];
    if (!file) { setErr('file', 'Choose a file to upload'); return; }
    if (file.size > 10 * 1024 * 1024) { setErr('file', 'File exceeds 10 MB limit'); return; }

    try {
      await kyc.uploadDocument(docType, file);
      toast(`${file.name} uploaded`, 'success');
      m.close();
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
