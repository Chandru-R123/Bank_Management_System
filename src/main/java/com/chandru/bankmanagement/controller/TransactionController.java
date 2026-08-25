package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.service.TransactionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ADMIN → Get all transactions
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/transactions")
    public List<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    // ADMIN or CUSTOMER → Get transactions by account
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> getTransactionsByAccount(
            @PathVariable Long id,
            Authentication authentication) {

        return transactionService.getTransactionsByAccountForUser(
                id,
                authentication.getName()
        );
    }

    // ADMIN → Get transaction by ID
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/transactions/{id}")
    public TransactionResponse getTransactionById(
            @PathVariable Long id) {

        return transactionService.getTransactionById(id);
    }
}
