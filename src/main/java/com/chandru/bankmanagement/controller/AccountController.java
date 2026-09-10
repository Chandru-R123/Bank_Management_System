package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.TransactionRequest;
import com.chandru.bankmanagement.dto.TransferRequest;
import com.chandru.bankmanagement.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ── ADMIN: create ──────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        return accountService.createAccount(request);
    }

    // ── CUSTOMER: my accounts ──────────────────────────────────────────

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public List<AccountResponse> getMyAccounts(@AuthenticationPrincipal Jwt jwt) {
        // jwt.getSubject() == Keycloak user UUID ("sub" claim)
        return accountService.getAccountsForSub(jwt.getSubject());
    }

    // ── ADMIN: all accounts ────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    // ── ADMIN: one account ─────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public AccountResponse getAccountById(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    // ── ADMIN: update ──────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public AccountResponse updateAccount(@PathVariable Long id,
                                         @Valid @RequestBody AccountRequest request) {
        return accountService.updateAccount(id, request);
    }

    // ── ADMIN: delete ──────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public String deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return "Account deleted successfully";
    }

    // ── ADMIN + CUSTOMER: deposit ──────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @PostMapping("/{id}/deposit")
    public AccountResponse deposit(@PathVariable Long id,
                                   @RequestBody TransactionRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        // ADMIN has no keycloakSub restriction; CUSTOMER is scoped to own account
        String sub = hasAdminRole(jwt) ? null : jwt.getSubject();
        return accountService.deposit(id, request.getAmount(), sub);
    }

    // ── ADMIN + CUSTOMER: withdraw ─────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @PostMapping("/{id}/withdraw")
    public AccountResponse withdraw(@PathVariable Long id,
                                    @RequestBody TransactionRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        String sub = hasAdminRole(jwt) ? null : jwt.getSubject();
        return accountService.withdraw(id, request.getAmount(), sub);
    }

    // ── ADMIN + CUSTOMER: transfer ─────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @PostMapping("/transfer")
    public String transfer(@RequestBody TransferRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        String sub = hasAdminRole(jwt) ? null : jwt.getSubject();
        accountService.transferMoney(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                sub);
        return "Money transferred successfully";
    }

    // ── helper ─────────────────────────────────────────────────────────

    private boolean hasAdminRole(Jwt jwt) {
        try {
            java.util.Map<String, Object> realmAccess =
                    jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return false;
            @SuppressWarnings("unchecked")
            java.util.List<String> roles =
                    (java.util.List<String>) realmAccess.get("roles");
            return roles != null && roles.contains("ADMIN");
        } catch (Exception e) {
            return false;
        }
    }
}
