import { customers, accounts, isAdmin } from '../api';
import type { Account, Customer, CustomerRequest } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, confirmDialog, withBusy, formatCurrency, initials,
  emptyState, errorState, skeletonRows, accountTypeBadge, statusBadge, debounce,
  downloadCsv, errorMessage,
} from '../utils';
import { openStatement } from '../components/tx';
import { openAccountForm } from './accounts';

export async function renderCustomers(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Customers</h1>
          <p class="page-desc">Everyone who banks with this branch, including online self-registrations.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="cust-export" disabled>${icon('download', 16)} Export</button>
          <button class="btn btn-primary" id="cust-add">${icon('userPlus', 16)} New customer</button>
        </div>
      </div>

      <div class="card">
        <div class="filters">
          <div class="search">
            ${icon('search', 16)}
            <input class="input" id="cust-search" placeholder="Search name, email or phone…" autocomplete="off" />
          </div>
          <div class="segmented" id="cust-filter">
            <button class="active" data-v="all">All</button>
            <button data-v="online">Online banking</button>
            <button data-v="branch">Branch only</button>
          </div>
          <div class="spacer"></div>
          <span class="text-sm text-muted" id="cust-count"></span>
        </div>
        <div id="cust-content">${skeletonRows(6)}</div>
      </div>
    </div>`;

  let list: Customer[] = [];
  let acctList: Account[] = [];
  let query = '';
  let filter: 'all' | 'online' | 'branch' = 'all';

  const content = document.getElementById('cust-content')!;
  const exportBtn = document.getElementById('cust-export') as HTMLButtonElement;

  document.getElementById('cust-add')!.addEventListener('click', () => openCustomerForm(null, loadData));
  document.getElementById('cust-search')!.addEventListener('input', debounce((e: Event) => {
    query = (e.target as HTMLInputElement).value.trim().toLowerCase();
    render();
  }, 150));
  document.querySelectorAll<HTMLButtonElement>('#cust-filter button').forEach((b) => {
    b.addEventListener('click', () => {
      filter = b.dataset.v as typeof filter;
      document.querySelectorAll('#cust-filter button').forEach((x) => x.classList.toggle('active', x === b));
      render();
    });
  });
  exportBtn.addEventListener('click', () => {
    downloadCsv('customers.csv',
      ['Customer ID', 'Name', 'Email', 'Phone', 'Address', 'Online banking', 'Accounts', 'Total balance'],
      visible().map((c) => {
        const own = acctList.filter((a) => a.customerId === c.customerId && a.status !== 'CLOSED');
        return [c.customerId, c.name, c.email, c.phone, c.address, c.onlineBanking ? 'Yes' : 'No',
          own.length, own.reduce((s, a) => s + a.balance, 0).toFixed(2)];
      }));
  });

  // Sync all Keycloak-registered users into PostgreSQL before loading the list,
  // so self-registered users appear even before their first login.
  try {
    await customers.adminSync();
  } catch {
    // Non-fatal — if sync fails we still show whatever is already in DB
  }

  await loadData();

  function visible(): Customer[] {
    return list.filter((c) => {
      if (filter === 'online' && !c.onlineBanking) return false;
      if (filter === 'branch' && c.onlineBanking) return false;
      if (!query) return true;
      return [c.name, c.email, c.phone, String(c.customerId)]
        .some((v) => (v ?? '').toLowerCase().includes(query));
    });
  }

  async function loadData() {
    if (!document.getElementById('cust-content')) return;
    content.innerHTML = skeletonRows(6);
    try {
      const [c, a] = await Promise.all([customers.getAll(), accounts.getAll().catch(() => [] as Account[])]);
      list = c;
      acctList = a;
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', loadData);
    }
  }

  function render() {
    const rows = visible();
    document.getElementById('cust-count')!.textContent =
      `${rows.length} of ${list.length} customer${list.length === 1 ? '' : 's'}`;
    exportBtn.disabled = rows.length === 0;

    if (list.length === 0) {
      content.innerHTML = emptyState({
        icon: 'users', title: 'No customers yet',
        text: 'Add a customer at the branch, or they can self-register for online banking.',
        action: `<button class="btn btn-primary btn-sm" data-empty-add>${icon('userPlus', 14)} New customer</button>`,
      });
      content.querySelector('[data-empty-add]')!.addEventListener('click', () => openCustomerForm(null, loadData));
      return;
    }
    if (rows.length === 0) {
      content.innerHTML = emptyState({ icon: 'search', title: 'No matches', text: 'Try a different search or filter.' });
      return;
    }

    const admin = isAdmin();
    content.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Customer</th><th>Phone</th><th>Access</th>
            <th class="num">Accounts</th><th class="num">Total balance</th><th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${rows.map((c) => {
              const own = acctList.filter((a) => a.customerId === c.customerId);
              const open = own.filter((a) => a.status !== 'CLOSED');
              const total = open.reduce((s, a) => s + a.balance, 0);
              const incomplete = !c.phone || !c.address;
              return `
                <tr class="clickable" data-id="${c.customerId}">
                  <td>
                    <div class="cell-person">
                      <span class="avatar">${esc(initials(c.name))}</span>
                      <div class="truncate">
                        <div class="cell-primary truncate">${esc(c.name)}</div>
                        <div class="cell-sub truncate">${esc(c.email)}</div>
                      </div>
                    </div>
                  </td>
                  <td class="nowrap">${c.phone ? esc(c.phone) : '<span class="text-muted">—</span>'}</td>
                  <td class="nowrap">
                    ${c.onlineBanking ? '<span class="badge badge-green">Online</span>' : '<span class="badge badge-neutral">Branch</span>'}
                    ${incomplete ? '<span class="badge badge-amber" title="Phone or address missing">KYC pending</span>' : ''}
                  </td>
                  <td class="num">${open.length}</td>
                  <td class="num fw-600">${formatCurrency(total)}</td>
                  <td class="actions">
                    <button class="btn-icon" data-view="${c.customerId}" title="View">${icon('eye', 16)}</button>
                    <button class="btn-icon" data-edit="${c.customerId}" title="Edit">${icon('pencil', 16)}</button>
                    ${admin ? `<button class="btn-icon danger" data-delete="${c.customerId}" ${own.length ? 'disabled title="Customers with accounts cannot be deleted"' : 'title="Delete"'}>${icon('trash', 16)}</button>` : ''}
                  </td>
                </tr>`;
            }).join('')}
          </tbody>
        </table>
      </div>`;

    const byId = (id: string | undefined) => list.find((c) => c.customerId === Number(id))!;

    content.querySelectorAll<HTMLTableRowElement>('tr[data-id]').forEach((tr) => {
      tr.addEventListener('click', (e) => {
        if ((e.target as HTMLElement).closest('button')) return;
        openCustomerDrawer(byId(tr.dataset.id), acctList, loadData);
      });
    });
    content.querySelectorAll<HTMLButtonElement>('[data-view]').forEach((b) =>
      b.addEventListener('click', () => openCustomerDrawer(byId(b.dataset.view), acctList, loadData)));
    content.querySelectorAll<HTMLButtonElement>('[data-edit]').forEach((b) =>
      b.addEventListener('click', () => openCustomerForm(byId(b.dataset.edit), loadData)));
    content.querySelectorAll<HTMLButtonElement>('[data-delete]').forEach((b) =>
      b.addEventListener('click', async () => {
        const c = byId(b.dataset.delete);
        const ok = await confirmDialog({
          title: `Delete ${c.name}?`,
          message: 'This permanently removes the customer record. This cannot be undone.',
          confirmText: 'Delete customer',
          tone: 'danger',
        });
        if (!ok) return;
        try {
          await customers.delete(c.customerId);
          toast(`${c.name} was deleted`, 'success');
          await loadData();
        } catch (err) {
          toast(errorMessage(err), 'error');
        }
      }));
  }
}

