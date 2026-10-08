package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GET  /api/captcha/config  — public: { enabled, siteKey } for the browser widget
 * POST /api/captcha/verify  — checks a token on its own (testing / Postman)
 *
 * Protected actions (e.g. PUT /api/customers/me) do NOT rely on /verify: they
 * send the token in the X-Captcha-Token header and check it themselves, so the
 * CAPTCHA cannot be skipped by calling the action directly.
 *
 * The secret key is never returned to the client.
 */
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /** The frontend reads this to decide whether to show the widget. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "enabled", captchaService.isEnabled(),
                "siteKey", captchaService.siteKey());
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String token = body.get("token");
        captchaService.validate(token, req.getRemoteAddr());
        return Map.of("valid", true, "message", "CAPTCHA verified");
    }
}
