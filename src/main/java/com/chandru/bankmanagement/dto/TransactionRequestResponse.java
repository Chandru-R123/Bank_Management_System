package com.chandru.bankmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO returned by all Maker–Checker request endpoints.
 */
public record TransactionRequestResponse(
        Long id,
        String requestRef,
        String requestType,
        String status,

        // From account
        Long fromAccountId,
        String fromAccountNumber,
        String fromAccountType,

        // To account (transfers only)
        Long toAccountId,
        String toAccountNumber,

        BigDecimal amount,
        String description,

        // Maker
        String makerUserId,
        String makerUsername,

        // Checker (null until action taken)
        String checkerUserId,
        String checkerUsername,

        // Timestamps
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt,
        LocalDateTime executedAt,

        // Outcome
        String rejectionReason,
        String remarks,
        Long transactionId,
        BigDecimal balanceAfter
) {}
