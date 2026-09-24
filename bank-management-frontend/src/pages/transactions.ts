import { transactions } from '../api';
import type { Transaction } from '../api';
import { icon } from '../icons';
import {
  formatCurrency, txMeta, emptyState, errorState, skeletonRows, debounce, errorMessage,
} from '../utils';
import { txTableHtml, txCsv, sumCredits, sumDebits } from '../components/tx';

const PAGE_SIZE = 25;
const TX_TYPES = ['DEPOSIT', 'WITHDRAW', 'TRANSFER_IN', 'TRANSFER_OUT', 'OPENING_DEPOSIT', 'CLOSURE_PAYOUT'];

export interface LedgerOptions {
  title: string;
  description: string;
  load: () => Promise<Transaction[]>;
  staff: boolean;
  exportName: string;
}

/** All transactions across the bank (staff). */
export function renderTransactions(container: HTMLElement) {
  return renderLedger(container, {
    title: 'Transactions',
    description: 'Every deposit, withdrawal and transfer — newest first.',
    load: transactions.getAll,
    staff: true,
    exportName: 'transactions',
  });
}

/** Shared ledger view used by staff (all transactions) and customers (own transactions). */
export async function renderLedger(container: HTMLElement, opts: LedgerOptions) {
  container.innerHTML = `
    <div class="page">
      <div class="page-header">
        <div>
          <h1 class="page-title">${opts.title}</h1>
          <p class="page-desc">${opts.description}</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="tx-export" disabled>${icon('download', 16)} Export CSV</button>
        </div>
      </div>

      <div class="kpi-grid kpi-grid-3" id="tx-summary"></div>

      <div class="card">
        <div class="filters">
          <div class="search">
            ${icon('search', 16)}
            <input class="input" id="tx-search" placeholder="${opts.staff ? 'Account, reference, remarks or staff…' : 'Account, reference or remarks…'}" autocomplete="off" />
          </div>
          <select class="select" id="tx-type" style="width:auto">
            <option value="">All types</option>
            ${TX_TYPES.map((t) => `<option value="${t}">${txMeta(t).label}</option>`).join('')}
          </select>
          <input type="date" class="input" id="tx-from" aria-label="From date" title="From date" />
          <input type="date" class="input" id="tx-to" aria-label="To date" title="To date" />
          <button class="btn btn-ghost btn-sm hidden" id="tx-clear">${icon('x', 14)} Clear</button>
        </div>
        <div id="tx-content">${skeletonRows(8)}</div>
        <div class="card-footer hidden" id="tx-footer">
          <span id="tx-range"></span>
          <div class="pagination">
            <button class="btn btn-secondary btn-sm" id="tx-prev">${icon('chevronLeft', 14)} Prev</button>
            <button class="btn btn-secondary btn-sm" id="tx-next">Next ${icon('chevronRight', 14)}</button>
          </div>
        </div>
      </div>
    </div>`;

  let all: Transaction[] = [];
  let page = 0;
  const content = document.getElementById('tx-content')!;
  const searchEl = document.getElementById('tx-search') as HTMLInputElement;
  const typeEl = document.getElementById('tx-type') as HTMLSelectElement;
  const fromEl = document.getElementById('tx-from') as HTMLInputElement;
  const toEl = document.getElementById('tx-to') as HTMLInputElement;
  const clearBtn = document.getElementById('tx-clear')!;
  const exportBtn = document.getElementById('tx-export') as HTMLButtonElement;

  const filtered = (): Transaction[] => {
    const q = searchEl.value.trim().toLowerCase();
    return all.filter((t) => {
      if (typeEl.value && t.transactionType !== typeEl.value) return false;
      const day = t.transactionDate.slice(0, 10);
      if (fromEl.value && day < fromEl.value) return false;
      if (toEl.value && day > toEl.value) return false;
      if (!q) return true;
      return [t.accountNumber, t.referenceId, t.description, t.counterpartyAccountNumber, t.performedBy, String(t.transactionId)]
        .some((v) => (v ?? '').toLowerCase().includes(q));
    });
  };

  const render = () => {
    const list = filtered();
    const active = !!(searchEl.value || typeEl.value || fromEl.value || toEl.value);
    clearBtn.classList.toggle('hidden', !active);
    exportBtn.disabled = list.length === 0;

    const credits = sumCredits(list);
    const debits = sumDebits(list);
    document.getElementById('tx-summary')!.innerHTML = `
      <div class="card kpi"><div class="kpi-top"><span class="kpi-label">Transactions</span><span class="kpi-icon tone-slate">${icon('receipt', 18)}</span></div><div class="kpi-value">${list.length}</div></div>
      <div class="card kpi"><div class="kpi-top"><span class="kpi-label">Money in</span><span class="kpi-icon tone-green">${icon('trendUp', 18)}</span></div><div class="kpi-value text-success">${formatCurrency(credits)}</div></div>
      <div class="card kpi"><div class="kpi-top"><span class="kpi-label">Money out</span><span class="kpi-icon tone-red">${icon('trendDown', 18)}</span></div><div class="kpi-value text-danger">${formatCurrency(debits)}</div></div>`;

    const footer = document.getElementById('tx-footer')!;
    if (list.length === 0) {
      content.innerHTML = all.length
        ? emptyState({ icon: 'search', title: 'No matches', text: 'Try different filters.' })
        : emptyState({ title: 'No transactions yet', text: 'Deposits, withdrawals and transfers will appear here.' });
      footer.classList.add('hidden');
      return;
    }

    const pages = Math.ceil(list.length / PAGE_SIZE);
    page = Math.min(page, pages - 1);
    const start = page * PAGE_SIZE;
    const slice = list.slice(start, start + PAGE_SIZE);
    content.innerHTML = txTableHtml(slice, { showAccount: true, showBalance: true, showPerformedBy: opts.staff });

    footer.classList.toggle('hidden', pages <= 1);
    document.getElementById('tx-range')!.textContent = `Showing ${start + 1}–${start + slice.length} of ${list.length}`;
    (document.getElementById('tx-prev') as HTMLButtonElement).disabled = page === 0;
    (document.getElementById('tx-next') as HTMLButtonElement).disabled = page >= pages - 1;
  };

  const resetAndRender = () => { page = 0; render(); };
  searchEl.addEventListener('input', debounce(resetAndRender, 150));
  [typeEl, fromEl, toEl].forEach((el) => el.addEventListener('change', resetAndRender));
  clearBtn.addEventListener('click', () => {
    searchEl.value = ''; typeEl.value = ''; fromEl.value = ''; toEl.value = '';
    resetAndRender();
  });
  document.getElementById('tx-prev')!.addEventListener('click', () => { page--; render(); window.scrollTo({ top: 0, behavior: 'smooth' }); });
  document.getElementById('tx-next')!.addEventListener('click', () => { page++; render(); window.scrollTo({ top: 0, behavior: 'smooth' }); });
  exportBtn.addEventListener('click', () => {
    txCsv(`${opts.exportName}_${new Date().toISOString().slice(0, 10)}.csv`, filtered());
  });

  const load = async () => {
    content.innerHTML = skeletonRows(8);
    try {
      all = await opts.load();
      render();
    } catch (err) {
      content.innerHTML = errorState(errorMessage(err));
      content.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  };
  await load();
}
