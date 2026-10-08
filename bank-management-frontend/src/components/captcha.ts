/**
 * Google reCAPTCHA v2 ("I'm not a robot" checkbox).
 *
 * The site key comes from the backend at runtime (GET /api/captcha/config),
 * which reads CAPTCHA_SITE_KEY from the .env file — no frontend rebuild is
 * needed when the keys change. The secret key never reaches the browser.
 *
 * Usage:
 *   const widget = await mountCaptcha(el);        // null when CAPTCHA is off
 *   await api.call(data, widget?.getToken());      // sent as X-Captcha-Token
 */
import { captcha } from '../api';
import type { CaptchaConfig } from '../api';

export interface CaptchaWidget {
  getToken: () => string;
  isDone: () => boolean;
  reset: () => void;
  destroy: () => void;
}

interface GRecaptcha {
  render(el: HTMLElement, opts: { sitekey: string; theme?: string; callback?: () => void }): number;
  getResponse(id?: number): string;
  reset(id?: number): void;
}

type RecaptchaWindow = Window & { grecaptcha?: GRecaptcha; __onRecaptchaLoad?: () => void };

let configPromise: Promise<CaptchaConfig> | null = null;
let scriptPromise: Promise<GRecaptcha> | null = null;

/** CAPTCHA settings from the backend (cached; "off" if the call fails). */
export function captchaConfig(): Promise<CaptchaConfig> {
  configPromise ??= captcha.getConfig()
    .then((c) => ({ enabled: !!c.enabled && !!c.siteKey, siteKey: c.siteKey ?? '' }))
    .catch(() => {
      configPromise = null;   // try again next time
      return { enabled: false, siteKey: '' };
    });
  return configPromise;
}

function loadScript(): Promise<GRecaptcha> {
  const win = window as RecaptchaWindow;
  scriptPromise ??= new Promise<GRecaptcha>((resolve, reject) => {
    if (win.grecaptcha?.render) { resolve(win.grecaptcha); return; }
    win.__onRecaptchaLoad = () => resolve(win.grecaptcha!);
    const s = document.createElement('script');
    s.src = 'https://www.google.com/recaptcha/api.js?render=explicit&onload=__onRecaptchaLoad';
    s.async = true;
    s.defer = true;
    s.onerror = () => {
      scriptPromise = null;
      reject(new Error('Could not load Google reCAPTCHA. Check your internet connection.'));
    };
    document.head.appendChild(s);
  });
  return scriptPromise;
}

/**
 * Renders the checkbox into `container`.
 * Resolves to null when CAPTCHA is switched off on the server.
 */
export async function mountCaptcha(container: HTMLElement): Promise<CaptchaWidget | null> {
  const cfg = await captchaConfig();
  if (!cfg.enabled) {
    container.innerHTML = '';
    return null;
  }

  const g = await loadScript();
  container.innerHTML = '<div></div>';
  const target = container.firstElementChild as HTMLElement;
  const widgetId = g.render(target, {
    sitekey: cfg.siteKey,
    theme: document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light',
  });

  const getToken = () => g.getResponse(widgetId) ?? '';
  return {
    getToken,
    isDone: () => getToken().length > 0,
    reset: () => g.reset(widgetId),
    destroy: () => { container.innerHTML = ''; },
  };
}
