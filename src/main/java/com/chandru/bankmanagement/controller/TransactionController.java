package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.service.TransactionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ── ADMIN: all transactions ────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/transactions")
    public List<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    // ── ADMIN + CUSTOMER: transactions for a specific account ──────────

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> getByAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        // ADMIN → null sub (no ownership check)
        // CUSTOMER → keycloakSub enforces ownership
        String sub = hasAdminRole(jwt) ? null : jwt.getSubject();
        return transactionService.getTransactionsByAccountForUser(id, sub);
    }

    // ── ADMIN: one transaction by id ───────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/transactions/{id}")
    public TransactionResponse getById(@PathVariable Long id) {
        return transactionService.getTransactionById(id);
    }

    // ── helper ─────────────────────────────────────────────────────────

    private boolean hasAdminRole(Jwt jwt) {
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return false;
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles != null && roles.contains("ADMIN");
        } catch (Exception e) {
            return false;
        }
    }
}
