import { customers, accounts, transactions, getDisplayName } from '../api';
import type { Account, Customer, Transaction } from '../api';
import { icon } from '../icons';
import {
  esc, formatCurrency, formatCompact, greeting, initials, txMeta, emptyState,
  errorState, skeletonRows, accountTypeLabel, errorMessage,
} from '../utils';
import { txTableHtml } from '../components/tx';
import { openCustomerForm } from './customers';
import { openAccountForm } from './accounts';

export async function renderDashboard(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">${esc(greeting())}, ${esc(getDisplayName().split(' ')[0])}</h1>
          <p class="page-desc">Here's what's happening at your branch today.</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="dash-new-customer">${icon('userPlus', 16)} New customer</button>
          <button class="btn btn-primary" id="dash-new-account">${icon('plus', 16)} Open account</button>
        </div>
      </div>

      <div class="kpi-grid" id="kpis">${kpiSkeleton()}</div>

      <div class="grid grid-3-1 section">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">Cash flow</div>
              <div class="card-sub">Money in vs money out · last 14 days</div>
            </div>
            <div class="chart-legend">
              <span><span class="legend-dot" style="background:var(--success)"></span>In</span>
              <span><span class="legend-dot" style="background:#f87171"></span>Out</span>
            </div>
          </div>
          <div class="card-body" id="flow-chart"><span class="skeleton" style="height:220px"></span></div>
        </div>
        <div class="card">
          <div class="card-header"><div><div class="card-title">Portfolio mix</div><div class="card-sub">Deposits by account type</div></div></div>
          <div class="card-body" id="mix"><span class="skeleton" style="height:160px"></span></div>
        </div>
      </div>

      <div class="grid grid-3-1 section">
        <div class="card">
          <div class="card-header">
            <div><div class="card-title">Recent activity</div><div class="card-sub">Latest transactions across all accounts</div></div>
            <a href="#transactions" class="btn btn-ghost btn-sm">View all ${icon('chevronRight', 14)}</a>
          </div>
          <div id="recent-tx">${skeletonRows(6)}</div>
        </div>
        <div class="card">
          <div class="card-header">
            <div><div class="card-title">Newest customers</div><div class="card-sub">Recently onboarded</div></div>
            <a href="#customers" class="btn btn-ghost btn-sm">All ${icon('chevronRight', 14)}</a>
          </div>
          <div id="new-customers">${skeletonRows(4)}</div>
        </div>
      </div>
    </div>`;

  document.getElementById('dash-new-customer')!.addEventListener('click', () => openCustomerForm(null, load));
  document.getElementById('dash-new-account')!.addEventListener('click', () => openAccountForm(null, load));

  // Pull any new Keycloak self-registrations into the DB; refresh if something changed.
  customers.adminSync()
    .then((r) => { if (r?.synced > 0 && document.getElementById('kpis')) void load(); })
    .catch(() => null);

  await load();

  async function load() {
    const [custR, acctR, txR] = await Promise.allSettled([
      customers.getAll(),
      accounts.getAll(),
      transactions.getAll(),
    ]);
    if (!document.getElementById('kpis')) return; // navigated away

    const custList = custR.status === 'fulfilled' ? custR.value : null;
    const acctList = acctR.status === 'fulfilled' ? acctR.value : null;
    const txList = txR.status === 'fulfilled' ? txR.value : null;

    renderKpis(custList, acctList, txList);
    renderFlow(txList, txR.status === 'rejected' ? errorMessage(txR.reason) : null);
    renderMix(acctList);
    renderRecent(txList, txR.status === 'rejected' ? errorMessage(txR.reason) : null);
    renderNewCustomers(custList, custR.status === 'rejected' ? errorMessage(custR.reason) : null);

    container.querySelectorAll('[data-retry]').forEach((b) => b.addEventListener('click', () => void load()));
  }
}

function kpiSkeleton(): string {
  return Array.from({ length: 4 }, () => `
    <div class="card kpi">
      <span class="skeleton" style="width:50%;height:12px"></span>
      <span class="skeleton" style="width:70%;height:24px"></span>
      <span class="skeleton" style="width:40%;height:10px"></span>
    </div>`).join('');
}

function kpi(label: string, value: string, meta: string, iconHtml: string, tone: string): string {
  return `
    <div class="card kpi">
      <div class="kpi-top">
        <span class="kpi-label">${esc(label)}</span>
        <span class="kpi-icon ${tone}">${iconHtml}</span>
      </div>
      <div class="kpi-value">${value}</div>
      <div class="kpi-meta">${meta}</div>
    </div>`;
}

function localDayKey(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function renderKpis(custList: Customer[] | null, acctList: Account[] | null, txList: Transaction[] | null) {
  const el = document.getElementById('kpis')!;
  const na = '<span class="text-danger">Unavailable</span>';

  const open = acctList?.filter((a) => a.status !== 'CLOSED') ?? [];
  const deposits = open.reduce((s, a) => s + a.balance, 0);
  const frozen = acctList?.filter((a) => a.status === 'FROZEN').length ?? 0;
  const closed = acctList?.filter((a) => a.status === 'CLOSED').length ?? 0;

  const today = localDayKey(new Date());
  const todayTx = txList?.filter((t) => t.transactionDate.slice(0, 10) === today) ?? [];
  const volume = todayTx.reduce((s, t) => s + t.amount, 0);

  const online = custList?.filter((c) => c.onlineBanking).length ?? 0;

  el.innerHTML = [
    kpi('Total deposits', acctList ? formatCompact(deposits) : na,
      acctList ? `${open.length} open account${open.length === 1 ? '' : 's'}` : '',
      icon('wallet', 18), 'tone-blue'),
    kpi('Customers', custList ? String(custList.length) : na,
      custList ? `${online} using online banking` : '',
      icon('users', 18), 'tone-violet'),
    kpi("Today's transactions", txList ? String(todayTx.length) : na,
      txList ? `${formatCompact(volume)} moved today` : '',
      icon('activity', 18), 'tone-green'),
    kpi('Needs attention', acctList ? String(frozen) : na,
      acctList ? `frozen · ${closed} closed` : '',
      icon('snowflake', 18), frozen > 0 ? 'tone-amber' : 'tone-slate'),
  ].join('');
}

function renderFlow(txList: Transaction[] | null, error: string | null) {
  const el = document.getElementById('flow-chart')!;
  if (!txList) {
    el.innerHTML = errorState(error ?? 'Failed to load');
    return;
  }

  const days: { key: string; label: string; inflow: number; outflow: number }[] = [];
  for (let i = 13; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push({
      key: localDayKey(d),
      label: d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }),
      inflow: 0,
      outflow: 0,
    });
  }
  const byKey = new Map(days.map((d) => [d.key, d]));
  for (const t of txList) {
    const d = byKey.get(t.transactionDate.slice(0, 10));
    if (!d) continue;
    if (txMeta(t.transactionType).credit) d.inflow += t.amount;
    else d.outflow += t.amount;
  }

  const max = Math.max(1, ...days.map((d) => Math.max(d.inflow, d.outflow)));
  if (days.every((d) => d.inflow === 0 && d.outflow === 0)) {
    el.innerHTML = emptyState({ icon: 'activity', title: 'No activity in the last 14 days', text: 'Deposits, withdrawals and transfers will appear here.' });
    return;
  }

  const W = 720, H = 220, padL = 56, padB = 26, padT = 8;
  const plotW = W - padL, plotH = H - padB - padT;
  const group = plotW / days.length;
  const barW = Math.min(14, group * 0.32);
  const y = (v: number) => padT + plotH - (v / max) * plotH;

  const grid = [0, 0.25, 0.5, 0.75, 1].map((f) => {
    const gy = padT + plotH - f * plotH;
    return `<line x1="${padL}" x2="${W}" y1="${gy}" y2="${gy}" stroke="#e8ecf2" stroke-dasharray="${f === 0 ? '' : '3 4'}"/>
            <text x="${padL - 8}" y="${gy + 4}" text-anchor="end" font-size="10.5" fill="#94a3b8">${esc(formatCompact(max * f).replace('.00', ''))}</text>`;
  }).join('');

  const bars = days.map((d, i) => {
    const cx = padL + group * i + group / 2;
    const inH = padT + plotH - y(d.inflow);
    const outH = padT + plotH - y(d.outflow);
    return `
      <g>
        <title>${esc(d.label)} — In ${esc(formatCurrency(d.inflow))} · Out ${esc(formatCurrency(d.outflow))}</title>
        <rect x="${cx - barW - 1}" y="${y(d.inflow)}" width="${barW}" height="${Math.max(inH, d.inflow ? 2 : 0)}" rx="3" fill="#10b981"/>
        <rect x="${cx + 1}" y="${y(d.outflow)}" width="${barW}" height="${Math.max(outH, d.outflow ? 2 : 0)}" rx="3" fill="#f87171"/>
        ${i % 2 === 1 || days.length <= 7 ? `<text x="${cx}" y="${H - 6}" text-anchor="middle" font-size="10.5" fill="#94a3b8">${esc(d.label)}</text>` : ''}
      </g>`;
  }).join('');

  const totalIn = days.reduce((s, d) => s + d.inflow, 0);
  const totalOut = days.reduce((s, d) => s + d.outflow, 0);
  el.innerHTML = `
    <div class="flex gap-3" style="margin-bottom:12px;flex-wrap:wrap">
      <div><div class="text-sm text-muted">Money in</div><div class="fw-600 text-success num">${formatCurrency(totalIn)}</div></div>
      <div><div class="text-sm text-muted">Money out</div><div class="fw-600 text-danger num">${formatCurrency(totalOut)}</div></div>
      <div><div class="text-sm text-muted">Net</div><div class="fw-600 num">${formatCurrency(totalIn - totalOut)}</div></div>
    </div>
    <svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Cash flow chart for the last 14 days">${grid}${bars}</svg>`;
}

