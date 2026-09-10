import { accounts, customers } from '../api';
import type { Account, AccountRequest } from '../api';
import { toast, showModal, formatCurrency } from '../utils';

export async function renderAccounts(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="toolbar">
        <h2>Accounts</h2>
        <button class="btn btn-primary btn-sm" id="add-account-btn">+ Add Account</button>
      </div>
      <div class="card">
        <div id="accounts-content"><div class="loading">Loading...</div></div>
      </div>
    </div>
  `;

  document.getElementById('add-account-btn')!.addEventListener('click', () => openForm(null, loadData));
  await loadData();

  async function loadData() {
    const content = document.getElementById('accounts-content');
    if (!content) return;
    try {
      const list = await accounts.getAll();
      if (list.length === 0) {
        content.innerHTML = '<div class="empty">No accounts found.</div>';
        return;
      }
      content.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead><tr>
              <th>ID</th><th>Account No.</th><th>Type</th><th>Balance</th><th>Customer</th><th>Actions</th>
            </tr></thead>
            <tbody>
              ${list.map(a => `
                <tr>
                  <td>${a.accountId}</td>
                  <td><code>${a.accountNumber}</code></td>
                  <td><span class="badge badge-blue">${a.accountType}</span></td>
                  <td class="text-success">${formatCurrency(a.balance)}</td>
                  <td>${a.customerName}</td>
                  <td class="actions">
                    <button class="btn btn-outline btn-sm" data-edit="${a.accountId}">Edit</button>
                    <button class="btn btn-danger btn-sm" data-delete="${a.accountId}">Delete</button>
                  </td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;

      content.querySelectorAll('[data-edit]').forEach(btn => {
        btn.addEventListener('click', () => {
          const id = parseInt((btn as HTMLElement).dataset.edit!);
          const account = list.find(a => a.accountId === id)!;
          openForm(account, loadData);
        });
      });

      content.querySelectorAll('[data-delete]').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = parseInt((btn as HTMLElement).dataset.delete!);
          if (!confirm('Delete this account?')) return;
          try {
            await accounts.delete(id);
            toast('Account deleted', 'success');
            await loadData();
          } catch (err: unknown) {
            toast((err as Error).message, 'error');
          }
        });
      });
    } catch (err: unknown) {
      content.innerHTML = `<div class="empty text-danger">${(err as Error).message}</div>`;
    }
  }
}

async function openForm(existing: Account | null, onSave: () => void) {
  let customerOptions = '<option value="">Loading...</option>';
  try {
    const custList = await customers.getAll();
    customerOptions = custList.map(c =>
      `<option value="${c.customerId}" ${existing && existing.customerName === c.name ? 'selected' : ''}>${c.customerId} - ${c.name}</option>`
    ).join('');
  } catch {
    customerOptions = '<option value="">Failed to load</option>';
  }

  const isEdit = existing !== null;
  const overlay = showModal(`
    <div class="modal-header">
      <h3>${isEdit ? 'Edit Account' : 'Add Account'}</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body">
      <form id="account-form">
        <div class="form-group">
          <label>Account Number</label>
          <input id="af-number" type="text" value="${existing?.accountNumber ?? ''}" required />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Account Type</label>
            <select id="af-type">
              <option value="SAVINGS" ${existing?.accountType === 'SAVINGS' ? 'selected' : ''}>Savings</option>
              <option value="CURRENT" ${existing?.accountType === 'CURRENT' ? 'selected' : ''}>Current</option>
              <option value="FIXED_DEPOSIT" ${existing?.accountType === 'FIXED_DEPOSIT' ? 'selected' : ''}>Fixed Deposit</option>
            </select>
          </div>
          <div class="form-group">
            <label>Initial Balance (₹)</label>
            <input id="af-balance" type="number" min="0" step="0.01" value="${existing?.balance ?? 0}" required />
          </div>
        </div>
        <div class="form-group">
          <label>Customer</label>
          <select id="af-customer">${customerOptions}</select>
        </div>
      </form>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="cancel-modal">Cancel</button>
      <button class="btn btn-primary btn-sm" id="save-account">Save</button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#cancel-modal')!.addEventListener('click', () => overlay.remove());

  overlay.querySelector('#save-account')!.addEventListener('click', async () => {
    const data: AccountRequest = {
      accountNumber: (document.getElementById('af-number') as HTMLInputElement).value.trim(),
      accountType: (document.getElementById('af-type') as HTMLSelectElement).value,
      balance: parseFloat((document.getElementById('af-balance') as HTMLInputElement).value),
      customerId: parseInt((document.getElementById('af-customer') as HTMLSelectElement).value),
    };
    if (!data.accountNumber || !data.customerId) {
      toast('All fields are required', 'error');
      return;
    }
    try {
      if (isEdit) {
        await accounts.update(existing!.accountId, data);
        toast('Account updated', 'success');
      } else {
        await accounts.create(data);
        toast('Account created', 'success');
      }
      overlay.remove();
      onSave();
    } catch (err: unknown) {
      toast((err as Error).message, 'error');
    }
  });
}
