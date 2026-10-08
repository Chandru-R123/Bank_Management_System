package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts Google reCAPTCHA v2 on Keycloak's REGISTER and LOGIN pages, using the
 * keys from the environment (.env), so no keys are committed in the realm file.
 *
 * Keycloak only reads the realm file when its volume is first created, so
 * this runs on every backend start (in the background, waiting for Keycloak).
 *
 * Register page — Keycloak's built-in reCAPTCHA step:
 *   1. finds the "reCAPTCHA" step in the realm's registration flow
 *   2. writes CAPTCHA_SITE_KEY / CAPTCHA_SECRET_KEY into its config
 *   3. sets the step to REQUIRED (or DISABLED when CAPTCHA is off, so
 *      registration still works without keys)
 *
 * Login page — our plugin (keycloak/recaptcha-login), since Keycloak 24 has
 * no login CAPTCHA of its own:
 *   4. copies the built-in "browser" flow to "browser with recaptcha"
 *   5. replaces its "Username Password Form" with the reCAPTCHA version
 *   6. makes it the realm's login (browser) flow
 *   The plugin reads the keys itself and acts as the normal form without them.
 *
 * Both pages: lets the page load Google's widget (Content-Security-Policy frame-src).
 */
@Component
public class KeycloakCaptchaSync {

    private static final Logger log = LoggerFactory.getLogger(KeycloakCaptchaSync.class);

    /** Keycloak's reCAPTCHA v2 form action. */
    private static final String RECAPTCHA_PROVIDER = "registration-recaptcha-action";
    private static final String CONFIG_ALIAS = "bank-recaptcha";

    /** Our login plugin — see keycloak/recaptcha-login (RecaptchaUsernamePasswordFormFactory). */
    private static final String LOGIN_PROVIDER = "auth-recaptcha-username-password-form";
    private static final String STANDARD_LOGIN_PROVIDER = "auth-username-password-form";
    private static final String BUILT_IN_BROWSER_FLOW = "browser";
    private static final String LOGIN_FLOW = "browser with recaptcha";

    /** Keycloak's default CSP, plus Google's reCAPTCHA iframe. */
    private static final String CSP_WITH_RECAPTCHA =
            "frame-src 'self' https://www.google.com https://www.recaptcha.net; "
            + "frame-ancestors 'self'; object-src 'none';";

    private static final int MAX_ATTEMPTS = 40;
    private static final long RETRY_DELAY_MS = 15_000;

    private final KeycloakAdminClient keycloak;
    private final CaptchaService captcha;

    public KeycloakCaptchaSync(KeycloakAdminClient keycloak, CaptchaService captcha) {
        this.keycloak = keycloak;
        this.captcha  = captcha;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        captcha.logMode();
        Thread worker = new Thread(this::runWhenKeycloakIsUp, "keycloak-captcha-sync");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWhenKeycloakIsUp() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                sync(keycloak.adminToken());
                return;
            } catch (ExternalServiceException e) {
                log.info("reCAPTCHA sync: Keycloak not ready yet (attempt {}/{})", attempt, MAX_ATTEMPTS);
            } catch (RuntimeException e) {
                log.warn("reCAPTCHA sync failed: {}", e.getMessage());
                return;
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("reCAPTCHA sync: gave up waiting for Keycloak");
    }

    private void sync(String token) {
        boolean on = captcha.isEnabled();
        Map<String, Object> realm = keycloak.getRealm(token);
        if (on) allowGoogleFrames(token, realm);
        syncRegistration(token, realm, on);
        syncLogin(token, realm, on);
    }

    // ── register page ────────────────────────────────────────────────────

    private void syncRegistration(String token, Map<String, Object> realm, boolean on) {
        String flow = realm.get("registrationFlow") instanceof String s && !s.isBlank() ? s : "registration";

        Map<String, Object> execution = keycloak.flowExecutions(token, flow).stream()
                .filter(e -> RECAPTCHA_PROVIDER.equals(e.get("providerId")))
                .findFirst().orElse(null);
        if (execution == null) {
            log.warn("reCAPTCHA sync: the '{}' registration flow has no reCAPTCHA step. "
                    + "Add 'reCAPTCHA' to it in the Keycloak admin console.", flow);
            return;
        }

        if (on) writeKeys(token, execution);

        String wanted = on ? "REQUIRED" : "DISABLED";
        if (!wanted.equals(execution.get("requirement"))) {
            Map<String, Object> update = new LinkedHashMap<>(execution);
            update.put("requirement", wanted);
            keycloak.updateFlowExecution(token, flow, update);
        }
        log.info("reCAPTCHA sync: register page CAPTCHA is {} (flow '{}')", on ? "ON" : "OFF", flow);
    }

    // ── login page ───────────────────────────────────────────────────────

