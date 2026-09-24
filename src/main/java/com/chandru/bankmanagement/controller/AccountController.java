package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AccountLookupResponse;
import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.TransactionRequest;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.dto.TransferRequest;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Roles:
 *   ADMIN    — everything, including freeze / unfreeze / close
 *   EMPLOYEE — view all accounts, open accounts, deposit / withdraw / transfer
 *   CUSTOMER — own accounts only
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ── STAFF: open account ────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @PostMapping
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return accountService.createAccount(request, SecurityUtils.actor(jwt));
    }

    // ── CUSTOMER: my accounts ──────────────────────────────────────────

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public List<AccountResponse> getMyAccounts(@AuthenticationPrincipal Jwt jwt) {
        // jwt.getSubject() == Keycloak user UUID ("sub" claim)
        return accountService.getAccountsForSub(jwt.getSubject());
    }

    // ── ANY ROLE: verify a beneficiary before transferring ─────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    @GetMapping("/lookup")
    public AccountLookupResponse lookup(@RequestParam("number") String number) {
        return accountService.lookup(number);
    }

    // ── STAFF: all accounts ────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    // ── STAFF: one account ─────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/{id}")
    public AccountResponse getAccountById(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    // ── ADMIN: update type / owner ─────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public AccountResponse updateAccount(@PathVariable Long id,
                                         @Valid @RequestBody AccountRequest request) {
        return accountService.updateAccount(id, request);
    }

    // ── ADMIN: close (DELETE kept for backwards compatibility) ─────────

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public AccountResponse deleteAccount(@PathVariable Long id,
                                         @AuthenticationPrincipal Jwt jwt) {
        return accountService.closeAccount(id, SecurityUtils.actor(jwt));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/close")
    public AccountResponse closeAccount(@PathVariable Long id,
                                        @AuthenticationPrincipal Jwt jwt) {
        return accountService.closeAccount(id, SecurityUtils.actor(jwt));
    }

    // ── ADMIN: freeze / unfreeze ───────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/freeze")
    public AccountResponse freeze(@PathVariable Long id,
                                  @AuthenticationPrincipal Jwt jwt) {
        return accountService.freeze(id, SecurityUtils.actor(jwt));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/unfreeze")
    public AccountResponse unfreeze(@PathVariable Long id,
                                    @AuthenticationPrincipal Jwt jwt) {
        return accountService.unfreeze(id, SecurityUtils.actor(jwt));
    }

    // ── ALL ROLES: deposit ─────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    @PostMapping("/{id}/deposit")
    public AccountResponse deposit(@PathVariable Long id,
                                   @Valid @RequestBody TransactionRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        // Staff have no ownership restriction; CUSTOMER is scoped to own account
        return accountService.deposit(id, request.getAmount(),
                request.getDescription(), SecurityUtils.actor(jwt));
    }

    // ── ALL ROLES: withdraw ────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    @PostMapping("/{id}/withdraw")
    public AccountResponse withdraw(@PathVariable Long id,
                                    @Valid @RequestBody TransactionRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return accountService.withdraw(id, request.getAmount(),
                request.getDescription(), SecurityUtils.actor(jwt));
    }

    // ── ALL ROLES: transfer ────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    @PostMapping("/transfer")
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return accountService.transferMoney(request, SecurityUtils.actor(jwt));
    }
}
