import { accounts } from '../api';
import type { Account, AccountLookup, Transaction } from '../api';
import { icon } from '../icons';
import {
  esc, formatCurrency, formatDate, openModal, parseAmount, toast, withBusy,
  accountTypeLabel, errorMessage,
} from '../utils';

/** Must match bank.savings.minimum-balance on the server (used for hints only — the server enforces it). */
export const SAVINGS_MIN_BALANCE = 1000;

/** Accounts that can send money: active and not a fixed deposit. */
export function canDebit(a: Account): boolean {
  return a.status === 'ACTIVE' && a.accountType !== 'FIXED_DEPOSIT';
}

/** Accounts that can receive money: active and not a fixed deposit. */
export function canCredit(a: Account): boolean {
  return a.status === 'ACTIVE' && a.accountType !== 'FIXED_DEPOSIT';
}

/** Largest amount the UI will let a user debit (server re-checks). */
export function availableToDebit(a: Account): number {
  const min = a.accountType === 'SAVINGS' ? SAVINGS_MIN_BALANCE : 0;
  return Math.max(0, a.balance - min);
}

function accountOption(a: Account, selected: boolean, withOwner = false): string {
  const owner = withOwner && a.customerName ? ` · ${a.customerName}` : '';
  return `<option value="${a.accountId}" ${selected ? 'selected' : ''}>${esc(a.accountNumber)} · ${esc(accountTypeLabel(a.accountType))}${esc(owner)} — ${formatCurrency(a.balance)}</option>`;
}

const QUICK_AMOUNTS = [1000, 5000, 10000, 25000];

function amountFieldHtml(): string {
  return `
    <div class="field" data-field="amount">
      <label class="field-label" for="mv-amount">Amount<span class="req">*</span></label>
      <div class="input-group">
        <span class="input-prefix">₹</span>
        <input id="mv-amount" class="input input-amount" inputmode="decimal" autocomplete="off" placeholder="0.00" />
      </div>
      <div class="chips mt-1">
        ${QUICK_AMOUNTS.map((v) => `<button type="button" class="chip" data-quick="${v}">${formatCurrency(v).replace('.00', '')}</button>`).join('')}
      </div>
      <div class="field-error" data-error></div>
    </div>`;
}

function wireQuickAmounts(root: HTMLElement) {
  const input = root.querySelector<HTMLInputElement>('#mv-amount')!;
  root.querySelectorAll<HTMLButtonElement>('[data-quick]').forEach((b) => {
    b.addEventListener('click', () => {
      input.value = b.dataset.quick!;
      input.dispatchEvent(new Event('input'));
      input.focus();
    });
  });
}

function setFieldError(root: HTMLElement, field: string, message: string | null) {
  const f = root.querySelector<HTMLElement>(`[data-field="${field}"]`);
  if (!f) return;
  f.classList.toggle('has-error', !!message);
  const e = f.querySelector<HTMLElement>('[data-error]');
  if (e) e.textContent = message ?? '';
}

function receiptHtml(opts: {
  title: string;
  amount: number;
  lines: [string, string][];
}): string {
  return `
    <div class="receipt">
      <div class="receipt-icon">${icon('check', 30, 2.5)}</div>
      <div class="fw-600">${esc(opts.title)}</div>
      <div class="receipt-amount">${formatCurrency(opts.amount)}</div>
      <div class="text-muted text-sm">${esc(formatDate(new Date().toISOString()))}</div>
      <div class="receipt-lines">
        ${opts.lines.map(([k, v]) => `<div><span>${esc(k)}</span><span>${esc(v)}</span></div>`).join('')}
      </div>
    </div>`;
}

// ── Deposit / Withdraw ───────────────────────────────────────────────────────

