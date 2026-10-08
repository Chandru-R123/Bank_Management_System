package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload a MAKER sends to POST /api/transaction-requests.
 *
 * For DEPOSIT  : fromAccountId (the account to credit), amount, description
 * For WITHDRAW : fromAccountId (the account to debit), amount, description
 * For TRANSFER : fromAccountId (source), toAccountId OR toAccountNumber, amount, description
 */
public record CreateTransactionRequestDto(

        @NotBlank(message = "requestType is required (DEPOSIT, WITHDRAW, or TRANSFER)")
        String requestType,

        @NotNull(message = "fromAccountId is required")
        Long fromAccountId,

        /** Required for TRANSFER only. Supply either id or number, not both. */
        Long toAccountId,
        String toAccountNumber,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        String description,

        /** Optional MAKER remarks recorded on the request. */
        String remarks
) {}
