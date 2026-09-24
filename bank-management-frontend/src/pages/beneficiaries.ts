import { accounts, beneficiaries, customers, isStaff, isChecker, canManageCustomers } from '../api';
import type { Account, Beneficiary, Customer } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, confirmDialog, withBusy, initials, formatDay, emptyState,
  errorState, skeletonRows, accountTypeBadge, debounce, errorMessage,
} from '../utils';
import { openTransferModal } from '../components/money';

/**
 * Customers: their saved payees — add, pay, delete.
 * Staff: every customer's payees; ADMIN/EMPLOYEE can add on a customer's
 * behalf, ADMIN/CHECKER can delete.
 */
export async function renderBeneficiaries(container: HTMLElement) {
  const staff = isStaff();
  const canAdd = !staff || canManageCustomers();

  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Beneficiaries</h1>
          <p class="page-desc">${staff
            ? 'Saved payees of every customer.'
            : 'People you pay regularly. Add them once, then transfer in two taps.'}</p>
        </div>
        <div class="page-actions">
          ${canAdd ? `<button class="btn btn-primary" id="ben-add">${icon('userPlus', 16)} Add beneficiary</button>` : ''}
        </div>
      </div>
      <div class="card">
        <div class="filters">
          <div class="search">
            ${icon('search', 16)}
            <input class="input" id="ben-search" placeholder="Search nickname, account${staff ? ' or customer' : ''}…" autocomplete="off" />
          </div>
          <div class="spacer"></div>
          <span class="text-sm text-muted" id="ben-count"></span>
        </div>
        <div id="ben-content">${skeletonRows(5)}</div>
      </div>
    </div>`;

  let list: Beneficiary[] = [];
  let myAccounts: Account[] = [];
  let query = '';
  const content = document.getElementById('ben-content')!;

  document.getElementById('ben-add')?.addEventListener('click', () => openBeneficiaryForm(load));
  document.getElementById('ben-search')!.addEventListener('input', debounce((e: Event) => {
    query = (e.target as HTMLInputElement).value.trim().toLowerCase();
    render();
  }, 150));

  await load();

  async function load() {
    if (!document.getElementById('ben-content')) return;
    content.innerHTML = skeletonRows(5);
    try {
      const [b, a] = await Promise.all([
        beneficiaries.getAll(),
        staff ? Promise.resolve([] as Account[]) : accounts.getMy().catch(() => [] as Account[]),
      ]);
      list = b;
      myAccounts = a;
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  }

  function render() {
    const rows = list.filter((b) => !query
      || [b.nickname, b.accountNumber, b.holderName, b.customerName].some((v) => (v ?? '').toLowerCase().includes(query)));
    document.getElementById('ben-count')!.textContent = `${rows.length} beneficiar${rows.length === 1 ? 'y' : 'ies'}`;

    if (list.length === 0) {
      content.innerHTML = emptyState({
        icon: 'users', title: 'No beneficiaries yet',
        text: staff ? 'Customers have not saved any payees.' : 'Add someone you pay regularly using their State Bank account number.',
        action: canAdd ? `<button class="btn btn-primary btn-sm" data-empty-add>${icon('userPlus', 14)} Add beneficiary</button>` : '',
      });
      content.querySelector('[data-empty-add]')?.addEventListener('click', () => openBeneficiaryForm(load));
      return;
    }
    if (rows.length === 0) {
      content.innerHTML = emptyState({ icon: 'search', title: 'No matches' });
      return;
    }

    const canDelete = !staff || isChecker();
    content.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Beneficiary</th>
            ${staff ? '<th>Customer</th>' : ''}
            <th>Account</th><th>Type</th><th>Status</th><th>Added</th>
            <th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${rows.map((b) => `
              <tr>
                <td>
                  <div class="cell-person">
                    <span class="avatar">${esc(initials(b.nickname))}</span>
                    <div class="truncate">
                      <div class="cell-primary truncate">${esc(b.nickname)}</div>
                      <div class="cell-sub truncate">${esc(b.holderName)}</div>
                    </div>
                  </div>
                </td>
                ${staff ? `<td>${esc(b.customerName)} <span class="text-muted text-sm">#${b.customerId}</span></td>` : ''}
                <td class="nowrap"><span class="mono">${esc(b.accountNumber)}</span></td>
                <td>${b.accountType ? accountTypeBadge(b.accountType) : '—'}</td>
                <td>${b.canReceive
                  ? '<span class="badge badge-dot badge-green">Ready</span>'
                  : '<span class="badge badge-dot badge-red" title="Account is frozen, closed or a Fixed Deposit">Cannot receive</span>'}</td>
                <td class="nowrap text-muted">${esc(formatDay(b.createdAt))}</td>
                <td class="actions">
                  ${!staff ? `<button class="btn btn-secondary btn-sm" data-pay="${b.beneficiaryId}" ${b.canReceive ? '' : 'disabled'}>${icon('transfer', 14)} Pay</button>` : ''}
                  ${canDelete ? `<button class="btn-icon danger" data-del="${b.beneficiaryId}" title="Delete">${icon('trash', 16)}</button>` : ''}
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    const byId = (id: string | undefined) => list.find((b) => b.beneficiaryId === Number(id))!;
    content.querySelectorAll<HTMLButtonElement>('[data-pay]').forEach((btn) => btn.addEventListener('click', () => {
      const b = byId(btn.dataset.pay);
      openTransferModal(myAccounts, null, load, {
        ownAccounts: myAccounts, beneficiaries: list, presetToNumber: b.accountNumber,
      });
    }));
    content.querySelectorAll<HTMLButtonElement>('[data-del]').forEach((btn) => btn.addEventListener('click', async () => {
      const b = byId(btn.dataset.del);
      const ok = await confirmDialog({
        title: `Remove ${b.nickname}?`,
        message: `${b.accountNumber} will be removed from ${staff ? `${b.customerName}'s` : 'your'} beneficiary list. Past transfers are not affected.`,
        confirmText: 'Remove',
        tone: 'danger',
      });
      if (!ok) return;
      try {
        await beneficiaries.delete(b.beneficiaryId);
        toast(`${b.nickname} removed`, 'success');
        await load();
      } catch (err) {
        toast(errorMessage(err), 'error');
      }
    }));
  }
}