export function openCashModal(
  kind: 'deposit' | 'withdraw',
  list: Account[],
  preselectId: number | null,
  onDone: () => void,
  opts: { withOwner?: boolean } = {},
) {
  const eligible = list.filter(kind === 'deposit' ? canCredit : canDebit);
  const isDeposit = kind === 'deposit';
  const title = isDeposit ? 'Deposit money' : 'Withdraw money';

  if (eligible.length === 0) {
    openModal({
      title,
      size: 'sm',
      body: `<div class="callout callout-warn">${icon('info', 16)}<div>No eligible account. ${isDeposit ? 'Deposits' : 'Withdrawals'} are only possible on active Savings or Current accounts — Fixed Deposits and frozen or closed accounts are excluded.</div></div>`,
      footer: '<button class="btn btn-secondary" data-close>Close</button>',
    });
    return;
  }

  const initial = eligible.find((a) => a.accountId === preselectId) ?? eligible[0];

  const m = openModal({
    title,
    subtitle: isDeposit ? 'Funds are credited immediately.' : 'Funds are debited immediately.',
    body: `
      <div class="form-grid" data-form>
        <div class="field">
          <label class="field-label" for="mv-account">Account</label>
          <select id="mv-account" class="select">
            ${eligible.map((a) => accountOption(a, a.accountId === initial.accountId, opts.withOwner)).join('')}
          </select>
        </div>
        <div class="summary-box" data-summary></div>
        ${amountFieldHtml()}
        <div class="field">
          <label class="field-label" for="mv-remarks">Remarks <span class="text-muted">(optional)</span></label>
          <input id="mv-remarks" class="input" maxlength="140" placeholder="${isDeposit ? 'e.g. Salary, cash deposit' : 'e.g. Rent, ATM withdrawal'}" />
        </div>
      </div>`,
    footer: `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn ${isDeposit ? 'btn-success' : 'btn-primary'}" data-submit>${icon(isDeposit ? 'arrowIn' : 'arrowOut', 16)} ${isDeposit ? 'Deposit' : 'Withdraw'}</button>`,
  });

  const root = m.el;
  const select = root.querySelector<HTMLSelectElement>('#mv-account')!;
  const amountEl = root.querySelector<HTMLInputElement>('#mv-amount')!;
  const remarksEl = root.querySelector<HTMLInputElement>('#mv-remarks')!;
  const summary = root.querySelector<HTMLElement>('[data-summary]')!;
  const current = () => eligible.find((a) => a.accountId === Number(select.value))!;

  const renderSummary = () => {
    const a = current();
    summary.innerHTML = isDeposit
      ? `<div><div class="label">Current balance</div><div class="value">${formatCurrency(a.balance)}</div></div>`
      : `<div><div class="label">Available to withdraw</div><div class="value">${formatCurrency(availableToDebit(a))}</div></div>
         ${a.accountType === 'SAVINGS' ? `<div class="text-sm text-muted" style="text-align:right">Keeps ${formatCurrency(SAVINGS_MIN_BALANCE)}<br/>minimum balance</div>` : ''}`;
  };
  renderSummary();
  select.addEventListener('change', renderSummary);
  amountEl.addEventListener('input', () => setFieldError(root, 'amount', null));
  wireQuickAmounts(root);

  const submit = root.querySelector<HTMLButtonElement>('[data-submit]')!;
  const go = () => withBusy(submit, async () => {
    const a = current();
    const amount = parseAmount(amountEl.value);
    if (amount === null) {
      setFieldError(root, 'amount', 'Enter a valid amount greater than zero (max 2 decimals)');
      return;
    }
    if (!isDeposit && amount > availableToDebit(a)) {
      setFieldError(root, 'amount', `Exceeds the available amount of ${formatCurrency(availableToDebit(a))}`);
      return;
    }
    try {
      const fn = isDeposit ? accounts.deposit : accounts.withdraw;
      const updated = await fn(a.accountId, amount, remarksEl.value.trim() || undefined);
      m.body.innerHTML = receiptHtml({
        title: isDeposit ? 'Deposit successful' : 'Withdrawal successful',
        amount,
        lines: [
          ['Account', updated.accountNumber],
          ['New balance', formatCurrency(updated.balance)],
          ...(remarksEl.value.trim() ? [['Remarks', remarksEl.value.trim()] as [string, string]] : []),
        ],
      });
      m.el.querySelector('.modal-footer')!.innerHTML = '<button class="btn btn-primary" data-close>Done</button>';
      m.setSubtitle('');
      onDone();
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  });
  submit.addEventListener('click', go);
  amountEl.addEventListener('keydown', (e) => { if (e.key === 'Enter') void go(); });
}

// ── Transfer ─────────────────────────────────────────────────────────────────

/**
 * @param sourceAccounts  accounts the user may send from (own accounts for a
 *                        customer, every account for staff)
 * @param ownAccounts     for customers: their accounts (offered as quick
 *                        destinations). Staff pass an empty list.
 */
export function openTransferModal(
  sourceAccounts: Account[],
  preselectFromId: number | null,
  onDone: () => void,
  opts: { ownAccounts?: Account[]; withOwner?: boolean } = {},
) {
  const sources = sourceAccounts.filter(canDebit);
  if (sources.length === 0) {
    openModal({
      title: 'Transfer money',
      size: 'sm',
      body: `<div class="callout callout-warn">${icon('info', 16)}<div>You need an active Savings or Current account with funds to make a transfer.</div></div>`,
      footer: '<button class="btn btn-secondary" data-close>Close</button>',
    });
    return;
  }
  const own = (opts.ownAccounts ?? []).filter(canCredit);
  const initial = sources.find((a) => a.accountId === preselectFromId) ?? sources[0];

  const m = openModal({
    title: 'Transfer money',
    subtitle: 'Send funds instantly to any State Bank account.',
    body: `
      <div class="form-grid" data-form>
        <div class="field">
          <label class="field-label" for="tr-from">From</label>
          <select id="tr-from" class="select">
            ${sources.map((a) => accountOption(a, a.accountId === initial.accountId, opts.withOwner)).join('')}
          </select>
          <div class="field-hint" data-available></div>
        </div>

        ${own.length > 1 ? `
        <div class="segmented" data-mode>
          <button type="button" class="active" data-v="other">Another account</button>
          <button type="button" data-v="own">Between my accounts</button>
        </div>` : ''}

        <div class="field" data-field="to" data-mode-panel="other">
          <label class="field-label" for="tr-to-number">Beneficiary account number<span class="req">*</span></label>
          <div class="flex gap-2">
            <input id="tr-to-number" class="input mono" placeholder="e.g. SB-12345678" autocomplete="off" style="text-transform:uppercase" />
            <button type="button" class="btn btn-secondary" data-verify>Verify</button>
          </div>
          <div class="field-error" data-error></div>
          <div data-beneficiary></div>
        </div>

        ${own.length > 1 ? `
        <div class="field hidden" data-mode-panel="own">
          <label class="field-label" for="tr-to-own">To</label>
          <select id="tr-to-own" class="select"></select>
        </div>` : ''}

        ${amountFieldHtml()}
        <div class="field">
          <label class="field-label" for="tr-remarks">Remarks <span class="text-muted">(optional)</span></label>
          <input id="tr-remarks" class="input" maxlength="140" placeholder="e.g. Rent for October" />
        </div>
      </div>
      <div class="hidden" data-review></div>`,
    footer: `
      <button class="btn btn-secondary" data-close data-cancel>Cancel</button>
      <button class="btn btn-primary" data-next>Review transfer ${icon('chevronRight', 16)}</button>`,
  });

  const root = m.el;
  const fromEl = root.querySelector<HTMLSelectElement>('#tr-from')!;
  const numberEl = root.querySelector<HTMLInputElement>('#tr-to-number')!;
  const ownEl = root.querySelector<HTMLSelectElement>('#tr-to-own');
  const amountEl = root.querySelector<HTMLInputElement>('#mv-amount')!;
  const remarksEl = root.querySelector<HTMLInputElement>('#tr-remarks')!;
  const benEl = root.querySelector<HTMLElement>('[data-beneficiary]')!;
  const availEl = root.querySelector<HTMLElement>('[data-available]')!;
  const formEl = root.querySelector<HTMLElement>('[data-form]')!;
  const reviewEl = root.querySelector<HTMLElement>('[data-review]')!;
  const footer = root.querySelector<HTMLElement>('.modal-footer')!;

  let mode: 'other' | 'own' = 'other';
  let verified: AccountLookup | null = null;

  const from = () => sources.find((a) => a.accountId === Number(fromEl.value))!;

  const refreshFrom = () => {
    const a = from();
    availEl.textContent = `Available: ${formatCurrency(availableToDebit(a))}${a.accountType === 'SAVINGS' ? ` (keeps ${formatCurrency(SAVINGS_MIN_BALANCE)} minimum)` : ''}`;
    if (ownEl) {
      const targets = own.filter((x) => x.accountId !== a.accountId);
      ownEl.innerHTML = targets.map((x) => accountOption(x, false)).join('');
    }
  };
  refreshFrom();
  fromEl.addEventListener('change', refreshFrom);

  root.querySelectorAll<HTMLButtonElement>('[data-mode] button').forEach((b) => {
    b.addEventListener('click', () => {
      mode = b.dataset.v as typeof mode;
      root.querySelectorAll('[data-mode] button').forEach((x) => x.classList.toggle('active', x === b));
      root.querySelectorAll<HTMLElement>('[data-mode-panel]').forEach((p) => p.classList.toggle('hidden', p.dataset.modePanel !== mode));
    });
  });

  const verify = async (): Promise<AccountLookup | null> => {
    const number = numberEl.value.trim().toUpperCase();
    setFieldError(root, 'to', null);
    benEl.innerHTML = '';
    verified = null;
    if (!number) {
      setFieldError(root, 'to', 'Enter the beneficiary account number');
      return null;
    }
    if (number === from().accountNumber.toUpperCase()) {
      setFieldError(root, 'to', 'You cannot transfer to the same account');
      return null;
    }
    try {
      const info = await accounts.lookup(number);
      benEl.innerHTML = `
        <div class="beneficiary mt-2 ${info.canReceive ? '' : 'bad'}">
          <span class="avatar" style="width:30px;height:30px">${icon(info.canReceive ? 'check' : 'alert', 16)}</span>
          <div class="grow">
            <div class="fw-600">${esc(info.holderName)}</div>
            <div class="text-sm text-muted">${esc(info.accountNumber)} · ${esc(accountTypeLabel(info.accountType))}</div>
          </div>
          ${info.canReceive ? '<span class="badge badge-green">Verified</span>' : '<span class="badge badge-red">Cannot receive</span>'}
        </div>`;
      if (!info.canReceive) return null;
      verified = info;
      return info;
    } catch (err) {
      setFieldError(root, 'to', errorMessage(err));
      return null;
    }
  };

  const verifyBtn = root.querySelector<HTMLButtonElement>('[data-verify]')!;
  verifyBtn.addEventListener('click', () => withBusy(verifyBtn, verify));
  numberEl.addEventListener('input', () => { verified = null; benEl.innerHTML = ''; setFieldError(root, 'to', null); });
  amountEl.addEventListener('input', () => setFieldError(root, 'amount', null));
  wireQuickAmounts(root);

  const showForm = () => {
    formEl.classList.remove('hidden');
    reviewEl.classList.add('hidden');
    footer.innerHTML = `
      <button class="btn btn-secondary" data-close>Cancel</button>
      <button class="btn btn-primary" data-next>Review transfer ${icon('chevronRight', 16)}</button>`;
    footer.querySelector<HTMLButtonElement>('[data-next]')!.addEventListener('click', onNext);
  };

  const onNext = async (e: Event) => {
    const btn = e.currentTarget as HTMLButtonElement;
    await withBusy(btn, async () => {
      const src = from();
      const amount = parseAmount(amountEl.value);
      let destLabel: string;
      let destNumber: string;
      let toAccountId: number | undefined;

      if (mode === 'own' && ownEl) {
        const dest = own.find((x) => x.accountId === Number(ownEl.value));
        if (!dest) { toast('Choose a destination account', 'error'); return; }
        toAccountId = dest.accountId;
        destNumber = dest.accountNumber;
        destLabel = `${accountTypeLabel(dest.accountType)} (own account)`;
      } else {
        const info = verified ?? await verify();
        if (!info) return;
        destNumber = info.accountNumber;
        destLabel = info.holderName;
      }

      if (amount === null) {
        setFieldError(root, 'amount', 'Enter a valid amount greater than zero (max 2 decimals)');
        return;
      }
      if (amount > availableToDebit(src)) {
        setFieldError(root, 'amount', `Exceeds the available amount of ${formatCurrency(availableToDebit(src))}`);
        return;
      }

      const remarks = remarksEl.value.trim();
      formEl.classList.add('hidden');
      reviewEl.classList.remove('hidden');
      reviewEl.innerHTML = `
        <div class="text-center" style="text-align:center">
          <div class="text-muted text-sm">You are sending</div>
          <div class="receipt-amount">${formatCurrency(amount)}</div>
        </div>
        <div class="receipt-lines">
          <div><span>From</span><span class="mono">${esc(src.accountNumber)}</span></div>
          <div><span>To</span><span class="mono">${esc(destNumber)}</span></div>
          <div><span>Beneficiary</span><span>${esc(destLabel)}</span></div>
          ${remarks ? `<div><span>Remarks</span><span>${esc(remarks)}</span></div>` : ''}
          <div><span>Balance after</span><span>${formatCurrency(src.balance - amount)}</span></div>
        </div>
        <div class="callout callout-info mt-4">${icon('shield', 16)}<div>Transfers are instant and cannot be reversed. Please confirm the beneficiary details.</div></div>`;
      footer.innerHTML = `
        <button class="btn btn-secondary" data-back>${icon('chevronLeft', 16)} Back</button>
        <button class="btn btn-primary" data-confirm>${icon('transfer', 16)} Confirm & send</button>`;
      footer.querySelector('[data-back]')!.addEventListener('click', showForm);
      const confirmBtn = footer.querySelector<HTMLButtonElement>('[data-confirm]')!;
      confirmBtn.addEventListener('click', () => withBusy(confirmBtn, async () => {
        try {
          const tx: Transaction = await accounts.transfer({
            fromAccountId: src.accountId,
            toAccountId,
            toAccountNumber: toAccountId ? undefined : destNumber,
            amount,
            description: remarks || undefined,
          });
          m.setSubtitle('');
          m.body.innerHTML = receiptHtml({
            title: 'Transfer successful',
            amount,
            lines: [
              ['Reference', tx.referenceId ?? `#${tx.transactionId}`],
              ['From', src.accountNumber],
              ['To', `${destNumber} · ${destLabel}`],
              ['Balance after', tx.balanceAfter != null ? formatCurrency(tx.balanceAfter) : '—'],
            ],
          });
          footer.innerHTML = '<button class="btn btn-primary" data-close>Done</button>';
          onDone();
        } catch (err) {
          toast(errorMessage(err), 'error');
        }
      }));
    });
  };

  footer.querySelector<HTMLButtonElement>('[data-next]')!.addEventListener('click', onNext);
}
