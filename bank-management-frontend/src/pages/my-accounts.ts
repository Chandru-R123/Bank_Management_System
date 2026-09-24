import { accounts, transactions, customers, getDisplayName } from '../api';
import type { Account, Customer, Transaction } from '../api';
import { icon } from '../icons';
import {
  esc, formatCurrency, greeting, emptyState, errorState, skeletonRows,
  accountTypeLabel, errorMessage,
} from '../utils';
import { txTableHtml, openStatement, sumCredits, sumDebits } from '../components/tx';
import { openCashModal, openTransferModal, canCredit, canDebit, availableToDebit } from '../components/money';

export async function renderMyAccounts(container: HTMLElement) {
  container.innerHTML = `
    <div class="page">
      <div class="hero" id="hero">
        <div style="position:relative;z-index:1">
          <div class="hero-eyebrow">${esc(greeting())}, <span id="hero-name">${esc(getDisplayName())}</span></div>
          <div class="hero-label mt-3">Total balance</div>
          <div class="hero-amount" id="hero-total"><span class="skeleton" style="width:220px;height:34px;background:rgba(255,255,255,.12)"></span></div>
          <div class="text-sm" style="color:#b8c7e0" id="hero-sub">&nbsp;</div>
        </div>
        <div class="hero-actions">
          <button class="btn btn-light" data-q="transfer" disabled>${icon('transfer', 16)} Transfer</button>
          <button class="btn btn-glass" data-q="deposit" disabled>${icon('arrowIn', 16)} Deposit</button>
          <button class="btn btn-glass" data-q="withdraw" disabled>${icon('arrowOut', 16)} Withdraw</button>
        </div>
      </div>

      <div id="profile-callout"></div>

      <div class="kpi-grid kpi-grid-3" id="month-stats"></div>

      <div class="section-head section">
        <div>
          <div class="section-title">Your accounts</div>
          <div class="section-sub">Tap an account for its statement</div>
        </div>
      </div>
      <div id="account-cards"><div class="account-grid">${cardSkeleton().repeat(2)}</div></div>

      <div class="section-head section">
        <div>
          <div class="section-title">Recent activity</div>
          <div class="section-sub">Your latest transactions across all accounts</div>
        </div>
        <a href="#my-transactions" class="btn btn-ghost btn-sm">View all ${icon('chevronRight', 14)}</a>
      </div>
      <div class="card" id="recent-tx">${skeletonRows(5)}</div>
    </div>`;

  let acctList: Account[] = [];

  container.querySelectorAll<HTMLButtonElement>('[data-q]').forEach((b) => {
    b.addEventListener('click', () => {
      switch (b.dataset.q) {
        case 'transfer': openTransferModal(acctList, null, loadAll, { ownAccounts: acctList }); break;
        case 'deposit':  openCashModal('deposit', acctList, null, loadAll); break;
        case 'withdraw': openCashModal('withdraw', acctList, null, loadAll); break;
      }
    });
  });

  await loadAll();

  async function loadAll() {
    if (!document.getElementById('hero')) return;
    const [profileR, acctR, txR] = await Promise.allSettled([
      customers.getMe(),
      accounts.getMy(),
      transactions.getMy(),
    ]);
    if (!document.getElementById('hero')) return;

    const profile = profileR.status === 'fulfilled' ? profileR.value : null;
    if (profile) document.getElementById('hero-name')!.textContent = profile.name.split(' ')[0];
    renderProfileCallout(profile);

    const cards = document.getElementById('account-cards')!;
    if (acctR.status === 'rejected') {
      cards.innerHTML = `<div class="card">${errorState(errorMessage(acctR.reason))}</div>`;
      cards.querySelector('[data-retry]')?.addEventListener('click', loadAll);
      document.getElementById('hero-total')!.textContent = '—';
      return;
    }

    acctList = acctR.value;
    const txList = txR.status === 'fulfilled' ? txR.value : [];
    const open = acctList.filter((a) => a.status !== 'CLOSED');
    const total = open.reduce((s, a) => s + a.balance, 0);

    document.getElementById('hero-total')!.textContent = formatCurrency(total);
    document.getElementById('hero-sub')!.textContent =
      `Across ${open.length} account${open.length === 1 ? '' : 's'}`;
    container.querySelectorAll<HTMLButtonElement>('[data-q]').forEach((b) => {
      b.disabled = b.dataset.q === 'deposit' ? !acctList.some(canCredit) : !acctList.some(canDebit);
    });

    renderMonthStats(txList, open);
    renderCards(profile);
    renderRecent(txR.status === 'fulfilled' ? txList : null, txR.status === 'rejected' ? errorMessage(txR.reason) : '');
  }

  function renderProfileCallout(profile: Customer | null) {
    const el = document.getElementById('profile-callout')!;
    if (profile && (!profile.phone || !profile.address)) {
      el.innerHTML = `
        <div class="callout callout-warn" style="margin-bottom:16px">
          ${icon('info', 16)}
          <div style="flex:1">Your profile is incomplete. Add your mobile number and address so the bank can reach you.</div>
          <a href="#profile" class="btn btn-secondary btn-sm">Complete profile</a>
        </div>`;
    } else {
      el.innerHTML = '';
    }
  }

  function renderMonthStats(txList: Transaction[], open: Account[]) {
    const now = new Date();
    const monthKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const month = txList.filter((t) => t.transactionDate.startsWith(monthKey));
    const monthName = now.toLocaleDateString('en-IN', { month: 'long' });
    const available = open.filter(canDebit).reduce((s, a) => s + availableToDebit(a), 0);
    document.getElementById('month-stats')!.innerHTML = `
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Money in · ${esc(monthName)}</span><span class="kpi-icon tone-green">${icon('trendUp', 18)}</span></div>
        <div class="kpi-value text-success">${formatCurrency(sumCredits(month))}</div>
      </div>
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Money out · ${esc(monthName)}</span><span class="kpi-icon tone-red">${icon('trendDown', 18)}</span></div>
        <div class="kpi-value text-danger">${formatCurrency(sumDebits(month))}</div>
      </div>
      <div class="card kpi">
        <div class="kpi-top"><span class="kpi-label">Available to spend</span><span class="kpi-icon tone-blue">${icon('wallet', 18)}</span></div>
        <div class="kpi-value">${formatCurrency(available)}</div>
        <div class="kpi-meta">After minimum balances</div>
      </div>`;
  }

  function renderCards(profile: Customer | null) {
    const el = document.getElementById('account-cards')!;
    if (acctList.length === 0) {
      el.innerHTML = `<div class="card">${emptyState({
        icon: 'bank',
        title: 'No accounts yet',
        text: profile
          ? `Visit your branch to open your first account. Quote customer ID #${profile.customerId}.`
          : 'Your online banking login is not yet linked to a customer record. Please contact your branch.',
      })}</div>`;
      return;
    }

    el.innerHTML = `<div class="account-grid">${acctList.map((a) => `
      <div class="bank-card type-${esc(a.accountType)} status-${esc(a.status)}" data-id="${a.accountId}" style="cursor:pointer">
        <div class="bank-card-top">
          <div>
            <div class="bank-card-type">${esc(accountTypeLabel(a.accountType))}</div>
            <div class="bank-card-number">${esc(a.accountNumber)}</div>
          </div>
          ${a.status === 'ACTIVE' ? '<span class="chip-mark"></span>' : `<span class="badge">${esc(a.status)}</span>`}
        </div>
        <div>
          <div class="bank-card-balance-label">${a.status === 'CLOSED' ? 'Closed' : 'Available balance'}</div>
          <div class="bank-card-balance">${formatCurrency(a.balance)}</div>
          <div class="bank-card-actions">
            <button class="btn" data-act="deposit" ${canCredit(a) ? '' : 'disabled'}>${icon('arrowIn', 14)} Deposit</button>
            <button class="btn" data-act="withdraw" ${canDebit(a) ? '' : 'disabled'}>${icon('arrowOut', 14)} Withdraw</button>
            <button class="btn" data-act="transfer" ${canDebit(a) ? '' : 'disabled'}>${icon('transfer', 14)} Transfer</button>
            <button class="btn" data-act="statement">${icon('file', 14)} Statement</button>
          </div>
        </div>
      </div>`).join('')}</div>`;

    el.querySelectorAll<HTMLElement>('.bank-card[data-id]').forEach((card) => {
      const acc = acctList.find((a) => a.accountId === Number(card.dataset.id))!;
      card.addEventListener('click', (e) => {
        const btn = (e.target as HTMLElement).closest<HTMLButtonElement>('button[data-act]');
        switch (btn?.dataset.act) {
          case 'deposit':  openCashModal('deposit', acctList, acc.accountId, loadAll); break;
          case 'withdraw': openCashModal('withdraw', acctList, acc.accountId, loadAll); break;
          case 'transfer': openTransferModal(acctList, acc.accountId, loadAll, { ownAccounts: acctList }); break;
          default:         openStatement(acc);
        }
      });
    });
  }

  function renderRecent(txList: Transaction[] | null, error: string) {
    const el = document.getElementById('recent-tx')!;
    if (!txList) {
      el.innerHTML = errorState(error);
      el.querySelector('[data-retry]')?.addEventListener('click', loadAll);
      return;
    }
    el.innerHTML = txList.length
      ? txTableHtml(txList.slice(0, 6), { showAccount: acctList.length > 1, showBalance: true, compact: true })
      : emptyState({ title: 'No transactions yet', text: 'Your deposits, withdrawals and transfers will show up here.' });
  }
}

function cardSkeleton(): string {
  return '<span class="skeleton" style="height:196px;border-radius:18px"></span>';
}