function renderMix(acctList: Account[] | null) {
  const el = document.getElementById('mix')!;
  if (!acctList) {
    el.innerHTML = errorState('Failed to load accounts');
    return;
  }
  const open = acctList.filter((a) => a.status !== 'CLOSED');
  if (open.length === 0) {
    el.innerHTML = emptyState({ icon: 'bank', title: 'No open accounts yet' });
    return;
  }
  const total = open.reduce((s, a) => s + a.balance, 0) || 1;
  const colors: Record<string, string> = { SAVINGS: '#2563eb', CURRENT: '#7c3aed', FIXED_DEPOSIT: '#d97706' };
  const rows = ['SAVINGS', 'CURRENT', 'FIXED_DEPOSIT'].map((type) => {
    const list = open.filter((a) => a.accountType === type);
    const sum = list.reduce((s, a) => s + a.balance, 0);
    const pct = Math.round((sum / total) * 100);
    return `
      <div>
        <div class="mix-row-top">
          <span class="fw-600">${esc(accountTypeLabel(type))} <span class="text-muted" style="font-weight:400">· ${list.length}</span></span>
          <span>${formatCompact(sum)} · ${pct}%</span>
        </div>
        <div class="bar"><span style="width:${pct}%;background:${colors[type]}"></span></div>
      </div>`;
  }).join('');
  el.innerHTML = `<div class="mix-list">${rows}</div>`;
}

