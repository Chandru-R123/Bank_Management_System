import { consents, openBanking, getDisplayName } from '../api';
import type { Consent, ConsentPermission, OpenBankingAccount } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, confirmDialog, withBusy, formatCurrency, formatDay, emptyState,
  errorState, skeletonRows, accountTypeLabel, errorMessage,
} from '../utils';
import { txTableHtml } from '../components/tx';
import { PERMISSIONS, consentStatusBadge, permissionChips, isOpen } from './consents';

/**
 * Third-Party Provider portal — what a fintech app would do through the API:
 *   1. POST /api/consents                         request access to a customer's data
 *   2. (customer approves in online banking)
 *   3. GET  /api/open-banking/accounts            with x-consent-id
 *   4. GET  /api/open-banking/accounts/{id}/transactions
 */
export async function renderTppPortal(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Open Banking portal</h1>
          <p class="page-desc">Request consent from State Bank customers, then read the data they choose to share.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-primary" id="tpp-new">${icon('plus', 16)} New consent request</button>
        </div>
      </div>
      <div class="callout callout-info" style="margin-bottom:16px">${icon('info', 16)}
        <div>Every data call sends the consent id in the <code>x-consent-id</code> header. Access works only while the consent is <strong>Authorised</strong> and within its expiry date.</div>
      </div>
      <div class="card">
        <div class="card-header"><div><div class="card-title">Your consent requests</div><div class="card-sub">Newest first</div></div></div>
        <div id="tpp-content">${skeletonRows(4)}</div>
      </div>
    </div>`;

  let list: Consent[] = [];
  const content = document.getElementById('tpp-content')!;
  document.getElementById('tpp-new')!.addEventListener('click', () => openRequestForm(load));

  await load();

  async function load() {
    if (!document.getElementById('tpp-content')) return;
    content.innerHTML = skeletonRows(4);
    try {
      list = await consents.getAll();
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  }

  function render() {
    if (list.length === 0) {
      content.innerHTML = emptyState({
        icon: 'shield', title: 'No consent requests yet',
        text: 'Create a request with the customer\'s online-banking email. They approve it from their State Bank app.',
        action: `<button class="btn btn-primary btn-sm" data-empty-new>${icon('plus', 14)} New consent request</button>`,
      });
      content.querySelector('[data-empty-new]')?.addEventListener('click', () => openRequestForm(load));
      return;
    }
    content.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Consent</th><th>Customer</th><th>Permissions</th><th>Status</th><th>Expires</th><th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${list.map((c) => `
              <tr>
                <td>
                  <div class="cell-primary mono">${esc(c.consentId)}</div>
                  <div class="cell-sub truncate" style="max-width:260px" title="${esc(c.purpose)}">${esc(c.purpose)}</div>
                </td>
                <td>${esc(c.customerName)}</td>
                <td>${permissionChips(c.permissions)}</td>
                <td>${consentStatusBadge(c.status)}</td>
                <td class="nowrap text-muted">${esc(formatDay(c.expiresAt))}</td>
                <td class="actions">
                  ${c.status === 'AUTHORISED' ? `<button class="btn btn-primary btn-sm" data-data="${esc(c.consentId)}">${icon('eye', 14)} View data</button>` : ''}
                  ${isOpen(c) ? `<button class="btn-icon danger" data-revoke="${esc(c.consentId)}" title="${c.status === 'AUTHORISED' ? 'Revoke' : 'Cancel request'}">${icon('closeCircle', 16)}</button>` : ''}
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    const byId = (id: string | undefined) => list.find((c) => c.consentId === id)!;
    content.querySelectorAll<HTMLButtonElement>('[data-data]').forEach((b) =>
      b.addEventListener('click', () => openDataDrawer(byId(b.dataset.data))));
    content.querySelectorAll<HTMLButtonElement>('[data-revoke]').forEach((b) => b.addEventListener('click', async () => {
      const c = byId(b.dataset.revoke);
      const ok = await confirmDialog({
        title: c.status === 'AUTHORISED' ? 'Give up this consent?' : 'Cancel this request?',
        message: 'You will no longer be able to read this customer\'s data with this consent.',
        confirmText: 'Revoke',
        tone: 'danger',
      });
      if (!ok) return;
      try {
        await consents.revoke(c.consentId);
        toast('Consent revoked', 'success');
        await load();
      } catch (err) {
        toast(errorMessage(err), 'error');
      }
    }));
  }
}

// ── New request ──────────────────────────────────────────────────────────────

