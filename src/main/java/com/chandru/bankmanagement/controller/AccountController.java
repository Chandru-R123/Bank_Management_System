package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.dto.TransactionRequest;
import com.chandru.bankmanagement.dto.TransferRequest;
import com.chandru.bankmanagement.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    // Constructor Injection
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // =====================================================
    // CREATE ACCOUNT
    // ADMIN ONLY
    // =====================================================

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public AccountResponse createAccount(
            @Valid @RequestBody AccountRequest request) {

        return accountService.createAccount(request);
    }

    // =====================================================
    // GET ALL ACCOUNTS
    // ADMIN ONLY
    // =====================================================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<AccountResponse> getAllAccounts() {

        return accountService.getAllAccounts();
    }

    // =====================================================
    // GET ACCOUNT BY ID
    // ADMIN ONLY
    // =====================================================

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public AccountResponse getAccountById(
            @PathVariable Long id) {

        return accountService.getAccountById(id);
    }

    // =====================================================
    // UPDATE ACCOUNT
    // ADMIN ONLY
    // =====================================================

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public AccountResponse updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountRequest request) {

        return accountService.updateAccount(id, request);
    }

    // =====================================================
    // DELETE ACCOUNT
    // ADMIN ONLY
    // =====================================================

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public String deleteAccount(
            @PathVariable Long id) {

        accountService.deleteAccount(id);

        return "Account deleted successfully";
    }

    // =====================================================
    // WITHDRAW
    // ADMIN + CUSTOMER
    // =====================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @PostMapping("/{id}/withdraw")
    public AccountResponse withdrawMoney(
            @PathVariable Long id,
            @RequestBody TransactionRequest request,
            Authentication authentication) {

        return accountService.withdraw(
                id,
                request.getAmount(),
                authentication.getName()
        );
    }

    // =====================================================
    // DEPOSIT
    // ADMIN + CUSTOMER
    // =====================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @PostMapping("/{id}/deposit")
    public AccountResponse depositMoney(
            @PathVariable Long id,
            @RequestBody TransactionRequest request,
            Authentication authentication) {

        return accountService.deposit(
                id,
                request.getAmount(),
                authentication.getName()
        );
    }

    // =====================================================
    // TRANSFER
    // ADMIN + CUSTOMER
    // =====================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @PostMapping("/transfer")
    public String transferMoney(
            @RequestBody TransferRequest request,
            Authentication authentication) {

        accountService.transferMoney(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                authentication.getName()
        );

        return "Money transferred successfully";
    }
}

