package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.TransactionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ── STAFF: all transactions (newest first) ─────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/transactions")
    public List<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    // ── CUSTOMER: all transactions across own accounts ─────────────────

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/transactions/my")
    public List<TransactionResponse> getMyTransactions(@AuthenticationPrincipal Jwt jwt) {
        return transactionService.getMyTransactions(jwt.getSubject());
    }

    // ── ALL ROLES: statement for a specific account ────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> getByAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        // Staff → no ownership check; CUSTOMER → must own the account
        return transactionService.getTransactionsByAccountForUser(id, SecurityUtils.actor(jwt));
    }

    // ── STAFF: one transaction by id ───────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/transactions/{id}")
    public TransactionResponse getById(@PathVariable Long id) {
        return transactionService.getTransactionById(id);
    }
}
