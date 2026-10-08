package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Returns the CAPTCHA site key for the frontend and
 * provides a server-side verify endpoint.
 *
 * POST /api/captcha/verify  — validates a CAPTCHA token (used by customer registration)
 * GET  /api/captcha/config  — returns { enabled, siteKey } to the frontend
 *
 * The secret key is NEVER returned to the client.
 */
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    @org.springframework.beans.factory.annotation.Value("${captcha.enabled:false}")
    private boolean captchaEnabled;

    /**
     * CAPTCHA_SITE_KEY is the public key shown to users in the browser widget.
     * It is not a secret — it is safe to return it in a public endpoint.
     */
    @org.springframework.beans.factory.annotation.Value("${captcha.site-key:}")
    private String siteKey;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /** Frontend reads this on load to decide whether to show the CAPTCHA widget. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "enabled", captchaEnabled,
                "siteKey", siteKey != null ? siteKey : ""
        );
    }

    /**
     * Validates the CAPTCHA token submitted from a registration or sensitive form.
     * Returns 200 OK on success, 400 Bad Request on failure.
     */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> verify(
            @RequestBody Map<String, String> body,
            HttpServletRequest req) {
        String token    = body.get("token");
        String remoteIp = req.getRemoteAddr();
        captchaService.validate(token, remoteIp);
        return Map.of("valid", true, "message", "CAPTCHA verified");
    }
}
