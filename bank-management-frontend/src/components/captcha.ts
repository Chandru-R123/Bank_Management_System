/**
 * reCAPTCHA v2 widget helper.
 *
 * Site key comes from VITE_CAPTCHA_SITE_KEY (baked in at build time).
 * Enabled/disabled state is confirmed from the backend at runtime.
 * The secret key is NEVER here — it stays server-side only.
 */

// Site key baked in by Vite at build time from VITE_CAPTCHA_SITE_KEY env var
const BAKED_SITE_KEY: string = import.meta.env.VITE_CAPTCHA_SITE_KEY ?? '';

export interface CaptchaWidget {
  getToken: () => string;
  isDone: () => boolean;
  reset: () => void;
  destroy: () => void;
}

let _scriptInjected = false;
let _widgetSeq = 0;

function injectScript() {
  if (_scriptInjected || document.getElementById('recaptcha-script')) {
    _scriptInjected = true;
    return;
  }
  const s = document.createElement('script');
  s.id = 'recaptcha-script';
  s.src = 'https://www.google.com/recaptcha/api.js?render=explicit&onload=_grecaptchaReady';
  s.async = true;
  s.defer = true;
  document.head.appendChild(s);
  _scriptInjected = true;
}

/**
 * Mount a reCAPTCHA v2 widget.
 * If siteKey is empty, renders nothing and always returns empty token.
 */
export function mountCaptcha(container: HTMLElement, siteKey?: string): CaptchaWidget {
  const key = siteKey ?? BAKED_SITE_KEY;

  if (!key) {
    container.innerHTML = '';
    return { getToken: () => '', isDone: () => true, reset: () => {}, destroy: () => {} };
  }

  injectScript();

  const id = `rc-widget-${++_widgetSeq}`;
  container.innerHTML = `<div id="${id}"></div>`;

  let widgetId: number | null = null;
  let rendered = false;

  const render = () => {
    if (rendered) return;
    const win = window as unknown as {
      grecaptcha?: { render(el: string, opts: object): number };
      _grecaptchaReady?: () => void;
    };
    if (win.grecaptcha?.render) {
      try {
        widgetId = win.grecaptcha.render(id, { sitekey: key, theme: 'light' });
        rendered = true;
      } catch {
        // already rendered (e.g. HMR)
        rendered = true;
      }
    } else {
      setTimeout(render, 300);
    }
  };

  // Hook onto the onload callback if not yet fired, else render immediately
  const win = window as unknown as { _grecaptchaReady?: () => void; grecaptcha?: object };
  if (win.grecaptcha) {
    setTimeout(render, 50);
  } else {
    const prev = win._grecaptchaReady;
    win._grecaptchaReady = () => {
      prev?.();
      render();
    };
    setTimeout(render, 1000); // fallback if callback missed
  }

  const getToken = () => {
    const w = window as unknown as { grecaptcha?: { getResponse(id?: number): string } };
    return w.grecaptcha?.getResponse(widgetId ?? undefined) ?? '';
  };

  return {
    getToken,
    isDone: () => getToken().length > 0,
    reset: () => {
      const w = window as unknown as { grecaptcha?: { reset(id?: number): void } };
      w.grecaptcha?.reset(widgetId ?? undefined);
    },
    destroy: () => { container.innerHTML = ''; rendered = false; },
  };
}
