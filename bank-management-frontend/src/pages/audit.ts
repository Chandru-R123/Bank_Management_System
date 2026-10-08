/**
 * Audit Log page — Staff (ADMIN / EMPLOYEE / CHECKER) view.
 *
 * Shows a paginated, filterable audit trail of every significant action.
 * Entries are append-only and never contain passwords, tokens or secrets.
 */
import { audit } from '../api';
import type { AuditLog } from '../api';
import { icon } from '../icons';
import {
  esc, formatDate, emptyState, errorState, skeletonRows,
  errorMessage, debounce, downloadCsv, openModal,
} from '../utils';

// Colour mapping for common action families
function actionBadge(action: string): string {
  const a = action.toUpperCase();
  const danger = ['REJECTED', 'FAILED', 'DELETED', 'DISABLED', 'CLOSED', 'FROZEN', 'CANCELLED'];
  const success = ['APPROVED', 'VERIFIED', 'CREATED', 'EXECUTED', 'AUTHORISED', 'ENABLED', 'UNFROZEN'];
  const warn = ['PENDING', 'UPLOADED', 'SUBMITTED', 'STARTED'];
  const isDanger  = danger.some((w) => a.includes(w));
  const isSuccess = success.some((w) => a.includes(w));
  const isWarn    = !isDanger && !isSuccess && warn.some((w) => a.includes(w));
  const cls = isDanger ? 'badge-red' : isSuccess ? 'badge-green' : isWarn ? 'badge-amber' : 'badge-neutral';
  const label = action.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  return `<span class="badge ${cls}" style="font-size:11px">${esc(label)}</span>`;
}

function roleBadge(role: string | null): string {
  if (!role) return '—';
  const cls: Record<string, string> = {
    ADMIN: 'badge-violet', CHECKER: 'badge-blue', MAKER: 'badge-sky',
    EMPLOYEE: 'badge-neutral', CUSTOMER: 'badge-green', TPP: 'badge-amber', SYSTEM: 'badge-neutral',
  };
  return `<span class="badge ${cls[role] ?? 'badge-neutral'}">${esc(role)}</span>`;
}

// ── Page groups for filtering ─────────────────────────────────────────────────
const ACTION_GROUPS: Record<string, string[]> = {
  Account:     ['ACCOUNT_CREATED', 'ACCOUNT_UPDATED', 'ACCOUNT_FROZEN', 'ACCOUNT_UNFROZEN', 'ACCOUNT_CLOSED'],
  Transaction: ['DEPOSIT_EXECUTED', 'WITHDRAW_EXECUTED', 'TRANSFER_EXECUTED'],
  'M/C Request': ['TXN_REQUEST_CREATED', 'TXN_REQUEST_APPROVED', 'TXN_REQUEST_REJECTED',
                  'TXN_REQUEST_CANCELLED', 'TXN_REQUEST_EXECUTED', 'TXN_REQUEST_FAILED'],
  KYC:         ['KYC_STARTED', 'KYC_DOCUMENT_UPLOADED', 'KYC_SUBMITTED',
                'KYC_APPROVED', 'KYC_REJECTED', 'KYC_RESUBMITTED'],
  Customer:    ['CUSTOMER_CREATED', 'CUSTOMER_UPDATED', 'CUSTOMER_DELETED',
                'CUSTOMER_ONLINE_BANKING_ENABLED', 'CUSTOMER_PROFILE_UPDATED'],
  Consent:     ['CONSENT_CREATED', 'CONSENT_APPROVED', 'CONSENT_REJECTED', 'CONSENT_REVOKED', 'CONSENT_EXPIRED'],
  Security:    ['STAFF_CREATED', 'STAFF_ROLES_UPDATED', 'STAFF_ENABLED', 'STAFF_DISABLED', 'PASSWORD_RESET_SENT'],
};