// ── Customer drawer ──────────────────────────────────────────────────────────

function openCustomerDrawer(c: Customer, acctList: Account[], onChange: () => void) {
  const own = acctList.filter((a) => a.customerId === c.customerId);
  const total = own.filter((a) => a.status !== 'CLOSED').reduce((s, a) => s + a.balance, 0);

  const m = openModal({
    drawer: true,
    autofocus: false,
    title: 'Customer profile',
    subtitle: `Customer #${c.customerId}`,
    body: `
      <div class="profile-head">
        <span class="avatar avatar-lg">${esc(initials(c.name))}</span>
        <div>
          <div style="font-size:18px;font-weight:700">${esc(c.name)}</div>
          <div class="text-muted">${esc(c.email)}</div>
          <div class="mt-2 flex gap-2">
            ${c.onlineBanking ? '<span class="badge badge-green">Online banking</span>' : '<span class="badge badge-neutral">Branch customer</span>'}
          </div>
        </div>
      </div>

      <div class="card mt-4"><div class="card-body">
        <dl class="dl">
          <dt>Phone</dt><dd>${c.phone ? esc(c.phone) : '<span class="text-muted">Not provided</span>'}</dd>
          <dt>Email</dt><dd>${esc(c.email)}</dd>
          <dt>Address</dt><dd>${c.address ? esc(c.address) : '<span class="text-muted">Not provided</span>'}</dd>
          <dt>Total balance</dt><dd class="num">${formatCurrency(total)}</dd>
        </dl>
      </div></div>

      <div class="section-head section">
        <div class="section-title">Accounts <span class="text-muted" style="font-weight:400">· ${own.length}</span></div>
        <button class="btn btn-secondary btn-sm" data-open-account>${icon('plus', 14)} Open account</button>
      </div>
      <div class="card">
        ${own.length ? `<div class="list">${own.map((a) => `
          <div class="list-item">
            <span class="tx-icon tone-blue">${icon('card', 16)}</span>
            <div class="grow">
              <div class="fw-600 mono">${esc(a.accountNumber)}</div>
              <div class="flex gap-2 mt-1">${accountTypeBadge(a.accountType)} ${statusBadge(a.status)}</div>
            </div>
            <div class="num fw-600">${formatCurrency(a.balance)}</div>
            <button class="btn-icon" data-statement="${a.accountId}" title="Statement">${icon('file', 16)}</button>
          </div>`).join('')}</div>`
        : emptyState({ icon: 'card', title: 'No accounts yet', text: 'Open the first account for this customer.' })}
      </div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Close</button>
      <button class="btn btn-primary" data-edit>${icon('pencil', 16)} Edit details</button>`,
  });

  m.el.querySelector('[data-edit]')!.addEventListener('click', () => {
    m.close();
    openCustomerForm(c, onChange);
  });
  m.el.querySelector('[data-open-account]')!.addEventListener('click', () => {
    m.close();
    openAccountForm(null, onChange, c.customerId);
  });
  m.el.querySelectorAll<HTMLButtonElement>('[data-statement]').forEach((b) => {
    const acc = own.find((a) => a.accountId === Number(b.dataset.statement))!;
    b.addEventListener('click', () => openStatement(acc, { showPerformedBy: true }));
  });
}

