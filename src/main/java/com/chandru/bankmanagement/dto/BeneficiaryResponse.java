package com.chandru.bankmanagement.dto;

import java.time.LocalDateTime;

public record BeneficiaryResponse(
        Long beneficiaryId,
        String nickname,
        String accountNumber,
        /** Masked name of the account holder, e.g. "Priya V." */
        String holderName,
        String accountType,
        /** False if the payee account is now frozen, closed or a Fixed Deposit. */
        boolean canReceive,
        Long customerId,
        String customerName,
        LocalDateTime createdAt
) {
}
