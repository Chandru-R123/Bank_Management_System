import { accounts, consents, isStaff, isChecker } from '../api';
import type { Account, Consent, ConsentPermission, ConsentStatus } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, confirmDialog, withBusy, formatDate, formatDay, emptyState,
  errorState, skeletonRows, accountTypeLabel, errorMessage,
} from '../utils';

// ── Shared helpers (also used by the TPP portal) ─────────────────────────────

export const PERMISSIONS: { value: ConsentPermission; label: string; detail: string }[] = [
  { value: 'READ_ACCOUNTS',     label: 'Account details',     detail: 'Account numbers, types and holder name' },
  { value: 'READ_BALANCES',     label: 'Balances',            detail: 'Current balance of each shared account' },
  { value: 'READ_TRANSACTIONS', label: 'Transaction history', detail: 'Deposits, withdrawals and transfers' },
];

export function permissionLabel(p: string): string {
  return PERMISSIONS.find((x) => x.value === p)?.label ?? p;
}

export function consentStatusBadge(status: ConsentStatus): string {
  const map: Record<ConsentStatus, [string, string]> = {
    AWAITING_AUTHORISATION: ['badge-amber', 'Awaiting approval'],
    AUTHORISED:             ['badge-green', 'Authorised'],
    REJECTED:               ['badge-red', 'Rejected'],
    REVOKED:                ['badge-neutral', 'Revoked'],
    EXPIRED:                ['badge-neutral', 'Expired'],
  };
  const [cls, label] = map[status] ?? ['badge-neutral', status];
  return `<span class="badge badge-dot ${cls}">${esc(label)}</span>`;
}

export function permissionChips(list: string[]): string {
  return list.map((p) => `<span class="badge badge-blue">${esc(permissionLabel(p))}</span>`).join(' ');
}

export function isOpen(c: Consent): boolean {
  return c.status === 'AWAITING_AUTHORISATION' || c.status === 'AUTHORISED';
}

// ── Page ─────────────────────────────────────────────────────────────────────

