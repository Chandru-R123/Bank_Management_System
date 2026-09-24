import { transactions } from '../api';
import type { Account, Transaction } from '../api';
import { icon } from '../icons';
import {
  esc, formatCurrency, formatDate, txMeta, txIcon, signedAmount,
  emptyState, errorState, skeletonRows, openModal, downloadCsv, accountTypeLabel,
  statusBadge, errorMessage,
} from '../utils';

export interface TxTableOptions {
  showAccount?: boolean;
  showBalance?: boolean;
  showPerformedBy?: boolean;
  compact?: boolean;
}

/** Renders a list of transactions as a table (newest first as given). */
export function txTableHtml(list: Transaction[], opts: TxTableOptions = {}): string {
  return `
    <div class="table-wrap">
      <table class="table${opts.compact ? ' table-compact' : ''}">
        <thead><tr>
          <th>Transaction</th>
          ${opts.showAccount ? '<th>Account</th>' : ''}
          <th>Date</th>
          <th>Reference</th>
          ${opts.showPerformedBy ? '<th>By</th>' : ''}
          <th class="num">Amount</th>
          ${opts.showBalance ? '<th class="num">Balance</th>' : ''}
        </tr></thead>
        <tbody>
          ${list.map((t) => txRowHtml(t, opts)).join('')}
        </tbody>
      </table>
    </div>`;
}

