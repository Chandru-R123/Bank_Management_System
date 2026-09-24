package com.chandru.bankmanagement.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public, unauthenticated endpoints.
 *
 *   GET /health, /api/health  — liveness + database connectivity
 *   GET /api/info             — service metadata
 *
 * Through the gateway use /api/health (NGINX answers /health itself).
 */
@RestController
public class InfoController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name:bank-management}")
    private String applicationName;

    public InfoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        String db;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            db = "UP";
        } catch (Exception e) {
            db = "DOWN";
        }
        body.put("status", "UP".equals(db) ? "UP" : "DEGRADED");
        body.put("database", db);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status("UP".equals(db) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    @GetMapping("/api/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", applicationName);
        body.put("description", "State Bank — Mini Banking / Open Banking Consent Management System");
        body.put("version", "1.0.0");
        body.put("bank", Map.of("name", "State Bank", "branch", "Coimbatore", "state", "Tamil Nadu"));
        body.put("java", System.getProperty("java.version"));
        body.put("modules", List.of(
                "customers", "accounts", "transactions", "beneficiaries", "consents", "open-banking"));
        body.put("auth", "Keycloak (OAuth2 / JWT bearer)");
        return body;
    }
}
