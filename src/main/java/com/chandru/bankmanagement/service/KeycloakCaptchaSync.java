package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puts the reCAPTCHA v2 keys from the environment onto Keycloak's
 * registration page, so they never have to be committed in the realm file.
 *
 * Keycloak only reads the realm file when its volume is first created, so
 * this runs on every backend start (in the background, waiting for Keycloak):
 *   1. finds the "reCAPTCHA" step in the realm's registration flow
 *   2. writes CAPTCHA_SITE_KEY / CAPTCHA_SECRET_KEY into its config
 *   3. sets the step to REQUIRED (or DISABLED when CAPTCHA is off, so
 *      registration still works without keys)
 *   4. lets the page load Google's widget (Content-Security-Policy frame-src)
 */
@Component
public class KeycloakCaptchaSync {

    private static final Logger log = LoggerFactory.getLogger(KeycloakCaptchaSync.class);

    /** Keycloak's reCAPTCHA v2 form action. */
    private static final String RECAPTCHA_PROVIDER = "registration-recaptcha-action";
    private static final String CONFIG_ALIAS = "bank-recaptcha";

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
        String flow = realm.get("registrationFlow") instanceof String s && !s.isBlank() ? s : "registration";

        Map<String, Object> execution = keycloak.flowExecutions(token, flow).stream()
                .filter(e -> RECAPTCHA_PROVIDER.equals(e.get("providerId")))
                .findFirst().orElse(null);
        if (execution == null) {
            log.warn("reCAPTCHA sync: the '{}' registration flow has no reCAPTCHA step. "
                    + "Add 'reCAPTCHA' to it in the Keycloak admin console.", flow);
            return;
        }

        if (on) {
            writeKeys(token, execution);
            allowGoogleFrames(token, realm);
        }

        String wanted = on ? "REQUIRED" : "DISABLED";
        if (!wanted.equals(execution.get("requirement"))) {
            Map<String, Object> update = new LinkedHashMap<>(execution);
            update.put("requirement", wanted);
            keycloak.updateFlowExecution(token, flow, update);
        }
        log.info("reCAPTCHA sync: registration page CAPTCHA is {} (flow '{}')", on ? "ON" : "OFF", flow);
    }

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
