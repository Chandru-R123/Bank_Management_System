import { staff, getUserId } from '../api';
import type { StaffMember, StaffRole } from '../api';
import { icon } from '../icons';
import {
  esc, toast, openModal, openMenu, confirmDialog, withBusy, initials, formatDay,
  emptyState, errorState, skeletonRows, debounce, errorMessage,
} from '../utils';
import type { MenuItem } from '../utils';

const ROLES: { value: StaffRole; label: string; detail: string }[] = [
  { value: 'EMPLOYEE', label: 'Employee',      detail: 'View everything, create and edit customers' },
  { value: 'MAKER',    label: 'Maker',         detail: 'Deposit, withdraw and transfer on any account' },
  { value: 'CHECKER',  label: 'Checker',       detail: 'Delete beneficiaries, revoke consents' },
  { value: 'ADMIN',    label: 'Administrator', detail: 'Full access, including staff and account management' },
];

const ROLE_BADGE: Record<StaffRole, string> = {
  ADMIN: 'badge-violet', EMPLOYEE: 'badge-neutral', MAKER: 'badge-blue', CHECKER: 'badge-amber',
};

const PHONE_RE = /^\+?[0-9][0-9 -]{8,14}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME_RE = /^[a-zA-Z0-9._-]{3,50}$/;

function roleBadges(roles: StaffRole[]): string {
  return roles.map((r) => `<span class="badge ${ROLE_BADGE[r] ?? 'badge-neutral'}">${esc(ROLES.find((x) => x.value === r)?.label ?? r)}</span>`).join(' ');
}

function fullName(s: StaffMember): string {
  return [s.firstName, s.lastName].filter(Boolean).join(' ') || s.username;
}

