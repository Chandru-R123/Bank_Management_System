import keycloak from '../keycloak';
import { customers, getUsername } from '../api';
import type { Customer } from '../api';
import { icon } from '../icons';
import type { IconName } from '../icons';
import { esc, initials, toast, withBusy, errorState, errorMessage } from '../utils';

const PHONE_RE = /^\+?[0-9][0-9 -]{8,14}$/;

export async function renderProfile(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Profile</h1>
          <p class="page-desc">Your contact details and sign-in security.</p>
        </div>
      </div>
      <div class="grid grid-3-1" id="profile-root">
        <div class="card"><div class="card-body"><span class="skeleton" style="height:260px"></span></div></div>
        <div class="card"><div class="card-body"><span class="skeleton" style="height:160px"></span></div></div>
      </div>
    </div>`;

  const root = document.getElementById('profile-root')!;

  const load = async () => {
    try {
      render(await customers.getMe());
    } catch (err) {
      root.innerHTML = `<div class="card">${errorState(errorMessage(err))}</div>`;
      root.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  };

  const infoRow = (ic: IconName, label: string, value: string | null) => `
    <div class="info-row">
      <span class="info-icon">${icon(ic, 16)}</span>
      <div>
        <div class="info-label">${esc(label)}</div>
        <div class="info-value">${value ? esc(value) : '<span class="text-muted">Not provided</span>'}</div>
      </div>
    </div>`;

  function render(p: Customer) {
    root.innerHTML = `
      <div class="card">
        <div class="card-header">
          <div class="profile-head">
            <span class="avatar avatar-lg">${esc(initials(p.name))}</span>
            <div>
              <div style="font-size:18px;font-weight:700">${esc(p.name)}</div>
              <div class="text-muted text-sm">Customer ID #${p.customerId}</div>
            </div>
          </div>
          <button class="btn btn-secondary btn-sm" id="edit-btn">${icon('pencil', 14)} Edit contact details</button>
        </div>
        <div class="card-body" id="profile-body">
          ${infoRow('mail', 'Email', p.email)}
          ${infoRow('phone', 'Mobile number', p.phone)}
          ${infoRow('mapPin', 'Address', p.address)}
          <p class="text-sm text-muted mt-3">To change your name or email, please visit your branch with a valid ID (KYC).</p>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><div class="card-title">Security</div><div class="card-sub">Signed in as ${esc(getUsername())}</div></div></div>
        <div class="card-body">
          <div class="callout callout-success">${icon('shield', 16)}<div>Your session is protected with single sign-on and PKCE.</div></div>
          <button class="btn btn-secondary btn-block mt-4" id="pwd-btn">${icon('key', 16)} Change password</button>
          <button class="btn btn-ghost btn-block mt-2" id="signout-btn">${icon('logout', 16)} Sign out</button>
        </div>
      </div>`;

    document.getElementById('pwd-btn')!.addEventListener('click', () => {
      void keycloak.accountManagement();
    });
    document.getElementById('signout-btn')!.addEventListener('click', () => {
      void keycloak.logout({ redirectUri: window.location.origin + '/' });
    });
    document.getElementById('edit-btn')!.addEventListener('click', () => renderEdit(p));
  }

  function renderEdit(p: Customer) {
    const body = document.getElementById('profile-body')!;
    body.innerHTML = `
      <form class="form-grid" id="profile-form" novalidate>
        <div class="field">
          <label class="field-label">Email</label>
          <input class="input" value="${esc(p.email)}" disabled />
        </div>
        <div class="field" data-field="phone">
          <label class="field-label" for="pf-phone">Mobile number<span class="req">*</span></label>
          <input id="pf-phone" class="input" inputmode="tel" maxlength="16" value="${esc(p.phone ?? '')}" placeholder="e.g. 9876543210" />
          <div class="field-error" data-error></div>
        </div>
        <div class="field" data-field="address">
          <label class="field-label" for="pf-address">Address<span class="req">*</span></label>
          <textarea id="pf-address" class="textarea" maxlength="255" rows="3">${esc(p.address ?? '')}</textarea>
          <div class="field-error" data-error></div>
        </div>
        <div class="flex gap-2" style="justify-content:flex-end">
          <button type="button" class="btn btn-secondary" id="pf-cancel">Cancel</button>
          <button type="submit" class="btn btn-primary" id="pf-save">Save changes</button>
        </div>
      </form>`;
    (document.getElementById('edit-btn') as HTMLButtonElement).disabled = true;
    document.getElementById('pf-phone')!.focus();

    const setErr = (field: string, msg: string | null) => {
      const f = body.querySelector<HTMLElement>(`[data-field="${field}"]`)!;
      f.classList.toggle('has-error', !!msg);
      f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
    };

    document.getElementById('pf-cancel')!.addEventListener('click', () => render(p));
    const saveBtn = document.getElementById('pf-save') as HTMLButtonElement;
    document.getElementById('profile-form')!.addEventListener('submit', (e) => {
      e.preventDefault();
      void withBusy(saveBtn, async () => {
        const phone = (document.getElementById('pf-phone') as HTMLInputElement).value.trim();
        const address = (document.getElementById('pf-address') as HTMLTextAreaElement).value.trim();
        let ok = true;
        if (!PHONE_RE.test(phone)) { setErr('phone', 'Enter a valid phone number (10–15 digits)'); ok = false; } else setErr('phone', null);
        if (!address) { setErr('address', 'Address is required'); ok = false; } else setErr('address', null);
        if (!ok) return;
        try {
          const updated = await customers.updateMe({ phone, address });
          toast('Contact details updated', 'success');
          render(updated);
        } catch (err) {
          toast(errorMessage(err), 'error');
        }
      });
    });
  }

  await load();
}