export async function renderConsents(container: HTMLElement) {
  const staff = isStaff();
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">${staff ? 'Open Banking consents' : 'Connected apps'}</h1>
          <p class="page-desc">${staff
            ? 'Every consent third-party providers have requested from customers.'
            : 'Apps that asked to read your account data. You decide which accounts they can see — and you can revoke access at any time.'}</p>
        </div>
      </div>
      <div class="tabs" id="cns-tabs">
        <button class="tab active" data-tab="pending">Awaiting approval <span data-count="pending"></span></button>
        <button class="tab" data-tab="active">Active <span data-count="active"></span></button>
        <button class="tab" data-tab="history">History <span data-count="history"></span></button>
      </div>
      <div id="cns-content">${skeletonRows(4)}</div>
    </div>`;

  let list: Consent[] = [];
  let myAccounts: Account[] = [];
  let tab: 'pending' | 'active' | 'history' = 'pending';
  const content = document.getElementById('cns-content')!;

  document.querySelectorAll<HTMLButtonElement>('#cns-tabs .tab').forEach((b) => b.addEventListener('click', () => {
    tab = b.dataset.tab as typeof tab;
    document.querySelectorAll('#cns-tabs .tab').forEach((x) => x.classList.toggle('active', x === b));
    render();
  }));

  await load();

  async function load() {
    if (!document.getElementById('cns-content')) return;
    content.innerHTML = skeletonRows(4);
    try {
      const [c, a] = await Promise.all([
        consents.getAll(),
        staff ? Promise.resolve([] as Account[]) : accounts.getMy().catch(() => [] as Account[]),
      ]);
      list = c;
      myAccounts = a;
      // Land on the most useful tab
      if (tab === 'pending' && !list.some((x) => x.status === 'AWAITING_AUTHORISATION')
          && list.some((x) => x.status === 'AUTHORISED')) {
        document.querySelector<HTMLButtonElement>('#cns-tabs [data-tab="active"]')?.click();
        return;
      }
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  }

  function bucket(c: Consent): typeof tab {
    if (c.status === 'AWAITING_AUTHORISATION') return 'pending';
    if (c.status === 'AUTHORISED') return 'active';
    return 'history';
  }

  function render() {
    (['pending', 'active', 'history'] as const).forEach((t) => {
      const n = list.filter((c) => bucket(c) === t).length;
      const el = document.querySelector(`[data-count="${t}"]`);
      if (el) el.textContent = n ? `(${n})` : '';
    });

    const rows = list.filter((c) => bucket(c) === tab);
    if (rows.length === 0) {
      const text = {
        pending: staff ? 'No consent requests are waiting for customers.' : 'No app is waiting for your approval.',
        active: staff ? 'No consents are currently authorised.' : 'You have not given any app access to your data.',
        history: 'Rejected, revoked and expired consents appear here.',
      }[tab];
      content.innerHTML = `<div class="card">${emptyState({ icon: 'shield', title: 'Nothing here', text })}</div>`;
      return;
    }

    content.innerHTML = `<div class="grid">${rows.map((c) => consentCard(c)).join('')}</div>`;

    content.querySelectorAll<HTMLButtonElement>('[data-review]').forEach((b) => b.addEventListener('click', () => {
      openReview(list.find((c) => c.consentId === b.dataset.review)!, myAccounts, load);
    }));
    content.querySelectorAll<HTMLButtonElement>('[data-revoke]').forEach((b) => b.addEventListener('click', async () => {
      const c = list.find((x) => x.consentId === b.dataset.revoke)!;
      const ok = await confirmDialog({
        title: `Revoke access for ${c.tppName}?`,
        message: 'The app immediately loses access to the shared account data. This cannot be undone.',
        confirmText: 'Revoke access',
        tone: 'danger',
      });
      if (!ok) return;
      try {
        await consents.revoke(c.consentId);
        toast(`Access revoked for ${c.tppName}`, 'success');
        await load();
      } catch (err) {
        toast(errorMessage(err), 'error');
      }
    }));
  }

  function consentCard(c: Consent): string {
    const canRevoke = isOpen(c) && (!staff || isChecker());
    return `
      <div class="card">
        <div class="card-header">
          <div class="flex items-center gap-3">
            <span class="kpi-icon tone-violet">${icon('shield', 18)}</span>
            <div>
              <div class="card-title">${esc(c.tppName)}</div>
              <div class="card-sub">${staff ? `For ${esc(c.customerName)} · ` : ''}<span class="mono">${esc(c.consentId)}</span></div>
            </div>
          </div>
          ${consentStatusBadge(c.status)}
        </div>
        <div class="card-body">
          <p><span class="text-muted">Purpose:</span> ${esc(c.purpose)}</p>
          <div class="mt-3 flex gap-2" style="flex-wrap:wrap">${permissionChips(c.permissions)}</div>
          ${c.accounts.length ? `
            <div class="mt-3 text-sm"><span class="text-muted">Shared accounts:</span>
              ${c.accounts.map((a) => `<span class="mono">${esc(a.accountNumber)}</span>`).join(', ')}
            </div>` : ''}
        </div>
        <div class="card-footer">
          <span>Requested ${esc(formatDate(c.createdAt))} · ${c.status === 'EXPIRED' ? 'expired' : 'expires'} ${esc(formatDay(c.expiresAt))}
            ${c.statusUpdatedBy && c.statusUpdatedBy !== 'system' && !isOpen(c) ? ` · by ${esc(c.statusUpdatedBy)}` : ''}</span>
          <div class="btn-group">
            ${!staff && c.status === 'AWAITING_AUTHORISATION'
              ? `<button class="btn btn-primary btn-sm" data-review="${esc(c.consentId)}">Review request</button>` : ''}
            ${canRevoke && c.status === 'AUTHORISED'
              ? `<button class="btn btn-danger-soft btn-sm" data-revoke="${esc(c.consentId)}">${icon('closeCircle', 14)} Revoke</button>` : ''}
            ${staff && canRevoke && c.status === 'AWAITING_AUTHORISATION'
              ? `<button class="btn btn-danger-soft btn-sm" data-revoke="${esc(c.consentId)}">${icon('closeCircle', 14)} Cancel request</button>` : ''}
          </div>
        </div>
      </div>`;
  }
}

// ── Customer review: choose accounts, approve or reject ──────────────────────

function openReview(c: Consent, myAccounts: Account[], onDone: () => void) {
  const shareable = myAccounts.filter((a) => a.status !== 'CLOSED');
  const m = openModal({
    title: `${c.tppName} wants access`,
    subtitle: c.purpose,
    size: 'lg',
    autofocus: false,
    body: `
      <div class="callout callout-info">${icon('shield', 16)}
        <div>State Bank never shares your password. ${esc(c.tppName)} will only be able to <strong>read</strong> the data below, for the accounts you select, until ${esc(formatDay(c.expiresAt))} or until you revoke access.</div>
      </div>

      <div class="section-title mt-4">They are asking to see</div>
      <div class="list card mt-2">
        ${PERMISSIONS.filter((p) => c.permissions.includes(p.value)).map((p) => `
          <div class="list-item">
            <span class="tx-icon tone-blue">${icon('check', 16)}</span>
            <div class="grow"><div class="fw-600">${esc(p.label)}</div><div class="text-sm text-muted">${esc(p.detail)}</div></div>
          </div>`).join('')}
      </div>

      <div class="section-title mt-4">Choose accounts to share</div>
      ${shareable.length ? `
      <div class="list card mt-2" data-accounts>
        ${shareable.map((a) => `
          <label class="list-item" style="cursor:pointer">
            <input type="checkbox" value="${a.accountId}" ${a.status === 'ACTIVE' ? 'checked' : ''} style="width:18px;height:18px" />
            <div class="grow">
              <div class="fw-600 mono">${esc(a.accountNumber)}</div>
              <div class="text-sm text-muted">${esc(accountTypeLabel(a.accountType))}${a.status !== 'ACTIVE' ? ` · ${esc(a.status)}` : ''}</div>
            </div>
          </label>`).join('')}
      </div>
      <div class="field-error mt-2" data-acc-error style="display:none">Select at least one account to approve.</div>`
      : `<div class="callout callout-warn mt-2">${icon('info', 16)}<div>You have no open accounts to share. You can only reject this request.</div></div>`}`,
    footer: `
      <button class="btn btn-danger-soft" data-reject>Reject</button>
      <div style="flex:1"></div>
      <button class="btn btn-secondary" data-close>Decide later</button>
      <button class="btn btn-primary" data-approve ${shareable.length ? '' : 'disabled'}>${icon('check', 16)} Approve access</button>`,
  });

  const errEl = m.el.querySelector<HTMLElement>('[data-acc-error]');
  const approve = m.el.querySelector<HTMLButtonElement>('[data-approve]')!;
  approve.addEventListener('click', () => withBusy(approve, async () => {
    const ids = Array.from(m.el.querySelectorAll<HTMLInputElement>('[data-accounts] input:checked')).map((i) => Number(i.value));
    if (ids.length === 0) {
      if (errEl) errEl.style.display = 'block';
      return;
    }
    try {
      await consents.approve(c.consentId, ids);
      toast(`${c.tppName} can now read ${ids.length} account${ids.length === 1 ? '' : 's'}`, 'success');
      m.close();
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
  m.el.querySelectorAll('[data-accounts] input').forEach((i) => i.addEventListener('change', () => {
    if (errEl) errEl.style.display = 'none';
  }));

  const reject = m.el.querySelector<HTMLButtonElement>('[data-reject]')!;
  reject.addEventListener('click', () => withBusy(reject, async () => {
    try {
      await consents.reject(c.consentId);
      toast(`Request from ${c.tppName} rejected`, 'info');
      m.close();
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}
