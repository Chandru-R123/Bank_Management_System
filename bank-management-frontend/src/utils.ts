import { icon } from './icons';
import type { IconName } from './icons';

// ── HTML escaping ─────────────────────────────────────────────────────────────
//
//  Every value that comes from the API (names, addresses, remarks...) MUST go
//  through esc() before being placed in innerHTML. Customers self-register via
//  Keycloak and choose their own names, so unescaped output = stored XSS.
//
const ESCAPES: Record<string, string> = {
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
};

export function esc(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ESCAPES[c]);
}

// ── Toast notifications ───────────────────────────────────────────────────────

export function toast(
  message: string,
  type: 'success' | 'error' | 'info' = 'info',
) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    container.setAttribute('role', 'status');
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);
  }
  const iconName: IconName = type === 'success' ? 'checkCircle' : type === 'error' ? 'alert' : 'info';
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.innerHTML = `
    <span class="toast-icon">${icon(iconName, 18)}</span>
    <div class="toast-msg">${esc(message)}</div>
    <button class="toast-close" aria-label="Dismiss">${icon('x', 16)}</button>`;
  container.appendChild(el);

  const remove = () => {
    el.classList.add('leaving');
    setTimeout(() => el.remove(), 200);
  };
  el.querySelector('.toast-close')!.addEventListener('click', remove);
  setTimeout(remove, type === 'error' ? 6000 : 4000);
}

// ── Modal & drawer ────────────────────────────────────────────────────────────

export interface ModalOptions {
  title: string;
  subtitle?: string;
  body: string;
  footer?: string;
  size?: 'sm' | 'md' | 'lg';
  drawer?: boolean;
  /** Focus the first form field on open (default true). */
  autofocus?: boolean;
  onClose?: () => void;
}

export interface ModalHandle {
  el: HTMLElement;
  body: HTMLElement;
  close: () => void;
  setSubtitle: (text: string) => void;
}

/**
 * Opens a modal (or right-side drawer). Elements inside with [data-close]
 * close it. ESC and backdrop click also close it.
 */
export function openModal(opts: ModalOptions): ModalHandle {
  const overlay = document.createElement('div');
  overlay.className = `overlay${opts.drawer ? ' drawer-overlay' : ''}`;
  const panelClass = opts.drawer ? 'drawer' : `modal${opts.size && opts.size !== 'md' ? ` modal-${opts.size}` : ''}`;
  overlay.innerHTML = `
    <div class="${panelClass}" role="dialog" aria-modal="true">
      <div class="modal-header">
        <div>
          <div class="modal-title">${esc(opts.title)}</div>
          <div class="modal-sub" data-subtitle>${esc(opts.subtitle ?? '')}</div>
        </div>
        <button class="btn-icon" data-close aria-label="Close">${icon('x', 18)}</button>
      </div>
      <div class="modal-body">${opts.body}</div>
      ${opts.footer ? `<div class="modal-footer">${opts.footer}</div>` : ''}
    </div>`;
  document.body.appendChild(overlay);
  document.body.style.overflow = 'hidden';

  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    document.removeEventListener('keydown', onKey);
    overlay.classList.add('closing');
    setTimeout(() => {
      overlay.remove();
      if (!document.querySelector('.overlay')) document.body.style.overflow = '';
    }, 150);
    opts.onClose?.();
  };
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && overlay === lastOverlay()) close();
  };
  document.addEventListener('keydown', onKey);
  overlay.addEventListener('mousedown', (e) => {
    if (e.target === overlay) close();
  });
  overlay.addEventListener('click', (e) => {
    const target = e.target as HTMLElement;
    if (target.closest('[data-close]')) close();
  });

  if (opts.autofocus !== false) {
    const firstInput = overlay.querySelector<HTMLElement>('input:not([disabled]), select:not([disabled]), textarea');
    setTimeout(() => firstInput?.focus(), 50);
  }

  const subtitleEl = overlay.querySelector<HTMLElement>('[data-subtitle]')!;
  return {
    el: overlay,
    body: overlay.querySelector<HTMLElement>('.modal-body')!,
    close,
    setSubtitle: (text: string) => { subtitleEl.textContent = text; },
  };
}

function lastOverlay(): Element | null {
  const all = document.querySelectorAll('.overlay');
  return all.length ? all[all.length - 1] : null;
}

