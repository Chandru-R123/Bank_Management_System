package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // Get All Transactions
    public List<TransactionResponse> getAllTransactions() {

        return transactionRepository.findAll()
                .stream()
                .map(transaction -> new TransactionResponse(
                        transaction.getTransactionId(),
                        transaction.getTransactionType(),
                        transaction.getAmount(),
                        transaction.getTransactionDate()
                ))
                .collect(Collectors.toList());
    }

    // Get Transactions By Account
    public List<TransactionResponse> getTransactionsByAccount(Long accountId) {

        return transactionRepository.findByAccountAccountId(accountId)
                .stream()
                .map(transaction -> new TransactionResponse(
                        transaction.getTransactionId(),
                        transaction.getTransactionType(),
                        transaction.getAmount(),
                        transaction.getTransactionDate()
                ))
                .collect(Collectors.toList());
    }
}