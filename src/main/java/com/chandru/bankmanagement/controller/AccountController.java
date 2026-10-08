package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AccountLookupResponse;
import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.CreateTransactionRequestDto;
import com.chandru.bankmanagement.dto.TransactionRequest;
import com.chandru.bankmanagement.dto.TransactionRequestResponse;
import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.dto.TransferRequest;
import com.chandru.bankmanagement.security.Roles;
import com.chandru.bankmanagement.security.SecurityUtils;
import com.chandru.bankmanagement.service.AccountService;
import com.chandru.bankmanagement.service.TransactionRequestService;
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
 *   MAKER    — submit Maker–Checker requests (deposit/withdraw/transfer go to request queue)
 *   EMPLOYEE / CHECKER — view all accounts
 *   CUSTOMER — own accounts only; direct deposit/withdraw/transfer on own accounts
 *
 * Maker–Checker routing:
 *   When a MAKER (not ADMIN) calls deposit/withdraw/transfer, the request is
 *   routed to POST /api/transaction-requests internally — balance is NOT changed.
 *   ADMIN calls these endpoints directly (existing behaviour preserved).
 *   CUSTOMER calls these endpoints directly on own accounts (existing behaviour).
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService             accountService;
    private final TransactionRequestService  requestService;

    public AccountController(AccountService accountService,
                             TransactionRequestService requestService) {
        this.accountService = accountService;
        this.requestService = requestService;
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
    //   ADMIN   → direct execution (existing behaviour)
    //   MAKER   → creates a PENDING_APPROVAL Maker–Checker request
    //   CUSTOMER → direct execution on own account (existing behaviour)

    @PreAuthorize(Roles.TRANSACTORS)
    @PostMapping("/{id}/deposit")
    public Object deposit(@PathVariable Long id,
                          @Valid @RequestBody TransactionRequest request,
                          @AuthenticationPrincipal Jwt jwt) {
        var actor = SecurityUtils.actor(jwt);
        if (actor.isPureMaker()) {
            // Route MAKER through Maker–Checker workflow
            return requestService.create(new CreateTransactionRequestDto(
                    "DEPOSIT", id, null, null,
                    request.getAmount(), request.getDescription(), null), actor);
        }
        return accountService.deposit(id, request.getAmount(),
                request.getDescription(), actor);
    }

    // ── withdraw ───────────────────────────────────────────────────────

    @PreAuthorize(Roles.TRANSACTORS)
    @PostMapping("/{id}/withdraw")
    public Object withdraw(@PathVariable Long id,
                           @Valid @RequestBody TransactionRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        var actor = SecurityUtils.actor(jwt);
        if (actor.isPureMaker()) {
            return requestService.create(new CreateTransactionRequestDto(
                    "WITHDRAW", id, null, null,
                    request.getAmount(), request.getDescription(), null), actor);
        }
        return accountService.withdraw(id, request.getAmount(),
                request.getDescription(), actor);
    }

    // ── transfer ───────────────────────────────────────────────────────

    @PreAuthorize(Roles.TRANSACTORS)
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public Object transfer(@Valid @RequestBody TransferRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        var actor = SecurityUtils.actor(jwt);
        if (actor.isPureMaker()) {
            return requestService.create(new CreateTransactionRequestDto(
                    "TRANSFER",
                    request.getFromAccountId(),
                    request.getToAccountId(),
                    request.getToAccountNumber(),
                    request.getAmount(),
                    request.getDescription(),
                    null), actor);
        }
        return accountService.transferMoney(request, actor);
    }
}
