import { transactions } from '../api';
import { renderLedger } from './transactions';

/** A customer's own transactions across all of their accounts. */
export function renderMyTransactions(container: HTMLElement) {
  return renderLedger(container, {
    title: 'Transactions',
    description: 'All activity across your accounts — search, filter by date and download statements.',
    load: transactions.getMy,
    staff: false,
    exportName: 'my_transactions',
  });
}
