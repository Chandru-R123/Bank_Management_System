package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AccountLookupResponse;
import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.TransactionRequest;
import com.chandru.bankmanagement.dto.TransactionRequestResponse;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.dto.TransferRequest;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Roles:
 *   ADMIN    — everything: open, edit, freeze / unfreeze / close; direct deposit/withdraw
 *   MAKER    — view all accounts; cannot move money here. Deposits, withdrawals
 *              and transfers are raised at POST /api/transaction-requests and
 *              run only after a CHECKER approves them.
 *   EMPLOYEE / CHECKER — view all accounts
 *   CUSTOMER — own accounts only; direct deposit/withdraw/transfer on own accounts
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ── ADMIN: open account ────────────────────────────────────────────

    @PreAuthorize(Roles.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return accountService.createAccount(request, SecurityUtils.actor(jwt));
    }

    // ── CUSTOMER: my accounts ──────────────────────────────────────────

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public List<AccountResponse> getMyAccounts(@AuthenticationPrincipal Jwt jwt) {
        return accountService.getAccountsForSub(jwt.getSubject());
    }

    // ── ANY ROLE: verify a beneficiary before transferring ─────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER', 'CUSTOMER')")
    @GetMapping("/lookup")
    public AccountLookupResponse lookup(@RequestParam("number") String number) {
        return accountService.lookup(number);
    }

    // ── STAFF: all accounts ────────────────────────────────────────────

    @PreAuthorize(Roles.STAFF)
    @GetMapping
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    // ── STAFF: one account ─────────────────────────────────────────────

    @PreAuthorize(Roles.STAFF)
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

    // ── deposit ────────────────────────────────────────────────────────
    //   ADMIN    → direct execution
    //   CUSTOMER → direct execution on own account
    //   MAKER    → not allowed here: raise a request at /api/transaction-requests

    @PreAuthorize(Roles.CUSTOMER_TRANSACTORS)
    @PostMapping("/{id}/deposit")
    public Object deposit(@PathVariable Long id,
                          @Valid @RequestBody TransactionRequest request,
                          @AuthenticationPrincipal Jwt jwt) {
        return accountService.deposit(id, request.getAmount(),
                request.getDescription(), SecurityUtils.actor(jwt));
    }

    // ── withdraw ───────────────────────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER_TRANSACTORS)
    @PostMapping("/{id}/withdraw")
    public Object withdraw(@PathVariable Long id,
                           @Valid @RequestBody TransactionRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return accountService.withdraw(id, request.getAmount(),
                request.getDescription(), SecurityUtils.actor(jwt));
    }

    // ── transfer ───────────────────────────────────────────────────────

    @PreAuthorize(Roles.CUSTOMER_TRANSACTORS)
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public Object transfer(@Valid @RequestBody TransferRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        return accountService.transferMoney(request, SecurityUtils.actor(jwt));
    }
}