/** Styled replacement for window.confirm(). Resolves true when confirmed. */
export function confirmDialog(opts: {
  title: string;
  message: string;
  confirmText?: string;
  tone?: 'danger' | 'primary';
}): Promise<boolean> {
  return new Promise((resolve) => {
    let result = false;
    const tone = opts.tone ?? 'primary';
    const m = openModal({
      title: '',
      size: 'sm',
      body: `
        <div class="confirm-icon ${tone === 'danger' ? 'tone-red' : 'tone-blue'}">
          ${icon(tone === 'danger' ? 'alert' : 'info', 22)}
        </div>
        <div class="modal-title">${esc(opts.title)}</div>
        <p class="confirm-text mt-2">${esc(opts.message)}</p>`,
      footer: `
        <button class="btn btn-secondary" data-close>Cancel</button>
        <button class="btn ${tone === 'danger' ? 'btn-danger' : 'btn-primary'}" data-confirm>${esc(opts.confirmText ?? 'Confirm')}</button>`,
      onClose: () => resolve(result),
    });
    m.el.querySelector('.modal-header .modal-title')?.remove();
    m.el.querySelector<HTMLButtonElement>('[data-confirm]')!.addEventListener('click', () => {
      result = true;
      m.close();
    });
  });
}

// ── Popover menu ──────────────────────────────────────────────────────────────

export interface MenuItem {
  label: string;
  icon: IconName;
  onClick: () => void;
  danger?: boolean;
  disabled?: boolean;
  hint?: string;
}

/** Small action menu anchored under a button. Closes on outside click / ESC / scroll. */
export function openMenu(anchor: HTMLElement, items: (MenuItem | 'divider')[]) {
  document.querySelector('.menu')?.remove();
  const menu = document.createElement('div');
  menu.className = 'menu';
  menu.setAttribute('role', 'menu');
  menu.innerHTML = items.map((it, i) => it === 'divider'
    ? '<hr/>'
    : `<button role="menuitem" data-i="${i}" class="${it.danger ? 'danger' : ''}" ${it.disabled ? 'disabled' : ''} ${it.hint ? `title="${esc(it.hint)}"` : ''}>${icon(it.icon, 16)}<span>${esc(it.label)}</span></button>`,
  ).join('');
  document.body.appendChild(menu);

  const r = anchor.getBoundingClientRect();
  const w = menu.offsetWidth;
  const h = menu.offsetHeight;
  const left = Math.max(8, Math.min(r.right - w, window.innerWidth - w - 8));
  const top = r.bottom + 6 + h > window.innerHeight ? r.top - h - 6 : r.bottom + 6;
  menu.style.left = `${left}px`;
  menu.style.top = `${Math.max(8, top)}px`;

  const close = () => {
    menu.remove();
    document.removeEventListener('mousedown', onDoc, true);
    document.removeEventListener('keydown', onKey);
    window.removeEventListener('scroll', close, true);
  };
  const onDoc = (e: MouseEvent) => {
    if (!menu.contains(e.target as Node) && !anchor.contains(e.target as Node)) close();
  };
  const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
  setTimeout(() => {
    document.addEventListener('mousedown', onDoc, true);
    document.addEventListener('keydown', onKey);
    window.addEventListener('scroll', close, true);
  });

  menu.querySelectorAll<HTMLButtonElement>('button[data-i]').forEach((b) => {
    b.addEventListener('click', () => {
      const it = items[Number(b.dataset.i)];
      close();
      if (it !== 'divider') it.onClick();
    });
  });
}

/** Disables a button and shows a spinner while fn runs — prevents double submits. */
export async function withBusy<T>(btn: HTMLButtonElement, fn: () => Promise<T>): Promise<T | undefined> {
  if (btn.disabled) return undefined;
  btn.disabled = true;
  btn.classList.add('is-loading');
  try {
    return await fn();
  } finally {
    btn.disabled = false;
    btn.classList.remove('is-loading');
  }
}

// ── Formatting ────────────────────────────────────────────────────────────────

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
const inrCompact = new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 2,
});

export function formatCurrency(amount: number | null | undefined): string {
  return inr.format(Number(amount ?? 0));
}