function txRowHtml(t: Transaction, opts: TxTableOptions): string {
  const meta = txMeta(t.transactionType);
  const sub = t.description || (t.counterpartyAccountNumber ? `${meta.credit ? 'From' : 'To'} ${t.counterpartyAccountNumber}` : '');
  return `
    <tr>
      <td>
        <div class="tx-cell">
          ${txIcon(t.transactionType)}
          <div class="truncate">
            <div class="cell-primary">${esc(meta.label)}</div>
            ${sub ? `<div class="cell-sub truncate" title="${esc(sub)}">${esc(sub)}</div>` : ''}
          </div>
        </div>
      </td>
      ${opts.showAccount ? `<td class="nowrap"><span class="mono">${esc(t.accountNumber)}</span></td>` : ''}
      <td class="nowrap text-muted">${formatDate(t.transactionDate)}</td>
      <td class="nowrap"><span class="mono text-sm text-muted">${esc(t.referenceId ?? `#${t.transactionId}`)}</span></td>
      ${opts.showPerformedBy ? `<td class="nowrap text-muted text-sm">${esc(t.performedBy ?? '—')}</td>` : ''}
      <td class="num">${signedAmount(t.transactionType, t.amount)}</td>
      ${opts.showBalance ? `<td class="num text-muted">${t.balanceAfter != null ? formatCurrency(t.balanceAfter) : '—'}</td>` : ''}
    </tr>`;
}

export function txCsv(filename: string, list: Transaction[]) {
  downloadCsv(
    filename,
    ['Date', 'Transaction ID', 'Reference', 'Account', 'Type', 'Description', 'Counterparty', 'Debit', 'Credit', 'Balance', 'Performed by'],
    list.map((t) => {
      const credit = txMeta(t.transactionType).credit;
      return [
        formatDate(t.transactionDate),
        t.transactionId,
        t.referenceId ?? '',
        t.accountNumber,
        txMeta(t.transactionType).label,
        t.description ?? '',
        t.counterpartyAccountNumber ?? '',
        credit ? '' : t.amount.toFixed(2),
        credit ? t.amount.toFixed(2) : '',
        t.balanceAfter != null ? t.balanceAfter.toFixed(2) : '',
        t.performedBy ?? '',
      ];
    }),
  );
}

export function sumCredits(list: Transaction[]): number {
  return list.filter((t) => txMeta(t.transactionType).credit).reduce((s, t) => s + t.amount, 0);
}

export function sumDebits(list: Transaction[]): number {
  return list.filter((t) => !txMeta(t.transactionType).credit).reduce((s, t) => s + t.amount, 0);
}

// ── Statement drawer ─────────────────────────────────────────────────────────

export function openStatement(account: Account, opts: { showPerformedBy?: boolean } = {}) {
  const m = openModal({
    drawer: true,
    autofocus: false,
    title: 'Account statement',
    subtitle: `${account.accountNumber} · ${account.customerName ?? ''}`,
    body: `
      <div class="summary-box">
        <div>
          <div class="label">${esc(accountTypeLabel(account.accountType))} account</div>
          <div class="value">${formatCurrency(account.balance)}</div>
        </div>
        ${statusBadge(account.status)}
      </div>
      <div class="flex gap-2 mt-4" style="flex-wrap:wrap">
        <input type="date" class="input" data-from style="width:auto;flex:1;min-width:140px" aria-label="From date" />
        <input type="date" class="input" data-to style="width:auto;flex:1;min-width:140px" aria-label="To date" />
        <div class="segmented" data-kind>
          <button class="active" data-v="all">All</button>
          <button data-v="credit">Credits</button>
          <button data-v="debit">Debits</button>
        </div>
      </div>
      <div class="grid grid-2 mt-3" data-totals></div>
      <div class="card mt-3" data-list>${skeletonRows(6)}</div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Close</button>
      <button class="btn btn-primary" data-csv disabled>${icon('download', 16)} Download CSV</button>`,
  });

  let all: Transaction[] = [];
  let kind: 'all' | 'credit' | 'debit' = 'all';
  const fromEl = m.el.querySelector<HTMLInputElement>('[data-from]')!;
  const toEl = m.el.querySelector<HTMLInputElement>('[data-to]')!;
  const listEl = m.el.querySelector<HTMLElement>('[data-list]')!;
  const totalsEl = m.el.querySelector<HTMLElement>('[data-totals]')!;
  const csvBtn = m.el.querySelector<HTMLButtonElement>('[data-csv]')!;

  const filtered = () => all.filter((t) => {
    const day = t.transactionDate.slice(0, 10);
    if (fromEl.value && day < fromEl.value) return false;
    if (toEl.value && day > toEl.value) return false;
    if (kind !== 'all' && txMeta(t.transactionType).credit !== (kind === 'credit')) return false;
    return true;
  });

  const render = () => {
    const list = filtered();
    totalsEl.innerHTML = `
      <div class="summary-box"><div><div class="label">Money in</div><div class="value text-success">${formatCurrency(sumCredits(list))}</div></div></div>
      <div class="summary-box"><div><div class="label">Money out</div><div class="value text-danger">${formatCurrency(sumDebits(list))}</div></div></div>`;
    listEl.innerHTML = list.length
      ? txTableHtml(list, { showBalance: true, showPerformedBy: opts.showPerformedBy, compact: true })
      : emptyState({ title: 'No transactions', text: all.length ? 'Nothing matches these filters.' : 'This account has no activity yet.' });
    csvBtn.disabled = list.length === 0;
  };

  const load = async () => {
    listEl.innerHTML = skeletonRows(6);
    try {
      all = await transactions.getByAccount(account.accountId);
      render();
    } catch (err) {
      listEl.innerHTML = errorState(errorMessage(err));
      listEl.querySelector('[data-retry]')?.addEventListener('click', load);
    }
  };

  fromEl.addEventListener('change', render);
  toEl.addEventListener('change', render);
  m.el.querySelectorAll<HTMLButtonElement>('[data-kind] button').forEach((b) => {
    b.addEventListener('click', () => {
      kind = b.dataset.v as typeof kind;
      m.el.querySelectorAll('[data-kind] button').forEach((x) => x.classList.toggle('active', x === b));
      render();
    });
  });
  csvBtn.addEventListener('click', () => {
    const suffix = fromEl.value || toEl.value
      ? `_${fromEl.value || 'start'}_to_${toEl.value || new Date().toISOString().slice(0, 10)}`
      : '';
    txCsv(`statement_${account.accountNumber}${suffix}.csv`, filtered());
  });

  void load();
}
