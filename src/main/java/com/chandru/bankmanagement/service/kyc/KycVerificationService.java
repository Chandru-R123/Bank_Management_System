package com.chandru.bankmanagement.service.kyc;

import com.chandru.bankmanagement.entity.KycRecord;

/**
 * Abstraction over the three supported KYC verification methods.
 *
 * Implementations:
 *   ManualKycVerificationService  — documents uploaded and reviewed by employee/ADMIN
 *   DigiLockerKycVerificationService — real UMANG/DigiLocker API (requires credentials)
 *   MockKycVerificationService    — development/test stub (clearly labelled)
 *
 * The active implementation is selected by the KYC_PROVIDER environment variable.
 * Configuration must NOT hardcode credentials.
 */
public interface KycVerificationService {

    /** Human-readable label for this provider — shown in admin UI and audit log. */
    String providerLabel();

    /**
     * Initiate verification for a customer.
     * For MANUAL: just marks the record as started.
     * For DIGILOCKER: returns an OAuth redirect URL (stored in initiationResult).
     * For MOCK: marks as PENDING immediately.
     */
    KycInitiationResult initiate(KycRecord record);

    /**
     * Used by MANUAL and MOCK workflows after documents are submitted.
     * For DIGILOCKER: called on OAuth callback; completes the verification.
     */
    void onSubmit(KycRecord record, String callbackData);

    /**
     * Whether this provider requires manual staff review after document upload.
     * MANUAL=true, DIGILOCKER=false (auto-verified), MOCK=false.
     */
    boolean requiresManualReview();
}
