package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.entity.User;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            UserRepository userRepository) {

        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    // Get All Transactions
    // ADMIN ONLY
    // ==========================================

    public List<TransactionResponse> getAllTransactions() {

        return transactionRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // ==========================================
    // Get Transactions By Account
    // Internal method
    // ==========================================

    public List<TransactionResponse> getTransactionsByAccount(
            Long accountId) {

        return transactionRepository
                .findByAccountAccountId(accountId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // ==========================================
    // Get Transactions By Account For User
    // ADMIN → Any account
    // CUSTOMER → Own account only
    // ==========================================

    public List<TransactionResponse> getTransactionsByAccountForUser(
            Long accountId,
            String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"));

        // ==========================================
        // ADMIN
        // ==========================================

        if ("ADMIN".equalsIgnoreCase(user.getRole())) {

            return getTransactionsByAccount(accountId);
        }

        // ==========================================
        // CUSTOMER
        // ==========================================

        if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {

            // Customer must be linked to a customer record
            if (user.getCustomer() == null) {

                throw new UnauthorizedAccessException(
                        "Customer is not linked to this user");
            }

            Long customerId =
                    user.getCustomer().getCustomerId();

            // Check ownership
            accountRepository
                    .findByAccountIdAndCustomerCustomerId(
                            accountId,
                            customerId)
                    .orElseThrow(() ->
                            new UnauthorizedAccessException(
                                    "You are not authorized to access this account"));

            // Ownership confirmed
            return getTransactionsByAccount(accountId);
        }

        // ==========================================
        // Unknown role
        // ==========================================

        throw new UnauthorizedAccessException(
                "You are not authorized to access this resource");
    }

    // ==========================================
    // Get Transaction By ID
    // ==========================================

    public TransactionResponse getTransactionById(Long id) {

        Transaction transaction =
                transactionRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Transaction not found"));

        return convertToResponse(transaction);
    }

    // ==========================================
    // Convert Entity → Response DTO
    // ==========================================

    private TransactionResponse convertToResponse(
            Transaction transaction) {

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getAccount().getAccountNumber()
        );
    }
}