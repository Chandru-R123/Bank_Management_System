package com.chandru.keycloak.recaptcha;

import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Google reCAPTCHA v2 settings and token check.
 *
 * Reads the same variables as the Spring Boot backend (from the project's .env,
 * passed to the Keycloak container by docker-compose.yml):
 *   CAPTCHA_SITE_KEY, CAPTCHA_SECRET_KEY, CAPTCHA_ENABLED (optional, default true)
 *
 * CAPTCHA is on only when both keys are set, so login keeps working without keys.
 */
final class Recaptcha {

    private static final Logger log = Logger.getLogger(Recaptcha.class);

    static final String RESPONSE_FIELD = "g-recaptcha-response";

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    /** Google's reply is pretty-printed ("success": true), so allow whitespace. */
    private static final Pattern SUCCESS = Pattern.compile("\"success\"\\s*:\\s*true");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Recaptcha() {
    }

    static boolean enabled() {
        return !"false".equalsIgnoreCase(env("CAPTCHA_ENABLED"))
                && !env("CAPTCHA_SITE_KEY").isEmpty()
                && !env("CAPTCHA_SECRET_KEY").isEmpty();
    }

    /** True when Google accepts the token. Any error counts as a failed check. */
    static boolean verify(String token) {
        if (token == null || token.isBlank()) return false;
        String body = "secret=" + encode(env("CAPTCHA_SECRET_KEY")) + "&response=" + encode(token.trim());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VERIFY_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() == 200 && SUCCESS.matcher(response.body()).find();
            if (!ok) {
                log.warnf("reCAPTCHA rejected a login token: HTTP %d %s",
                        response.statusCode(), response.body().replaceAll("\\s+", " "));
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.errorf("reCAPTCHA verification call failed: %s", e.getMessage());
            return false;
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