export function formatCompact(amount: number): string {
  return Math.abs(amount) < 100000 ? formatCurrency(amount) : inrCompact.format(amount);
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDay(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  return new Date(dateStr).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatTime(dateStr: string): string {
  return new Date(dateStr).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

export function todayLong(): string {
  return new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

export function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

export function initials(name: string | null | undefined): string {
  const parts = String(name ?? '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return ((parts[0][0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

/** Parses a user-entered amount. Returns null when invalid (≤ 0 or > 2 decimals). */
export function parseAmount(raw: string): number | null {
  const s = raw.replace(/,/g, '').trim();
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return null;
  const n = Number(s);
  return n > 0 ? n : null;
}

// ── Domain helpers ────────────────────────────────────────────────────────────

interface TxMeta { label: string; credit: boolean; icon: IconName; }

const TX_META: Record<string, TxMeta> = {
  DEPOSIT:         { label: 'Deposit',         credit: true,  icon: 'arrowIn' },
  WITHDRAW:        { label: 'Withdrawal',      credit: false, icon: 'arrowOut' },
  TRANSFER_IN:     { label: 'Transfer in',     credit: true,  icon: 'transfer' },
  TRANSFER_OUT:    { label: 'Transfer out',    credit: false, icon: 'transfer' },
  OPENING_DEPOSIT: { label: 'Opening deposit', credit: true,  icon: 'wallet' },
  CLOSURE_PAYOUT:  { label: 'Closure payout',  credit: false, icon: 'closeCircle' },
};

export function txMeta(type: string): TxMeta {
  const t = (type ?? '').toUpperCase();
  return TX_META[t] ?? {
    label: t.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase()),
    credit: /DEPOSIT|CREDIT|_IN$/.test(t),
    icon: 'activity',
  };
}

export function txIcon(type: string): string {
  const m = txMeta(type);
  return `<span class="tx-icon ${m.credit ? 'tone-green' : 'tone-red'}">${icon(m.icon, 16)}</span>`;
}

export function signedAmount(type: string, amount: number): string {
  const credit = txMeta(type).credit;
  return `<span class="${credit ? 'amount-credit' : 'amount-debit'}">${credit ? '+' : '−'}${formatCurrency(amount)}</span>`;
}

export const ACCOUNT_TYPES = [
  { value: 'SAVINGS', label: 'Savings' },
  { value: 'CURRENT', label: 'Current' },
  { value: 'FIXED_DEPOSIT', label: 'Fixed Deposit' },
] as const;

export function accountTypeLabel(type: string): string {
  return ACCOUNT_TYPES.find((t) => t.value === type)?.label ?? type;
}

export function accountTypeBadge(type: string): string {
  const cls = type === 'SAVINGS' ? 'badge-blue' : type === 'CURRENT' ? 'badge-violet' : 'badge-amber';
  return `<span class="badge ${cls}">${esc(accountTypeLabel(type))}</span>`;
}

export function statusBadge(status: string): string {
  const s = status || 'ACTIVE';
  const cls = s === 'ACTIVE' ? 'badge-green' : s === 'FROZEN' ? 'badge-sky' : 'badge-neutral';
  const label = s.charAt(0) + s.slice(1).toLowerCase();
  return `<span class="badge badge-dot ${cls}">${esc(label)}</span>`;
}

// ── State blocks ──────────────────────────────────────────────────────────────

export function emptyState(opts: { icon?: IconName; title: string; text?: string; action?: string }): string {
  return `
    <div class="empty-state">
      <div class="empty-icon">${icon(opts.icon ?? 'inbox', 22)}</div>
      <div class="empty-title">${esc(opts.title)}</div>
      ${opts.text ? `<div class="empty-text">${esc(opts.text)}</div>` : ''}
      ${opts.action ?? ''}
    </div>`;
}

export function errorState(message: string): string {
  return `
    <div class="empty-state error">
      <div class="empty-icon">${icon('alert', 22)}</div>
      <div class="empty-title">Couldn't load this data</div>
      <div class="empty-text">${esc(message)}</div>
      <button class="btn btn-secondary btn-sm" data-retry>${icon('refresh', 14)} Try again</button>
    </div>`;
}

export function skeletonRows(rows = 5): string {
  const row = `
    <div class="skeleton-row">
      <span class="skeleton" style="width:32px;height:32px;border-radius:9px"></span>
      <span class="skeleton" style="flex:2;height:12px"></span>
      <span class="skeleton" style="flex:1;height:12px"></span>
      <span class="skeleton" style="width:90px;height:12px"></span>
    </div>`;
  return `<div class="skeleton-rows">${row.repeat(rows)}</div>`;
}

export function errorMessage(err: unknown): string {
  return (err as Error)?.message || 'Something went wrong';
}

// ── CSV export ────────────────────────────────────────────────────────────────

export function downloadCsv(
  filename: string,
  header: string[],
  rows: (string | number | null | undefined)[][],
) {
  const cell = (v: string | number | null | undefined) => {
    const s = String(v ?? '');
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const csv = [header, ...rows].map((r) => r.map(cell).join(',')).join('\r\n');
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Debounces an event handler (search boxes). */
export function debounce(fn: (e: Event) => void, ms = 200): (e: Event) => void {
  let t: ReturnType<typeof setTimeout> | undefined;
  return (e: Event) => {
    if (t) clearTimeout(t);
    t = setTimeout(() => fn(e), ms);
  };
}