    private void syncLogin(String token, Map<String, Object> realm, boolean on) {
        if (!keycloak.authenticatorProviderIds(token).contains(LOGIN_PROVIDER)) {
            log.warn("reCAPTCHA sync: the login plugin is not installed in Keycloak, so the LOGIN page has "
                    + "no CAPTCHA. Rebuild Keycloak: docker compose up -d --build keycloak");
            return;
        }
        String current = realm.get("browserFlow") instanceof String s ? s : BUILT_IN_BROWSER_FLOW;
        if (!current.equals(BUILT_IN_BROWSER_FLOW) && !current.equals(LOGIN_FLOW)) {
            log.warn("reCAPTCHA sync: realm uses a custom login flow '{}'; not changing it. "
                    + "Replace its 'Username Password Form' with '{}' to add CAPTCHA.", current, LOGIN_PROVIDER);
            return;
        }

        if (!keycloak.flowAliases(token).contains(LOGIN_FLOW)) {
            keycloak.copyFlow(token, BUILT_IN_BROWSER_FLOW, LOGIN_FLOW);
            log.info("reCAPTCHA sync: created login flow '{}'", LOGIN_FLOW);
        }
        useRecaptchaForm(token);

        if (!LOGIN_FLOW.equals(current)) {
            keycloak.updateRealm(token, Map.of("browserFlow", LOGIN_FLOW));
        }
        log.info("reCAPTCHA sync: login page CAPTCHA is {} (flow '{}')", on ? "ON" : "OFF", LOGIN_FLOW);
    }

    /** In LOGIN_FLOW, swaps the standard username/password form for ours, in the same place. */
    private void useRecaptchaForm(String token) {
        List<Map<String, Object>> executions = keycloak.flowExecutions(token, LOGIN_FLOW);
        if (executions.stream().noneMatch(e -> LOGIN_PROVIDER.equals(e.get("providerId")))) {
            int i = indexOf(executions, STANDARD_LOGIN_PROVIDER);
            if (i < 0) {
                log.warn("reCAPTCHA sync: no username/password step in '{}'. Delete that flow in the Keycloak "
                        + "admin console (Authentication) and restart the backend to recreate it.", LOGIN_FLOW);
                return;
            }
            String parent = parentFlowAlias(executions, i);
            keycloak.deleteExecution(token, String.valueOf(executions.get(i).get("id")));
            keycloak.addExecution(token, parent, LOGIN_PROVIDER);
            executions = keycloak.flowExecutions(token, LOGIN_FLOW);
        }

        int i = indexOf(executions, LOGIN_PROVIDER);
        Map<String, Object> ours = executions.get(i);
        if (!"REQUIRED".equals(ours.get("requirement"))) {
            Map<String, Object> update = new LinkedHashMap<>(ours);
            update.put("requirement", "REQUIRED");
            keycloak.updateFlowExecution(token, LOGIN_FLOW, update);
        }
        // A new step is added last (after the OTP sub-flow); move it back to first place
        String id = String.valueOf(ours.get("id"));
        for (int moves = 0; moves < 10 && asInt(ours.get("index")) > 0; moves++) {
            keycloak.raiseExecutionPriority(token, id);
            executions = keycloak.flowExecutions(token, LOGIN_FLOW);
            ours = executions.get(indexOf(executions, LOGIN_PROVIDER));
        }
    }

    private static int indexOf(List<Map<String, Object>> executions, String providerId) {
        for (int i = 0; i < executions.size(); i++) {
            if (providerId.equals(executions.get(i).get("providerId"))) return i;
        }
        return -1;
    }

    /** Executions are listed depth-first; the parent is the closest sub-flow one level up. */
    private static String parentFlowAlias(List<Map<String, Object>> executions, int index) {
        int level = asInt(executions.get(index).get("level"));
        for (int i = index - 1; i >= 0 && level > 0; i--) {
            Map<String, Object> e = executions.get(i);
            if (Boolean.TRUE.equals(e.get("authenticationFlow")) && asInt(e.get("level")) == level - 1) {
                return String.valueOf(e.get("displayName"));   // a sub-flow's display name is its alias
            }
        }
        return LOGIN_FLOW;
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    // ── shared ───────────────────────────────────────────────────────────

    private void writeKeys(String token, Map<String, Object> execution) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("site.key", captcha.siteKey());
        values.put("secret", captcha.secretKey());
        values.put("useRecaptchaNet", "false");

        Object configId = execution.get("authenticationConfig");
        if (configId != null) {
            Map<String, Object> existing = new LinkedHashMap<>(
                    keycloak.getAuthenticatorConfig(token, String.valueOf(configId)));
            Map<String, Object> config = new LinkedHashMap<>();
            if (existing.get("config") instanceof Map<?, ?> old) {
                old.forEach((k, v) -> config.put(String.valueOf(k), v));
            }
            config.putAll(values);
            existing.put("config", config);
            keycloak.updateAuthenticatorConfig(token, String.valueOf(configId), existing);
        } else {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("alias", CONFIG_ALIAS);
            created.put("config", values);
            keycloak.createExecutionConfig(token, String.valueOf(execution.get("id")), created);
        }
    }

    /** Keycloak's default CSP (frame-src 'self') blocks the Google widget iframe. */
    private void allowGoogleFrames(String token, Map<String, Object> realm) {
        Map<String, Object> headers = new LinkedHashMap<>();
        if (realm.get("browserSecurityHeaders") instanceof Map<?, ?> current) {
            current.forEach((k, v) -> headers.put(String.valueOf(k), v));
        }
        Object csp = headers.get("contentSecurityPolicy");
        if (csp instanceof String s && s.contains("https://www.google.com")) return;

        headers.put("contentSecurityPolicy", CSP_WITH_RECAPTCHA);
        keycloak.updateRealm(token, Map.of("browserSecurityHeaders", headers));
        log.info("reCAPTCHA sync: allowed Google's reCAPTCHA frame in Keycloak's Content-Security-Policy");
    }
}
