/**
 * Adds the Google reCAPTCHA v2 checkbox to Keycloak's LOGIN form.
 *
 * The site key comes from the backend (GET /api/captcha/config, same origin
 * through NGINX), which reads CAPTCHA_SITE_KEY from .env. Google puts the
 * token in the hidden "g-recaptcha-response" field of the form; the
 * reCAPTCHA login plugin (keycloak/recaptcha-login) verifies it on the
 * server, so this script is only the visible part of the check.
 *
 * The register page uses Keycloak's built-in reCAPTCHA and is not touched here.
 */
(function () {
    function setup() {
        var form = document.getElementById('kc-form-login');
        if (!form || !window.fetch) return;

        fetch('/api/captcha/config', { credentials: 'omit' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (cfg) {
                if (!cfg || !cfg.enabled || !cfg.siteKey) return;
                mount(form, cfg.siteKey);
            })
            .catch(function () { /* backend not reachable: the server check will report it */ });
    }

    function mount(form, siteKey) {
        var box = document.createElement('div');
        box.className = 'bank-login-recaptcha';
        box.style.margin = '12px 0';
        var widget = document.createElement('div');
        var error = document.createElement('div');
        error.style.cssText = 'color:#c9190b;font-size:13px;margin-top:6px;display:none';
        error.textContent = 'Please tick "I\'m not a robot".';
        box.appendChild(widget);
        box.appendChild(error);

        var buttons = document.getElementById('kc-form-buttons');
        if (buttons && buttons.parentNode === form) {
            form.insertBefore(box, buttons);
        } else {
            form.appendChild(box);
        }

        window.__bankLoginRecaptchaReady = function () {
            window.grecaptcha.render(widget, {
                sitekey: siteKey,
                callback: function () { error.style.display = 'none'; }
            });
        };
        var script = document.createElement('script');
        script.src = 'https://www.google.com/recaptcha/api.js?onload=__bankLoginRecaptchaReady&render=explicit';
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);

        // Friendly check before posting (the real check is on the server)
        form.addEventListener('submit', function (e) {
            if (window.grecaptcha && window.grecaptcha.getResponse && !window.grecaptcha.getResponse()) {
                e.preventDefault();
                error.style.display = 'block';
                // Keycloak's form disables the Sign In button on submit — undo that
                var login = document.getElementById('kc-login');
                if (login) login.disabled = false;
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setup);
    } else {
        setup();
    }
})();