/** ADMIN only — staff logins live in Keycloak, managed through the backend. */
export async function renderStaff(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Staff</h1>
          <p class="page-desc">Create and manage logins for employees, makers, checkers and administrators.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-primary" id="staff-add">${icon('userPlus', 16)} Add staff member</button>
        </div>
      </div>
      <div class="card">
        <div class="filters">
          <div class="search">
            ${icon('search', 16)}
            <input class="input" id="staff-search" placeholder="Search name, username or email…" autocomplete="off" />
          </div>
          <select class="select" id="staff-role" style="width:auto">
            <option value="">All roles</option>
            ${ROLES.map((r) => `<option value="${r.value}">${r.label}</option>`).join('')}
          </select>
          <div class="spacer"></div>
          <span class="text-sm text-muted" id="staff-count"></span>
        </div>
        <div id="staff-content">${skeletonRows(5)}</div>
      </div>
    </div>`;

  let list: StaffMember[] = [];
  let query = '';
  let roleFilter = '';
  const content = document.getElementById('staff-content')!;
  const me = getUserId();

  document.getElementById('staff-add')!.addEventListener('click', () => openStaffForm(load));
  document.getElementById('staff-search')!.addEventListener('input', debounce((e: Event) => {
    query = (e.target as HTMLInputElement).value.trim().toLowerCase();
    render();
  }, 150));
  document.getElementById('staff-role')!.addEventListener('change', (e) => {
    roleFilter = (e.target as HTMLSelectElement).value;
    render();
  });

  await load();

  async function load() {
    if (!document.getElementById('staff-content')) return;
    content.innerHTML = skeletonRows(5);
    try {
      list = await staff.getAll();
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  }

  function render() {
    const rows = list.filter((s) => {
      if (roleFilter && !s.roles.includes(roleFilter as StaffRole)) return false;
      if (!query) return true;
      return [fullName(s), s.username, s.email].some((v) => (v ?? '').toLowerCase().includes(query));
    });
    document.getElementById('staff-count')!.textContent = `${rows.length} of ${list.length} staff`;

    if (rows.length === 0) {
      content.innerHTML = emptyState({ icon: 'users', title: list.length ? 'No matches' : 'No staff yet' });
      return;
    }

    content.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Staff member</th><th>Username</th><th>Roles</th><th>Status</th><th>Since</th>
            <th class="actions">Actions</th>
          </tr></thead>
          <tbody>
            ${rows.map((s) => `
              <tr>
                <td>
                  <div class="cell-person">
                    <span class="avatar">${esc(initials(fullName(s)))}</span>
                    <div class="truncate">
                      <div class="cell-primary truncate">${esc(fullName(s))}${s.id === me ? ' <span class="text-muted text-sm">(you)</span>' : ''}</div>
                      <div class="cell-sub truncate">${esc(s.email ?? 'No email')}</div>
                    </div>
                  </div>
                </td>
                <td class="nowrap"><span class="mono">${esc(s.username)}</span></td>
                <td>${roleBadges(s.roles)}</td>
                <td>${s.enabled
                  ? '<span class="badge badge-dot badge-green">Active</span>'
                  : '<span class="badge badge-dot badge-neutral">Disabled</span>'}</td>
                <td class="nowrap text-muted">${s.createdTimestamp ? esc(formatDay(new Date(s.createdTimestamp).toISOString())) : '—'}</td>
                <td class="actions">
                  <button class="btn-icon" data-roles="${esc(s.id)}" title="Change roles">${icon('shield', 16)}</button>
                  <button class="btn-icon" data-more="${esc(s.id)}" title="More">${icon('menu', 16)}</button>
                </td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    const byId = (id: string | undefined) => list.find((s) => s.id === id)!;
    content.querySelectorAll<HTMLButtonElement>('[data-roles]').forEach((b) =>
      b.addEventListener('click', () => openRolesForm(byId(b.dataset.roles), load)));
    content.querySelectorAll<HTMLButtonElement>('[data-more]').forEach((b) =>
      b.addEventListener('click', () => openMenu(b, staffMenu(byId(b.dataset.more)))));
  }

  function staffMenu(s: StaffMember): (MenuItem | 'divider')[] {
    const self = s.id === me;
    return [
      { label: 'Change roles', icon: 'shield', onClick: () => openRolesForm(s, load) },
      { label: 'Email password reset link', icon: 'mail', disabled: !s.email,
        hint: s.email ? undefined : 'No email address', onClick: () => void sendReset(s) },
      { label: 'Set temporary password', icon: 'key', onClick: () => openPasswordForm(s) },
      'divider',
      s.enabled
        ? { label: 'Disable login', icon: 'lock', danger: true, disabled: self,
            hint: self ? 'You cannot disable yourself' : undefined, onClick: () => void toggle(s) }
        : { label: 'Enable login', icon: 'unlock', onClick: () => void toggle(s) },
    ];
  }

  async function sendReset(s: StaffMember) {
    try {
      const r = await staff.passwordEmail(s.id);
      toast(r.message, 'success');
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }

  async function toggle(s: StaffMember) {
    if (s.enabled) {
      const ok = await confirmDialog({
        title: `Disable ${fullName(s)}?`,
        message: 'They will not be able to sign in until the login is enabled again.',
        confirmText: 'Disable login',
        tone: 'danger',
      });
      if (!ok) return;
    }
    try {
      await (s.enabled ? staff.disable(s.id) : staff.enable(s.id));
      toast(`${fullName(s)} ${s.enabled ? 'disabled' : 'enabled'}`, 'success');
      await load();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }
}

// ── Forms ────────────────────────────────────────────────────────────────────

function roleCheckboxes(selected: StaffRole[], lockAdmin = false): string {
  return `
    <div class="list card" data-roles>
      ${ROLES.map((r) => `
        <label class="list-item" style="cursor:pointer">
          <input type="checkbox" value="${r.value}" ${selected.includes(r.value) ? 'checked' : ''}
                 ${lockAdmin && r.value === 'ADMIN' ? 'disabled' : ''} style="width:18px;height:18px" />
          <div class="grow"><div class="fw-600">${esc(r.label)}</div><div class="text-sm text-muted">${esc(r.detail)}</div></div>
        </label>`).join('')}
    </div>`;
}

function checkedRoles(root: HTMLElement): StaffRole[] {
  return Array.from(root.querySelectorAll<HTMLInputElement>('[data-roles] input'))
    .filter((i) => i.checked)
    .map((i) => i.value as StaffRole);
}

function openStaffForm(onSave: () => void) {
  const m = openModal({
    title: 'Add staff member',
    subtitle: 'Creates a sign-in for the bank console.',
    size: 'lg',
    body: `
      <form class="form-grid" novalidate>
        <div class="form-row">
          <div class="field" data-field="firstName">
            <label class="field-label" for="sf-first">First name<span class="req">*</span></label>
            <input id="sf-first" class="input" maxlength="60" autocomplete="off" />
            <div class="field-error" data-error></div>
          </div>
          <div class="field" data-field="lastName">
            <label class="field-label" for="sf-last">Last name<span class="req">*</span></label>
            <input id="sf-last" class="input" maxlength="60" autocomplete="off" />
            <div class="field-error" data-error></div>
          </div>
        </div>
        <div class="form-row">
          <div class="field" data-field="username">
            <label class="field-label" for="sf-username">Username<span class="req">*</span></label>
            <input id="sf-username" class="input mono" maxlength="50" placeholder="e.g. kavya.r" autocomplete="off" />
            <div class="field-error" data-error></div>
          </div>
          <div class="field" data-field="email">
            <label class="field-label" for="sf-email">Work email<span class="req">*</span></label>
            <input id="sf-email" class="input" type="email" maxlength="150" autocomplete="off" />
            <div class="field-error" data-error></div>
          </div>
        </div>
        <div class="form-row">
          <div class="field" data-field="phone">
            <label class="field-label" for="sf-phone">Mobile number<span class="req">*</span></label>
            <input id="sf-phone" class="input" inputmode="tel" maxlength="16" autocomplete="off" />
            <div class="field-error" data-error></div>
          </div>
          <div class="field">
            <label class="field-label" for="sf-branch">Branch</label>
            <input id="sf-branch" class="input" maxlength="255" value="State Bank, Coimbatore Branch" />
          </div>
        </div>

        <div class="field" data-field="roles">
          <span class="field-label">Roles<span class="req">*</span></span>
          ${roleCheckboxes(['EMPLOYEE'])}
          <div class="field-error" data-error></div>
        </div>

        <div class="field">
          <span class="field-label">Password setup</span>
          <div class="segmented" data-pw-mode>
            <button type="button" class="active" data-v="email">Email a set-password link</button>
            <button type="button" data-v="temp">Set a temporary password</button>
          </div>
          <div class="field-hint" data-pw-hint>They receive an email (valid 24 hours) to choose their own password.</div>
        </div>
        <div class="field hidden" data-field="password">
          <label class="field-label" for="sf-password">Temporary password<span class="req">*</span></label>
          <input id="sf-password" class="input" type="text" maxlength="64" autocomplete="new-password" placeholder="At least 8 characters" />
          <div class="field-error" data-error></div>
        </div>
      </form>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>Create staff login</button>`,
  });

  const root = m.el;
  const val = (id: string) => root.querySelector<HTMLInputElement>(`#${id}`)!.value.trim();
  const setErr = (field: string, msg: string | null) => {
    const f = root.querySelector<HTMLElement>(`[data-field="${field}"]`);
    if (!f) return;
    f.classList.toggle('has-error', !!msg);
    f.querySelector<HTMLElement>('[data-error]')!.textContent = msg ?? '';
  };
  root.querySelectorAll('input').forEach((el) => el.addEventListener('input', () => {
    const field = (el.closest('[data-field]') as HTMLElement | null)?.dataset.field;
    if (field) setErr(field, null);
  }));

  // Suggest a username from the name
  const suggest = () => {
    const u = root.querySelector<HTMLInputElement>('#sf-username')!;
    if (u.dataset.touched) return;
    const first = val('sf-first').toLowerCase().replace(/[^a-z0-9]/g, '');
    const last = val('sf-last').toLowerCase().replace(/[^a-z0-9]/g, '');
    u.value = first && last ? `${first}.${last[0]}` : first;
  };
  root.querySelector('#sf-first')!.addEventListener('input', suggest);
  root.querySelector('#sf-last')!.addEventListener('input', suggest);
  root.querySelector('#sf-username')!.addEventListener('input', (e) => {
    (e.target as HTMLInputElement).dataset.touched = '1';
  });

  let mode: 'email' | 'temp' = 'email';
  root.querySelectorAll<HTMLButtonElement>('[data-pw-mode] button').forEach((b) => b.addEventListener('click', () => {
    mode = b.dataset.v as typeof mode;
    root.querySelectorAll('[data-pw-mode] button').forEach((x) => x.classList.toggle('active', x === b));
    root.querySelector('[data-field="password"]')!.classList.toggle('hidden', mode !== 'temp');
    root.querySelector('[data-pw-hint]')!.textContent = mode === 'email'
      ? 'They receive an email (valid 24 hours) to choose their own password.'
      : 'Share it securely — they must change it the first time they sign in.';
  }));

  const save = root.querySelector<HTMLButtonElement>('[data-save]')!;
  save.addEventListener('click', () => withBusy(save, async () => {
    const roles = checkedRoles(root);
    const data = {
      firstName: val('sf-first'),
      lastName: val('sf-last'),
      username: val('sf-username'),
      email: val('sf-email'),
      phone: val('sf-phone'),
      address: val('sf-branch') || undefined,
      roles,
      password: mode === 'temp' ? val('sf-password') : undefined,
    };
    let ok = true;
    if (!data.firstName) { setErr('firstName', 'First name is required'); ok = false; }
    if (!data.lastName) { setErr('lastName', 'Last name is required'); ok = false; }
    if (!USERNAME_RE.test(data.username)) { setErr('username', '3–50 letters, digits, ".", "_" or "-"'); ok = false; }
    if (!EMAIL_RE.test(data.email)) { setErr('email', 'Enter a valid email'); ok = false; }
    if (!PHONE_RE.test(data.phone)) { setErr('phone', 'Enter a valid phone number (10–15 digits)'); ok = false; }
    if (roles.length === 0) { setErr('roles', 'Choose at least one role'); ok = false; }
    if (mode === 'temp' && (data.password ?? '').length < 8) { setErr('password', 'At least 8 characters'); ok = false; }
    if (!ok) return;

    try {
      const r = await staff.create(data);
      toast(r.message, r.emailSent || mode === 'temp' ? 'success' : 'info');
      m.close();
      onSave();
    } catch (err) {
      const msg = errorMessage(err);
      if (/username/i.test(msg)) setErr('username', msg);
      else if (/email/i.test(msg) && !/could not be sent/i.test(msg)) setErr('email', msg);
      else toast(msg, 'error');
    }
  }));
}

function openRolesForm(s: StaffMember, onSave: () => void) {
  const self = s.id === getUserId();
  const m = openModal({
    title: `Roles for ${fullName(s)}`,
    subtitle: s.username,
    autofocus: false,
    body: `
      ${roleCheckboxes(s.roles, self)}
      ${self ? `<p class="text-sm text-muted mt-2">You cannot remove your own Administrator role.</p>` : ''}
      <div class="field-error mt-2" data-err style="display:none">Choose at least one role.</div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>Save roles</button>`,
  });
  const save = m.el.querySelector<HTMLButtonElement>('[data-save]')!;
  save.addEventListener('click', () => withBusy(save, async () => {
    const roles = checkedRoles(m.el);
    if (roles.length === 0) {
      m.el.querySelector<HTMLElement>('[data-err]')!.style.display = 'block';
      return;
    }
    try {
      await staff.updateRoles(s.id, roles);
      toast(`Roles updated for ${fullName(s)}`, 'success');
      m.close();
      onSave();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}

function openPasswordForm(s: StaffMember) {
  const m = openModal({
    title: 'Set temporary password',
    subtitle: `${fullName(s)} must change it at next sign-in.`,
    size: 'sm',
    body: `
      <div class="field" data-field="password">
        <label class="field-label" for="tp-password">Temporary password</label>
        <input id="tp-password" class="input" type="text" maxlength="64" autocomplete="new-password" placeholder="At least 8 characters" />
        <div class="field-error" data-error></div>
      </div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-save>Set password</button>`,
  });
  const input = m.el.querySelector<HTMLInputElement>('#tp-password')!;
  const field = m.el.querySelector<HTMLElement>('[data-field="password"]')!;
  input.addEventListener('input', () => field.classList.remove('has-error'));
  const save = m.el.querySelector<HTMLButtonElement>('[data-save]')!;
  save.addEventListener('click', () => withBusy(save, async () => {
    if (input.value.length < 8) {
      field.classList.add('has-error');
      field.querySelector<HTMLElement>('[data-error]')!.textContent = 'At least 8 characters';
      return;
    }
    try {
      const r = await staff.setPassword(s.id, input.value);
      toast(r.message, 'success');
      m.close();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  }));
}
