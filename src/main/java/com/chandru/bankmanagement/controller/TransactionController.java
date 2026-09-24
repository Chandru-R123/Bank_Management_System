package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.PostTransactionRequest;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.exception.BusinessRuleException;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.AccountService;
import com.chandru.bankmanagement.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService     accountService;

    public TransactionController(TransactionService transactionService,
                                 AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService     = accountService;
    }

    // ── MAKER / ADMIN (any account) or CUSTOMER (own): post a transaction ──

    @PreAuthorize(Roles.TRANSACTORS)
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse post(@Valid @RequestBody PostTransactionRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        if (request.accountId() == null) {
            throw new BusinessRuleException("accountId is required");
        }
        return accountService.postTransaction(request.accountId(), request.type(),
                request.amount(), request.description(), SecurityUtils.actor(jwt));
    }

    @PreAuthorize(Roles.TRANSACTORS)
    @PostMapping("/accounts/{id}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse postForAccount(@PathVariable Long id,
                                              @Valid @RequestBody PostTransactionRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return accountService.postTransaction(id, request.type(),
                request.amount(), request.description(), SecurityUtils.actor(jwt));
    }

    // ── STAFF: all transactions (newest first) ─────────────────────────

    @PreAuthorize(Roles.STAFF)
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

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER', 'CUSTOMER')")
    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> getByAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        // Staff → no ownership check; CUSTOMER → must own the account
        return transactionService.getTransactionsByAccountForUser(id, SecurityUtils.actor(jwt));
    }

    // ── STAFF: one transaction by id ───────────────────────────────────

    @PreAuthorize(Roles.STAFF)
    @GetMapping("/transactions/{id}")
    public TransactionResponse getById(@PathVariable Long id) {
        return transactionService.getTransactionById(id);
    }
}