// ── Create / edit form ───────────────────────────────────────────────────────

const PHONE_RE = /^\+?[0-9][0-9 -]{8,14}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function openCustomerForm(existing: Customer | null, onSave: () => void) {
  const isEdit = existing !== null;
  const m = openModal({
    title: isEdit ? 'Edit customer' : 'New customer',
    subtitle: isEdit ? `Customer #${existing.customerId}` : 'Create a KYC record. Accounts can be opened afterwards.',
    body: `
      <form class="form-grid" id="customer-form" novalidate>
        <div class="field" data-field="name">
          <label class="field-label" for="cf-name">Full name<span class="req">*</span></label>
          <input id="cf-name" class="input" maxlength="100" value="${esc(existing?.name ?? '')}" autocomplete="off" />
          <div class="field-error" data-error></div>
        </div>
        <div class="field" data-field="email">
          <label class="field-label" for="cf-email">Email<span class="req">*</span></label>
          <input id="cf-email" class="input" type="email" maxlength="150" value="${esc(existing?.email ?? '')}" autocomplete="off" />
          <div class="field-error" data-error></div>
        </div>
        <div class="field" data-field="phone">
          <label class="field-label" for="cf-phone">Mobile number<span class="req">*</span></label>
          <input id="cf-phone" class="input" inputmode="tel" maxlength="16" placeholder="e.g. 9876543210" value="${esc(existing?.phone ?? '')}" autocomplete="off" />
          <div class="field-error" data-error></div>
        </div>
        <div class="field" data-field="address">
          <label class="field-label" for="cf-address">Address<span class="req">*</span></label>
          <textarea id="cf-address" class="textarea" maxlength="255" rows="2">${esc(existing?.address ?? '')}</textarea>
          <div class="field-error" data-error></div>
        </div>
        ${isEdit && existing.onlineBanking ? `<div class="callout callout-info">${icon('info', 16)}<div>This customer signs in with online banking. Changing the email here does not change their login email.</div></div>` : ''}
      </form>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>${isEdit ? 'Save changes' : 'Create customer'}</button>`,
  });

  const root = m.el;
  const val = (id: string) => (root.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!.value).trim();
  const setErr = (field: string, msg: string | null) => {
    const f = root.querySelector<HTMLElement>(`[data-field="${field}"]`)!;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };
  root.querySelectorAll('input, textarea').forEach((el) => el.addEventListener('input', () => {
    const field = (el.closest('[data-field]') as HTMLElement | null)?.dataset.field;
    if (field) setErr(field, null);
  }));

  const save = root.querySelector<HTMLButtonElement>('[data-save]')!;
  const submit = () => withBusy(save, async () => {
    const data: CustomerRequest = {
      name: val('cf-name'),
      email: val('cf-email'),
      phone: val('cf-phone'),
      address: val('cf-address'),
    };
    let ok = true;
    if (!data.name) { setErr('name', 'Name is required'); ok = false; }
    if (!EMAIL_RE.test(data.email)) { setErr('email', 'Enter a valid email address'); ok = false; }
    if (!PHONE_RE.test(data.phone)) { setErr('phone', 'Enter a valid phone number (10–15 digits)'); ok = false; }
    if (!data.address) { setErr('address', 'Address is required'); ok = false; }
    if (!ok) return;

    try {
      if (isEdit) {
        await customers.update(existing.customerId, data);
        toast('Customer details updated', 'success');
      } else {
        const created = await customers.create(data);
        toast(`${created.name} added as customer #${created.customerId}`, 'success');
      }
      m.close();
      onSave();
    } catch (err) {
      const msg = errorMessage(err);
      if (/email/i.test(msg)) setErr('email', msg);
      else toast(msg, 'error');
    }
  });
  save.addEventListener('click', submit);
  root.querySelector('form')!.addEventListener('submit', (e) => { e.preventDefault(); void submit(); });
}
