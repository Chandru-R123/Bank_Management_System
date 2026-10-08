package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.exception.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Google reCAPTCHA v2 ("I'm not a robot" checkbox) verification.
 *
 * Configuration (environment variables, e.g. in the project's .env file):
 *   CAPTCHA_SITE_KEY     public site key — shown in the browser widget
 *   CAPTCHA_SECRET_KEY   secret key — server side only, never sent to the browser
 *   CAPTCHA_ENABLED      optional; true by default. CAPTCHA is active only when
 *                        it is true AND both keys are set, so local development
 *                        without keys keeps working.
 *
 * The same keys are pushed to Keycloak's registration page by
 * {@link KeycloakCaptchaSync}.
 *
 * Protected app requests send the widget token in the {@value #TOKEN_HEADER} header.
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    public static final String TOKEN_HEADER = "X-Captcha-Token";

    /** Google's reply is pretty-printed ("success": true), so match whitespace too. */
    private static final Pattern SUCCESS = Pattern.compile("\"success\"\\s*:\\s*true");

    @Value("${captcha.enabled:true}")
    private boolean enabledFlag;

    @Value("${captcha.site-key:}")
    private String siteKey;

    @Value("${captcha.secret-key:}")
    private String secretKey;

    @Value("${captcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
    private String verifyUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Logs whether CAPTCHA is active (called once at startup). */
    void logMode() {
        if (isEnabled()) {
            log.info("reCAPTCHA v2 is ON (site key {}…)", siteKey.substring(0, Math.min(8, siteKey.length())));
        } else if (enabledFlag && (hasText(siteKey) || hasText(secretKey))) {
            log.warn("reCAPTCHA is OFF: set BOTH CAPTCHA_SITE_KEY and CAPTCHA_SECRET_KEY to turn it on");
        } else {
            log.info("reCAPTCHA is OFF (no keys configured or CAPTCHA_ENABLED=false)");
        }
    }

    /** True when CAPTCHA is switched on and both keys are configured. */
    public boolean isEnabled() {
        return enabledFlag && hasText(siteKey) && hasText(secretKey);
    }

    /** Public site key for the browser widget ("" when CAPTCHA is off). */
    public String siteKey() {
        return isEnabled() ? siteKey.trim() : "";
    }

    String secretKey() {
        return secretKey == null ? "" : secretKey.trim();
    }

    /** Validates the token sent in the {@value #TOKEN_HEADER} header of an app request. */
    public void validate(HttpServletRequest request) {
        validate(request.getHeader(TOKEN_HEADER), clientIp(request));
    }

    /**
     * Validates a widget token with Google. No-op when CAPTCHA is off.
     *
     * @throws BusinessRuleException (400) when the token is missing, invalid or expired
     */
    public void validate(String token, String remoteIp) {
        if (!isEnabled()) return;

        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("Please tick \"I'm not a robot\" to continue");
        }

        String result;
        try {
            result = callVerifyApi(token.trim(), remoteIp);
        } catch (IOException e) {
            log.error("reCAPTCHA verification call failed: {}", e.getMessage());
            throw new BusinessRuleException("CAPTCHA verification could not be completed. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("CAPTCHA verification could not be completed. Please try again.");
        }

        if (!SUCCESS.matcher(result).find()) {
            // e.g. "timeout-or-duplicate" (token used twice / older than 2 minutes), "invalid-input-secret"
            log.warn("reCAPTCHA rejected the token: {}", result.replaceAll("\\s+", " "));
            throw new BusinessRuleException("CAPTCHA check failed or expired. Please tick \"I'm not a robot\" again.");
        }
    }

    // ── internal ─────────────────────────────────────────────────────────

    private String callVerifyApi(String token, String remoteIp) throws IOException, InterruptedException {
        String body = "secret=" + urlEncode(secretKey())
                + "&response=" + urlEncode(token)
                + (hasText(remoteIp) ? "&remoteip=" + urlEncode(remoteIp) : "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(verifyUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("reCAPTCHA API returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Browser IP as seen by NGINX (the backend itself only sees the gateway). */
    private static String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (hasText(realIp)) return realIp.trim();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (hasText(forwarded)) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
