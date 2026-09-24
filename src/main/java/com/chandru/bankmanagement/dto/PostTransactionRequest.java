package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /api/transactions (accountId required) and
 * POST /api/accounts/{accountId}/transactions (accountId taken from the path).
 */
public record PostTransactionRequest(

        Long accountId,

        @NotBlank(message = "Transaction type is required")
        @Pattern(regexp = "(?i)DEPOSIT|WITHDRAW", message = "Transaction type must be DEPOSIT or WITHDRAW")
        String type,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 140, message = "Remarks must be at most 140 characters")
        String description
) {
}