function openRequestForm(onSave: () => void) {
  const m = openModal({
    title: 'New consent request',
    subtitle: 'The customer will see this request in online banking and decide.',
    body: `
      <form class="form-grid" novalidate>
        <div class="field" data-field="email">
          <label class="field-label" for="cr-email">Customer email<span class="req">*</span></label>
          <input id="cr-email" class="input" type="email" placeholder="customer@example.com" autocomplete="off" />
          <div class="field-error" data-error></div>
        </div>
        <div class="field" data-field="purpose">
          <label class="field-label" for="cr-purpose">Purpose<span class="req">*</span></label>
          <input id="cr-purpose" class="input" maxlength="140" placeholder="e.g. Monthly budgeting and spend insights" />
          <div class="field-error" data-error></div>
        </div>
        <div class="field">
          <span class="field-label">Data requested</span>
          <div class="list card">
            ${PERMISSIONS.map((p) => `
              <label class="list-item" style="cursor:pointer">
                <input type="checkbox" value="${p.value}" ${p.value === 'READ_ACCOUNTS' ? 'checked disabled' : 'checked'} style="width:18px;height:18px" />
                <div class="grow"><div class="fw-600">${esc(p.label)}</div><div class="text-sm text-muted">${esc(p.detail)}</div></div>
              </label>`).join('')}
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label class="field-label" for="cr-days">Valid for</label>
            <select id="cr-days" class="select">
              <option value="30">30 days</option>
              <option value="90" selected>90 days</option>
              <option value="180">180 days</option>
              <option value="365">1 year</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="cr-name">App name shown to customer</label>
            <input id="cr-name" class="input" maxlength="80" value="${esc(getDisplayName())}" />
          </div>
        </div>
      </form>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>Send request</button>`,
  });

  const root = m.el;
  const val = (id: string) => root.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!.value.trim();
  const setErr = (field: string, msg: string | null) => {
    const f = root.querySelector<HTMLElement>(`[data-field="${field}"]`)!;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };
  root.querySelector('#cr-email')!.addEventListener('input', () => setErr('email', null));
  root.querySelector('#cr-purpose')!.addEventListener('input', () => setErr('purpose', null));

  const save = root.querySelector<HTMLButtonElement>('[data-save]')!;
  save.addEventListener('click', () => withBusy(save, async () => {
    const customerEmail = val('cr-email');
    const purpose = val('cr-purpose');
    let ok = true;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail)) { setErr('email', 'Enter the customer\'s email'); ok = false; }
    if (!purpose) { setErr('purpose', 'Explain why you need the data'); ok = false; }
    if (!ok) return;

    const permissions = Array.from(root.querySelectorAll<HTMLInputElement>('.list input[type="checkbox"]'))
      .filter((i) => i.checked)
      .map((i) => i.value as ConsentPermission);
    try {
      const created = await consents.create({
        customerEmail,
        purpose,
        permissions,
        validityDays: Number(val('cr-days')),
        tppName: val('cr-name') || undefined,
      });
      toast(`Request ${created.consentId} sent to ${created.customerName}`, 'success');
      m.close();
      onSave();
    } catch (err) {
      const msg = errorMessage(err);
      if (/customer|email/i.test(msg)) setErr('email', msg);
      else toast(msg, 'error');
    }
  }));
}

// ── Data access via the Open Banking API ─────────────────────────────────────

function openDataDrawer(c: Consent) {
  const m = openModal({
    drawer: true,
    autofocus: false,
    title: `Data for ${c.customerName}`,
    subtitle: `Consent ${c.consentId}`,
    body: `
      <div class="flex gap-2" style="flex-wrap:wrap">${permissionChips(c.permissions)}</div>
      <div class="section-head section"><div class="section-title">Shared accounts</div>
        <span class="text-sm text-muted mono">GET /api/open-banking/accounts</span></div>
      <div class="card" data-accounts>${skeletonRows(3)}</div>
      <div data-tx></div>`,
    footer: '<button class="btn btn-secondary" data-close>Close</button>',
  });

  const accEl = m.el.querySelector<HTMLElement>('[data-accounts]')!;
  const txEl = m.el.querySelector<HTMLElement>('[data-tx]')!;
  const canTx = c.permissions.includes('READ_TRANSACTIONS');

  openBanking.accounts(c.consentId)
    .then((list: OpenBankingAccount[]) => {
      accEl.innerHTML = list.length ? `<div class="list">${list.map((a) => `
        <div class="list-item">
          <span class="tx-icon tone-blue">${icon('card', 16)}</span>
          <div class="grow">
            <div class="fw-600 mono">${esc(a.accountNumber)}</div>
            <div class="text-sm text-muted">${esc(accountTypeLabel(a.accountType))} · ${esc(a.holderName)} · ${esc(a.status)}</div>
          </div>
          <div class="num fw-600">${a.balance != null ? formatCurrency(a.balance) : '<span class="text-muted text-sm">No balance permission</span>'}</div>
          ${canTx ? `<button class="btn btn-secondary btn-sm" data-acc="${a.accountId}">Transactions</button>` : ''}
        </div>`).join('')}</div>` : emptyState({ title: 'No accounts shared' });

      accEl.querySelectorAll<HTMLButtonElement>('[data-acc]').forEach((b) => b.addEventListener('click', () => {
        const acc = list.find((a) => a.accountId === Number(b.dataset.acc))!;
        txEl.innerHTML = `
          <div class="section-head section"><div class="section-title">Transactions · <span class="mono">${esc(acc.accountNumber)}</span></div></div>
          <div class="card">${skeletonRows(4)}</div>`;
        openBanking.transactions(c.consentId, acc.accountId)
          .then((tx) => {
            txEl.querySelector('.card')!.innerHTML = tx.length
              ? txTableHtml(tx, { showBalance: true, compact: true })
              : emptyState({ title: 'No transactions' });
          })
          .catch((err) => { txEl.querySelector('.card')!.innerHTML = errorState(errorMessage(err)); });
      }));
    })
    .catch((err) => { accEl.innerHTML = errorState(errorMessage(err)); });
}