export async function renderAuditLog(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">Audit Log</h1>
          <p class="page-desc">Every significant action recorded — append-only, no secrets stored.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="audit-export" disabled>${icon('download', 16)} Export</button>
          <button class="btn btn-secondary" id="audit-refresh">${icon('refresh', 16)} Refresh</button>
        </div>
      </div>

      <div class="card">
        <div class="filters" style="flex-wrap:wrap;gap:8px">
          <div class="search" style="min-width:220px">
            ${icon('search', 16)}
            <input class="input" id="audit-search" placeholder="Search actor, action, resource…" autocomplete="off" />
          </div>
          <select class="select" id="audit-group" style="width:auto">
            <option value="">All categories</option>
            ${Object.keys(ACTION_GROUPS).map((g) => `<option value="${g}">${esc(g)}</option>`).join('')}
          </select>
          <select class="select" id="audit-role" style="width:auto">
            <option value="">All roles</option>
            ${['ADMIN','CHECKER','MAKER','EMPLOYEE','CUSTOMER','TPP','SYSTEM'].map((r) =>
              `<option value="${r}">${r}</option>`).join('')}
          </select>
          <div class="spacer"></div>
          <span class="text-sm text-muted" id="audit-count"></span>
        </div>
        <div id="audit-table">${skeletonRows(8)}</div>
        <div class="card-footer" id="audit-pager" style="display:none;justify-content:center;gap:8px;padding:12px"></div>
      </div>
    </div>`;

  let list: AuditLog[] = [];
  let query = '';
  let groupFilter = '';
  let roleFilter  = '';
  let page = 0;
  const pageSize = 50;

  document.getElementById('audit-search')!.addEventListener('input', debounce((e: Event) => {
    query = (e.target as HTMLInputElement).value.trim().toLowerCase();
    page = 0; render();
  }, 150));
  document.getElementById('audit-group')!.addEventListener('change', (e) => {
    groupFilter = (e.target as HTMLSelectElement).value; page = 0; render();
  });
  document.getElementById('audit-role')!.addEventListener('change', (e) => {
    roleFilter = (e.target as HTMLSelectElement).value; page = 0; render();
  });
  document.getElementById('audit-export')!.addEventListener('click', () => exportCsv(visible()));
  document.getElementById('audit-refresh')!.addEventListener('click', loadData);

  await loadData();

  async function loadData() {
    const el = document.getElementById('audit-table');
    if (!el) return;
    el.innerHTML = skeletonRows(8);
    try {
      list = await audit.getAll(0, 500); // load up to 500 for client-side filtering
      render();
    } catch (err) {
      el.innerHTML = errorState(errorMessage(err));
      el.querySelector('[data-retry]')?.addEventListener('click', loadData);
    }
  }

  function visible(): AuditLog[] {
    const groupActions = groupFilter ? ACTION_GROUPS[groupFilter] : null;
    return list.filter((e) => {
      if (groupActions && !groupActions.includes(e.action)) return false;
      if (roleFilter && e.actorRole !== roleFilter) return false;
      if (!query) return true;
      const haystack = [e.action, e.actorUsername, e.actorRole, e.resourceType,
                        e.resourceId, e.remarks, e.status].join(' ').toLowerCase();
      return haystack.includes(query);
    });
  }

  function render() {
    const el = document.getElementById('audit-table')!;
    const countEl = document.getElementById('audit-count')!;
    const exportBtn = document.getElementById('audit-export') as HTMLButtonElement;
    const rows = visible();
    const paged = rows.slice(page * pageSize, (page + 1) * pageSize);

    countEl.textContent = `${rows.length} entries`;
    exportBtn.disabled = rows.length === 0;

    if (list.length === 0) {
      el.innerHTML = emptyState({ icon: 'activity', title: 'No audit events yet',
        text: 'Events are recorded as actions happen in the system.' });
      renderPager(0, 0);
      return;
    }
    if (rows.length === 0) {
      el.innerHTML = emptyState({ icon: 'search', title: 'No matches', text: 'Try different filters.' });
      renderPager(0, 0);
      return;
    }

    el.innerHTML = `
      <div class="table-wrap">
        <table class="table">
          <thead><tr>
            <th>Timestamp</th><th>Action</th><th>Actor</th><th>Role</th>
            <th>Resource</th><th>Status</th><th>Remarks</th>
          </tr></thead>
          <tbody>
            ${paged.map((e) => `
              <tr class="clickable" data-id="${e.id}">
                <td class="text-sm mono">${esc(formatDate(e.timestamp))}</td>
                <td>${actionBadge(e.action)}</td>
                <td class="text-sm">${esc(e.actorUsername)}</td>
                <td>${roleBadge(e.actorRole)}</td>
                <td class="text-sm">${e.resourceType
                  ? `<span class="text-muted">${esc(e.resourceType)}</span> <span class="mono">${esc(e.resourceId ?? '')}</span>`
                  : '—'}</td>
                <td>${e.status ? `<span class="badge badge-neutral text-xs">${esc(e.status)}</span>` : '—'}</td>
                <td class="text-sm text-muted" style="max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
                    title="${esc(e.remarks ?? '')}">${esc(e.remarks ?? '—')}</td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>`;

    el.querySelectorAll<HTMLTableRowElement>('tr[data-id]').forEach((tr) => {
      const entry = list.find((e) => e.id === Number(tr.dataset.id))!;
      tr.addEventListener('click', () => openEntryDetail(entry));
    });

    renderPager(rows.length, paged.length);
  }

  function renderPager(total: number, shown: number) {
    const el = document.getElementById('audit-pager')!;
    if (total <= pageSize) { el.style.display = 'none'; return; }
    const totalPages = Math.ceil(total / pageSize);
    el.style.display = 'flex';
    el.innerHTML = `
      <button class="btn btn-secondary btn-sm" id="pg-prev" ${page === 0 ? 'disabled' : ''}>${icon('chevronLeft', 14)} Prev</button>
      <span class="text-sm" style="padding:4px 8px">Page ${page + 1} of ${totalPages} · ${shown} shown</span>
      <button class="btn btn-secondary btn-sm" id="pg-next" ${page >= totalPages - 1 ? 'disabled' : ''}>Next ${icon('chevronRight', 14)}</button>`;
    el.querySelector('#pg-prev')?.addEventListener('click', () => { page--; render(); });
    el.querySelector('#pg-next')?.addEventListener('click', () => { page++; render(); });
  }
}

// ── Entry detail drawer ───────────────────────────────────────────────────────

function openEntryDetail(e: AuditLog) {
  openModal({
    drawer: true,
    autofocus: false,
    title: 'Audit entry',
    subtitle: formatDate(e.timestamp),
    body: `
      <div class="card"><div class="card-body">
        <dl class="dl">
          <dt>ID</dt><dd>${e.id}</dd>
          <dt>Timestamp</dt><dd>${formatDate(e.timestamp)}</dd>
          <dt>Action</dt><dd>${actionBadge(e.action)}</dd>
          <dt>Actor</dt><dd>${esc(e.actorUsername)} <span class="text-muted">(${esc(e.actorUserId)})</span></dd>
          <dt>Role</dt><dd>${roleBadge(e.actorRole)}</dd>
          ${e.resourceType ? `<dt>Resource type</dt><dd>${esc(e.resourceType)}</dd>` : ''}
          ${e.resourceId ? `<dt>Resource ID</dt><dd class="mono">${esc(e.resourceId)}</dd>` : ''}
          <dt>Status</dt><dd>${e.status ? esc(e.status) : '—'}</dd>
          ${e.remarks ? `<dt>Remarks</dt><dd>${esc(e.remarks)}</dd>` : ''}
          ${e.requestId ? `<dt>M/C Request ID</dt><dd class="mono">${esc(e.requestId)}</dd>` : ''}
          ${e.httpRequestId ? `<dt>HTTP Request ID</dt><dd class="mono">${esc(e.httpRequestId)}</dd>` : ''}
        </dl>
      </div></div>`,
    footer: `<button class="btn btn-secondary" data-close>Close</button>`,
  });
}

// ── CSV export ────────────────────────────────────────────────────────────────

function exportCsv(rows: AuditLog[]) {
  downloadCsv('audit-log.csv',
    ['ID', 'Timestamp', 'Action', 'Actor', 'Role', 'Resource type', 'Resource ID', 'Status', 'Remarks', 'M/C Request ID'],
    rows.map((e) => [
      e.id, formatDate(e.timestamp), e.action, e.actorUsername, e.actorRole,
      e.resourceType ?? '', e.resourceId ?? '', e.status ?? '', e.remarks ?? '', e.requestId ?? '',
    ]));
}
