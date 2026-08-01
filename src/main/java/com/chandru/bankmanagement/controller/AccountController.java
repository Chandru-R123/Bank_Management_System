package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.AccountRequest;
import com.chandru.bankmanagement.dto.AccountResponse;
import com.chandru.bankmanagement.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.chandru.bankmanagement.dto.TransactionRequest;
import com.chandru.bankmanagement.dto.TransferRequest;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    // Constructor Injection
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // Create Account
    @PostMapping
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        return accountService.createAccount(request);
    }

    // Get All Accounts
    @GetMapping
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    // Get Account By ID
    @GetMapping("/{id}")
    public AccountResponse getAccountById(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    // Update Account
    @PutMapping("/{id}")
    public AccountResponse updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountRequest request) {

        return accountService.updateAccount(id, request);
    }

    // Delete Account
    @DeleteMapping("/{id}")
    public String deleteAccount(@PathVariable Long id) {

        accountService.deleteAccount(id);

        return "Account deleted successfully";
    }
    @PostMapping("/{id}/withdraw")
    public AccountResponse withdrawMoney(
            @PathVariable Long id,
            @RequestBody TransactionRequest request) {

        return accountService.withdraw(id, request.getAmount());
    }
    @PostMapping("/{id}/deposit")
    public AccountResponse depositMoney(
            @PathVariable Long id,
            @RequestBody TransactionRequest request) {

        return accountService.deposit(id, request.getAmount());
    }
    @PostMapping("/transfer")
    public String transferMoney(@RequestBody TransferRequest request) {

        accountService.transferMoney(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount());

        return "Money transferred successfully";
    }
}