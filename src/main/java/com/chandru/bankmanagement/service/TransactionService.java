package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ADMIN  → any account.
 * CUSTOMER → own accounts only (verified via keycloakSub == customer.keycloak_sub).
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository     accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository     = accountRepository;
    }

    // ── ADMIN: all transactions ────────────────────────────────────────

    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── ADMIN: by account (no ownership check) ─────────────────────────

    public List<TransactionResponse> getTransactionsByAccount(Long accountId) {
        return transactionRepository.findByAccountAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── ADMIN + CUSTOMER: by account with ownership enforcement ────────

    /**
     * @param keycloakSub  null → caller is ADMIN (skip ownership check)
     *                     non-null → caller is CUSTOMER; account must belong to them
     */
    public List<TransactionResponse> getTransactionsByAccountForUser(
            Long accountId, String keycloakSub) {

        if (keycloakSub != null) {
            // Verify the account belongs to this customer
            accountRepository
                    .findByAccountIdAndCustomerKeycloakSub(accountId, keycloakSub)
                    .orElseThrow(() -> new UnauthorizedAccessException(
                            "You are not authorised to access this account"));
        }

        return getTransactionsByAccount(accountId);
    }

    // ── ADMIN: single transaction by id ───────────────────────────────

    public TransactionResponse getTransactionById(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return toResponse(tx);
    }

    // ── mapping ────────────────────────────────────────────────────────

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getTransactionId(),
                tx.getTransactionType(),
                tx.getAmount(),
                tx.getTransactionDate(),
                tx.getAccount().getAccountNumber()
        );
    }
}
