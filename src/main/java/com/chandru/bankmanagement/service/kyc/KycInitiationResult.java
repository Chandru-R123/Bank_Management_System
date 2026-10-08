package com.chandru.bankmanagement.service.kyc;

/**
 * Result returned when KYC verification is initiated.
 *
 * @param redirectUrl For DIGILOCKER: the OAuth URL the customer must visit.
 *                    Null for MANUAL and MOCK.
 * @param message     Human-readable next-step instruction shown to the customer.
 * @param providerRef A reference identifier from the provider (e.g. DigiLocker transaction id).
 *                    Null for MANUAL.
 */
public record KycInitiationResult(
        String redirectUrl,
        String message,
        String providerRef
) {}
