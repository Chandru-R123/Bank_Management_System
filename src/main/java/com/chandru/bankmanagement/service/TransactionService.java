package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.TransactionResponse;
import com.chandru.bankmanagement.entity.Account;
import com.chandru.bankmanagement.entity.Transaction;
import com.chandru.bankmanagement.exception.AccountNotFoundException;
import com.chandru.bankmanagement.exception.ResourceNotFoundException;
import com.chandru.bankmanagement.exception.UnauthorizedAccessException;
import com.chandru.bankmanagement.repository.AccountRepository;
import com.chandru.bankmanagement.repository.TransactionRepository;
import com.chandru.bankmanagement.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * STAFF    → any account.
 * CUSTOMER → own accounts only (verified via keycloakSub == customer.keycloak_sub).
 * All lists are returned newest first.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository     accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository     = accountRepository;
    }

    // ── STAFF: all transactions ────────────────────────────────────────

    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAllByOrderByTransactionDateDescTransactionIdDesc()
                .stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── CUSTOMER: every transaction across my accounts ─────────────────

    public List<TransactionResponse> getMyTransactions(String keycloakSub) {
        return transactionRepository
                .findByAccountCustomerKeycloakSubOrderByTransactionDateDescTransactionIdDesc(keycloakSub)
                .stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── STAFF + CUSTOMER: by account with ownership enforcement ────────

    public List<TransactionResponse> getTransactionsByAccountForUser(Long accountId, Actor actor) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        if (!actor.staff()) {
            String ownerSub = account.getCustomer() != null
                    ? account.getCustomer().getKeycloakSub() : null;
            if (ownerSub == null || !ownerSub.equals(actor.sub())) {
                throw new UnauthorizedAccessException(
                        "You are not authorised to access this account");
            }
        }

        return transactionRepository
                .findByAccountAccountIdOrderByTransactionDateDescTransactionIdDesc(accountId)
                .stream()
                .map(ResponseMapper::toResponse)
                .toList();
    }

    // ── STAFF: single transaction by id ────────────────────────────────

    public TransactionResponse getTransactionById(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return ResponseMapper.toResponse(tx);
    }
}
