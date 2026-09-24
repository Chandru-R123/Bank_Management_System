package com.chandru.bankmanagement.dto;

import java.math.BigDecimal;

/**
 * Account data returned to a TPP. {@code balance} is null unless the
 * consent includes READ_BALANCES.
 */
public record OpenBankingAccountResponse(
        Long accountId,
        String accountNumber,
        String accountType,
        String status,
        String holderName,
        BigDecimal balance,
        String currency
) {
}