function renderRecent(txList: Transaction[] | null, error: string | null) {
  const el = document.getElementById('recent-tx')!;
  if (!txList) {
    el.innerHTML = errorState(error ?? 'Failed to load');
    return;
  }
  el.innerHTML = txList.length
    ? txTableHtml(txList.slice(0, 8), { showAccount: true, showPerformedBy: true, compact: true })
    : emptyState({ title: 'No transactions yet', text: 'Activity will appear here as soon as money moves.' });
}

function renderNewCustomers(custList: Customer[] | null, error: string | null) {
  const el = document.getElementById('new-customers')!;
  if (!custList) {
    el.innerHTML = errorState(error ?? 'Failed to load');
    return;
  }
  const latest = [...custList].sort((a, b) => b.customerId - a.customerId).slice(0, 6);
  el.innerHTML = latest.length
    ? `<div class="list">${latest.map((c) => `
        <div class="list-item">
          <span class="avatar">${esc(initials(c.name))}</span>
          <div class="grow">
            <div class="fw-600 truncate">${esc(c.name)}</div>
            <div class="text-sm text-muted truncate">${esc(c.email)}</div>
          </div>
          ${c.onlineBanking ? '<span class="badge badge-green">Online</span>' : '<span class="badge badge-neutral">Branch</span>'}
        </div>`).join('')}</div>`
    : emptyState({ icon: 'users', title: 'No customers yet' });
}
