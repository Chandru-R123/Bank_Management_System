package com.chandru.bankmanagement.dto;

/**
 * Beneficiary verification — lets a customer confirm who they are sending
 * money to without exposing the full name of another customer.
 */
public record AccountLookupResponse(
        String accountNumber,
        String holderName,
        String accountType,
        boolean canReceive
) {
}
