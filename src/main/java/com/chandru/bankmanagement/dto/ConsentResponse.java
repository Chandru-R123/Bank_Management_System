package com.chandru.bankmanagement.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentResponse(
        String consentId,
        String status,
        Long customerId,
        String customerName,
        String tppUsername,
        String tppName,
        String purpose,
        List<String> permissions,
        List<SharedAccount> accounts,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime statusUpdatedAt,
        String statusUpdatedBy
) {

    public record SharedAccount(Long accountId, String accountNumber, String accountType) {
    }
}
