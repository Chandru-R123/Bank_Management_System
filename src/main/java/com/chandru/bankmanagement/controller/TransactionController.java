package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.service.TransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Get All Transactions
    @GetMapping("/transactions")
    public List<TransactionResponse> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    // Get Transactions By Account
    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> getTransactionsByAccount(
            @PathVariable Long id) {

        return transactionService.getTransactionsByAccount(id);
    }
}