package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * CAPTCHA validation service.
 *
 * Supports Google reCAPTCHA v2/v3 and hCaptcha (same verify API shape).
 *
 * Configuration (all via environment variables — never hardcoded):
 *   CAPTCHA_ENABLED      true | false  (default: false — dev mode)
 *   CAPTCHA_SECRET_KEY   Your server-side secret key
 *   CAPTCHA_VERIFY_URL   (optional) Override default reCAPTCHA URL for hCaptcha etc.
 *   CAPTCHA_MIN_SCORE    (optional, v3 only) Minimum score 0.0–1.0 (default: 0.5)
 *
 * The CAPTCHA_SITE_KEY is a FRONTEND-only value and is NEVER read by this service.
 *
 * When CAPTCHA_ENABLED=false the validation is a no-op (development/test mode).
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final String DEFAULT_VERIFY_URL =
            "https://www.google.com/recaptcha/api/siteverify";

    @Value("${captcha.enabled:false}")
    private boolean enabled;

    @Value("${captcha.secret-key:}")
    private String secretKey;

    @Value("${captcha.verify-url:" + DEFAULT_VERIFY_URL + "}")
    private String verifyUrl;

    @Value("${captcha.min-score:0.5}")
    private double minScore;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Validates the CAPTCHA token submitted from the client.
     *
     * @param token       The token value from the frontend widget.
     * @param remoteIp    Client IP address (optional, sent to Google for scoring).
     * @throws BusinessRuleException if CAPTCHA is enabled and validation fails.
     */
    public void validate(String token, String remoteIp) {
        if (!enabled) {
            log.debug("CAPTCHA validation skipped (CAPTCHA_ENABLED=false)");
            return;
        }

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "CAPTCHA is enabled but CAPTCHA_SECRET_KEY is not configured. "
                    + "Set the environment variable or disable CAPTCHA with CAPTCHA_ENABLED=false.");
        }

        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("CAPTCHA verification is required");
        }

        try {
            String result = callVerifyApi(token, remoteIp);
            parseCaptchaResult(result);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("CAPTCHA verification API call failed: {}", e.getMessage());
            // If CAPTCHA API is unreachable, fail open in dev but fail closed in prod
            throw new BusinessRuleException(
                    "CAPTCHA verification could not be completed. Please try again.");
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String callVerifyApi(String token, String remoteIp) throws IOException, InterruptedException {
        String body = "secret=" + urlEncode(secretKey)
                + "&response=" + urlEncode(token)
                + (remoteIp != null && !remoteIp.isBlank() ? "&remoteip=" + urlEncode(remoteIp) : "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(verifyUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("CAPTCHA API returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Minimal JSON parsing without pulling in Jackson just for two fields.
     * Handles both reCAPTCHA v2 ({"success":true}) and
     * reCAPTCHA v3 ({"success":true,"score":0.9}).
     */
    private void parseCaptchaResult(String json) {
        boolean success = json.contains("\"success\":true");
        if (!success) {
            log.warn("CAPTCHA validation failed. Response: {}", json);
            throw new BusinessRuleException(
                    "CAPTCHA verification failed. Please complete the CAPTCHA and try again.");
        }
        // For reCAPTCHA v3: check score
        if (json.contains("\"score\":")) {
            double score = extractScore(json);
            if (score < minScore) {
                log.warn("CAPTCHA score {} below minimum {}", score, minScore);
                throw new BusinessRuleException(
                        "CAPTCHA score too low. Please try again or contact support.");
            }
        }
    }

    private double extractScore(String json) {
        try {
            int idx = json.indexOf("\"score\":");
            if (idx < 0) return 1.0;
            String sub = json.substring(idx + 8).trim();
            int end = sub.indexOf(',');
            if (end < 0) end = sub.indexOf('}');
            if (end < 0) return 1.0;
            return Double.parseDouble(sub.substring(0, end).trim());
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }
}
