import { transactions } from '../api';
import { formatCurrency, formatDate } from '../utils';

export async function renderTransactions(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="toolbar">
        <h2>All Transactions</h2>
      </div>
      <div class="card">
        <div id="tx-content"><div class="loading">Loading...</div></div>
      </div>
    </div>
  `;

  const content = document.getElementById('tx-content')!;
  try {
    const list = await transactions.getAll();
    if (list.length === 0) {
      content.innerHTML = '<div class="empty">No transactions found.</div>';
      return;
    }
    content.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead><tr>
            <th>ID</th><th>Account No.</th><th>Type</th><th>Amount</th><th>Date</th>
          </tr></thead>
          <tbody>
            ${list.map(t => `
              <tr>
                <td>${t.transactionId}</td>
                <td><code>${t.accountNumber}</code></td>
                <td><span class="badge ${badgeClass(t.transactionType)}">${t.transactionType}</span></td>
                <td class="${t.transactionType === 'DEBIT' || t.transactionType === 'WITHDRAWAL' ? 'text-danger' : 'text-success'}">${formatCurrency(t.amount)}</td>
                <td class="text-muted">${formatDate(t.transactionDate)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  } catch (err: unknown) {
    content.innerHTML = `<div class="empty text-danger">${(err as Error).message}</div>`;
  }
}

function badgeClass(type: string): string {
  if (type.includes('CREDIT') || type.includes('DEPOSIT')) return 'badge-green';
  if (type.includes('DEBIT') || type.includes('WITHDRAW')) return 'badge-red';
  if (type.includes('TRANSFER')) return 'badge-yellow';
  return 'badge-blue';
}
