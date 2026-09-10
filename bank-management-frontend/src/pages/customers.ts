import { customers } from '../api';
import type { Customer, CustomerRequest } from '../api';
import { toast, showModal } from '../utils';

export async function renderCustomers(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="toolbar">
        <h2>Customers</h2>
        <button class="btn btn-primary btn-sm" id="add-customer-btn">+ Add Customer</button>
      </div>
      <div class="card">
        <div id="customers-content"><div class="loading">Loading...</div></div>
      </div>
    </div>
  `;

  document.getElementById('add-customer-btn')!.addEventListener('click', () => openForm(null, loadData));
  await loadData();

  async function loadData() {
    const content = document.getElementById('customers-content');
    if (!content) return;
    try {
      const list = await customers.getAll();
      if (list.length === 0) {
        content.innerHTML = '<div class="empty">No customers found.</div>';
        return;
      }
      content.innerHTML = `
        <div class="table-wrap">
          <table>
            <thead><tr>
              <th>ID</th><th>Name</th><th>Email</th><th>Phone</th><th>Address</th><th>Actions</th>
            </tr></thead>
            <tbody>
              ${list.map(c => `
                <tr>
                  <td>${c.customerId}</td>
                  <td>${c.name}</td>
                  <td>${c.email}</td>
                  <td>${c.phone}</td>
                  <td>${c.address}</td>
                  <td class="actions">
                    <button class="btn btn-outline btn-sm" data-edit="${c.customerId}">Edit</button>
                    <button class="btn btn-danger btn-sm" data-delete="${c.customerId}">Delete</button>
                  </td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;

      content.querySelectorAll('[data-edit]').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = parseInt((btn as HTMLElement).dataset.edit!);
          const customer = list.find(c => c.customerId === id)!;
          openForm(customer, loadData);
        });
      });

      content.querySelectorAll('[data-delete]').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = parseInt((btn as HTMLElement).dataset.delete!);
          if (!confirm('Delete this customer?')) return;
          try {
            await customers.delete(id);
            toast('Customer deleted', 'success');
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

function openForm(existing: Customer | null, onSave: () => void) {
  const isEdit = existing !== null;
  const overlay = showModal(`
    <div class="modal-header">
      <h3>${isEdit ? 'Edit Customer' : 'Add Customer'}</h3>
      <button class="btn-close" id="close-modal">&times;</button>
    </div>
    <div class="modal-body">
      <form id="customer-form">
        <div class="form-group">
          <label>Name</label>
          <input id="cf-name" type="text" value="${existing?.name ?? ''}" required />
        </div>
        <div class="form-group">
          <label>Email</label>
          <input id="cf-email" type="email" value="${existing?.email ?? ''}" required />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Phone</label>
            <input id="cf-phone" type="text" value="${existing?.phone ?? ''}" required />
          </div>
          <div class="form-group">
            <label>Address</label>
            <input id="cf-address" type="text" value="${existing?.address ?? ''}" required />
          </div>
        </div>
      </form>
    </div>
    <div class="modal-footer">
      <button class="btn btn-outline btn-sm" id="cancel-modal">Cancel</button>
      <button class="btn btn-primary btn-sm" id="save-customer">Save</button>
    </div>
  `);

  overlay.querySelector('#close-modal')!.addEventListener('click', () => overlay.remove());
  overlay.querySelector('#cancel-modal')!.addEventListener('click', () => overlay.remove());

  overlay.querySelector('#save-customer')!.addEventListener('click', async () => {
    const data: CustomerRequest = {
      name: (document.getElementById('cf-name') as HTMLInputElement).value.trim(),
      email: (document.getElementById('cf-email') as HTMLInputElement).value.trim(),
      phone: (document.getElementById('cf-phone') as HTMLInputElement).value.trim(),
      address: (document.getElementById('cf-address') as HTMLInputElement).value.trim(),
    };
    if (!data.name || !data.email || !data.phone || !data.address) {
      toast('All fields are required', 'error');
      return;
    }
    try {
      if (isEdit) {
        await customers.update(existing!.customerId, data);
        toast('Customer updated', 'success');
      } else {
        await customers.create(data);
        toast('Customer created', 'success');
      }
      overlay.remove();
      onSave();
    } catch (err: unknown) {
      toast((err as Error).message, 'error');
    }
  });
}