// ── Add form ─────────────────────────────────────────────────────────────────

async function openBeneficiaryForm(onSave: () => void) {
  const staff = isStaff();
  let custList: Customer[] = [];
  if (staff) {
    try {
      custList = (await customers.getAll()).sort((a, b) => a.name.localeCompare(b.name));
    } catch (err) {
      toast(errorMessage(err), 'error');
      return;
    }
  }

  const m = openModal({
    title: 'Add beneficiary',
    subtitle: 'Only State Bank accounts can be added. We verify the account before saving.',
    body: `
      <form class="form-grid" novalidate>
        ${staff ? `
        <div class="field" data-field="customer">
          <label class="field-label" for="bf-customer">For customer<span class="req">*</span></label>
          <select id="bf-customer" class="select">
            <option value="">Select a customer…</option>
            ${custList.map((c) => `<option value="${c.customerId}">${esc(c.name)} · #${c.customerId}</option>`).join('')}
          </select>
          <div class="field-error" data-error></div>
        </div>` : ''}
        <div class="field" data-field="number">
          <label class="field-label" for="bf-number">Account number<span class="req">*</span></label>
          <div class="flex gap-2">
            <input id="bf-number" class="input mono" maxlength="30" placeholder="e.g. SB-12345678" autocomplete="off" style="text-transform:uppercase" />
            <button type="button" class="btn btn-secondary" data-verify>Verify</button>
          </div>
          <div class="field-error" data-error></div>
          <div data-holder></div>
        </div>
        <div class="field" data-field="nickname">
          <label class="field-label" for="bf-nickname">Nickname<span class="req">*</span></label>
          <input id="bf-nickname" class="input" maxlength="60" placeholder="e.g. Landlord, Mom" autocomplete="off" />
          <div class="field-error" data-error></div>
        </div>
      </form>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>Save beneficiary</button>`,
  });

  const root = m.el;
  const numberEl = root.querySelector<HTMLInputElement>('#bf-number')!;
  const nickEl = root.querySelector<HTMLInputElement>('#bf-nickname')!;
  const custEl = root.querySelector<HTMLSelectElement>('#bf-customer');
  const holderEl = root.querySelector<HTMLElement>('[data-holder]')!;
  const setErr = (field: string, msg: string | null) => {
    const f = root.querySelector<HTMLElement>(`[data-field="${field}"]`);
    if (!f) return;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };

  const verify = async () => {
    const number = numberEl.value.trim().toUpperCase();
    holderEl.innerHTML = '';
    setErr('number', null);
    if (!number) { setErr('number', 'Enter an account number'); return; }
    try {
      const info = await accounts.lookup(number);
      holderEl.innerHTML = `
        <div class="beneficiary mt-2 ${info.canReceive ? '' : 'bad'}">
          <div class="grow">
            <div class="fw-600">${esc(info.holderName)}</div>
            <div class="text-sm text-muted">${esc(info.accountNumber)}</div>
          </div>
          ${info.canReceive ? '<span class="badge badge-green">Verified</span>' : '<span class="badge badge-red">Cannot receive transfers</span>'}
        </div>`;
      if (!nickEl.value.trim()) nickEl.value = info.holderName.split(' ')[0];
    } catch (err) {
      setErr('number', errorMessage(err));
    }
  };
  const verifyBtn = root.querySelector<HTMLButtonElement>('[data-verify]')!;
  verifyBtn.addEventListener('click', () => withBusy(verifyBtn, verify));
  numberEl.addEventListener('blur', () => { if (numberEl.value.trim() && !holderEl.innerHTML) void withBusy(verifyBtn, verify); });
  numberEl.addEventListener('input', () => { holderEl.innerHTML = ''; setErr('number', null); });
  nickEl.addEventListener('input', () => setErr('nickname', null));
  custEl?.addEventListener('change', () => setErr('customer', null));

  const save = root.querySelector<HTMLButtonElement>('[data-save]')!;
  save.addEventListener('click', () => withBusy(save, async () => {
    const accountNumber = numberEl.value.trim().toUpperCase();
    const nickname = nickEl.value.trim();
    let ok = true;
    if (custEl && !custEl.value) { setErr('customer', 'Choose the customer'); ok = false; }
    if (!accountNumber) { setErr('number', 'Enter an account number'); ok = false; }
    if (!nickname) { setErr('nickname', 'Give this beneficiary a nickname'); ok = false; }
    if (!ok) return;
    try {
      const created = await beneficiaries.create({
        accountNumber, nickname, customerId: custEl ? Number(custEl.value) : undefined,
      });
      toast(`${created.nickname} added to beneficiaries`, 'success');
      m.close();
      onSave();
    } catch (err) {
      const msg = errorMessage(err);
      if (/account/i.test(msg)) setErr('number', msg);
      else toast(msg, 'error');
    }
  }));
}
