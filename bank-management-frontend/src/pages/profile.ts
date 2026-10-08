import keycloak from '../keycloak';
import { customers, getUsername, captcha } from '../api';
import type { Customer } from '../api';
import { icon } from '../icons';
import type { IconName } from '../icons';
import { esc, initials, toast, withBusy, errorState, errorMessage } from '../utils';
import { mountCaptcha } from '../components/captcha';
import type { CaptchaWidget } from '../components/captcha';

const PHONE_RE = /^\+?[0-9][0-9 -]{8,14}$/;

// Site key baked in at Vite build time from VITE_CAPTCHA_SITE_KEY
const SITE_KEY: string = import.meta.env.VITE_CAPTCHA_SITE_KEY ?? '';
const CAPTCHA_ON = SITE_KEY.length > 0;

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
        <div class="card"><div class="card-body">
          <span class="skeleton" style="height:260px;display:block"></span>
        </div></div>
        <div class="card"><div class="card-body">
          <span class="skeleton" style="height:160px;display:block"></span>
        </div></div>
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
        <div class="info-value">${value
          ? esc(value)
          : '<span class="text-muted">Not provided</span>'}</div>
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
          <button class="btn btn-secondary btn-sm" id="edit-btn">
            ${icon('pencil', 14)} Edit contact details
          </button>
        </div>
        <div class="card-body" id="profile-body">
          ${infoRow('mail',   'Email',         p.email)}
          ${infoRow('phone',  'Mobile number', p.phone)}
          ${infoRow('mapPin', 'Address',       p.address)}
          <p class="text-sm text-muted mt-3">
            To change your name or email, visit your branch with a valid ID.
          </p>
        </div>
      </div>

      <!-- Right column: Security only -->
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">Security</div>
            <div class="card-sub">Signed in as <strong>${esc(getUsername())}</strong></div>
          </div>
        </div>
        <div class="card-body">
          <div class="callout callout-success">
            ${icon('shield', 16)}
            <div>Session protected with OAuth2 PKCE + Keycloak SSO.</div>
          </div>
          <button class="btn btn-secondary btn-block mt-4" id="pwd-btn">
            ${icon('key', 16)} Change password
          </button>
          <button class="btn btn-ghost btn-block mt-2" id="signout-btn">
            ${icon('logout', 16)} Sign out
          </button>
        </div>
      </div>`;

    document.getElementById('pwd-btn')!.addEventListener('click', () =>
      void keycloak.accountManagement());
    document.getElementById('signout-btn')!.addEventListener('click', () =>
      void keycloak.logout({ redirectUri: window.location.origin + '/' }));
    document.getElementById('edit-btn')!.addEventListener('click', () => renderEdit(p));
  }

  // ── Edit form ───────────────────────────────────────────────────────────────

  function renderEdit(p: Customer) {
    const body = document.getElementById('profile-body')!;
    body.innerHTML = `
      <form class="form-grid" id="profile-form" novalidate>
        <div class="field">
          <label class="field-label">Email</label>
          <input class="input" value="${esc(p.email)}" disabled />
        </div>

        <div class="field" data-field="phone">
          <label class="field-label" for="pf-phone">
            Mobile number<span class="req">*</span>
          </label>
          <input id="pf-phone" class="input" inputmode="tel" maxlength="16"
            value="${esc(p.phone ?? '')}" placeholder="e.g. 9876543210" />
          <div class="field-error" data-error></div>
        </div>

        <div class="field" data-field="address">
          <label class="field-label" for="pf-address">
            Address<span class="req">*</span>
          </label>
          <textarea id="pf-address" class="textarea" maxlength="255"
            rows="3">${esc(p.address ?? '')}</textarea>
          <div class="field-error" data-error></div>
        </div>

        ${CAPTCHA_ON ? `
        <div class="field" data-field="captcha">
          <label class="field-label">
            ${icon('shield', 14)} Human verification<span class="req">*</span>
          </label>
          <div id="pf-captcha-wrap" style="margin-top:6px"></div>
          <div class="field-error" id="pf-captcha-err" style="margin-top:4px"></div>
        </div>` : ''}

        <div class="flex gap-2" style="justify-content:flex-end">
          <button type="button" class="btn btn-secondary" id="pf-cancel">Cancel</button>
          <button type="submit" class="btn btn-primary" id="pf-save">
            ${icon('checkCircle', 14)} Save changes
          </button>
        </div>
      </form>`;

    (document.getElementById('edit-btn') as HTMLButtonElement).disabled = true;
    document.getElementById('pf-phone')!.focus();

    let formWidget: CaptchaWidget | null = null;
    if (CAPTCHA_ON) {
      formWidget = mountCaptcha(document.getElementById('pf-captcha-wrap')!, SITE_KEY);
    }

    const setErr = (field: string, msg: string | null) => {
      const f = body.querySelector<HTMLElement>(`[data-field="${field}"]`);
      if (!f) return;
      f.classList.toggle('has-error', !!msg);
      f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
    };

    document.getElementById('pf-cancel')!.addEventListener('click', () => {
      formWidget?.destroy();
      render(p);
    });

    const saveBtn = document.getElementById('pf-save') as HTMLButtonElement;
    document.getElementById('profile-form')!.addEventListener('submit', (e) => {
      e.preventDefault();
      void withBusy(saveBtn, async () => {
        const phone   = (document.getElementById('pf-phone') as HTMLInputElement).value.trim();
        const address = (document.getElementById('pf-address') as HTMLTextAreaElement).value.trim();
        let valid = true;

        if (!PHONE_RE.test(phone)) {
          setErr('phone', 'Enter a valid phone number (10–15 digits)'); valid = false;
        } else { setErr('phone', null); }

        if (!address) {
          setErr('address', 'Address is required'); valid = false;
        } else { setErr('address', null); }
        if (!valid) return;

        // Silent CAPTCHA check — only blocks if enabled and not completed
        if (CAPTCHA_ON && formWidget) {
          if (!formWidget.isDone()) {
            const errEl = document.getElementById('pf-captcha-err');
            if (errEl) errEl.textContent = 'Please complete the CAPTCHA';
            toast('Please complete the CAPTCHA first', 'error');
            return;
          }
          try {
            await captcha.verify(formWidget.getToken());
          } catch (err) {
            formWidget.reset();
            const errEl = document.getElementById('pf-captcha-err');
            if (errEl) errEl.textContent = 'CAPTCHA failed — please try again';
            toast(errorMessage(err), 'error');
            return;
          }
        }

        try {
          const updated = await customers.updateMe({ phone, address });
          formWidget?.destroy();
          toast('Profile updated', 'success');
          render(updated);
        } catch (err) {
          formWidget?.reset();
          toast(errorMessage(err), 'error');
        }
      });
    });
  }

  await load();
}
