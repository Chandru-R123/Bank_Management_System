package com.chandru.bankmanagement.service.kyc;

import com.chandru.bankmanagement.entity.KycMethod;
import com.chandru.bankmanagement.entity.KycRecord;
import com.chandru.bankmanagement.entity.KycStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Manual KYC — customer uploads documents; an employee or ADMIN reviews them.
 *
 * Active when: KYC_PROVIDER=MANUAL (or when KYC_PROVIDER is not set).
 *
 * Flow:
 *   1. Customer calls POST /api/kyc/start → record created (NOT_STARTED → PENDING)
 *   2. Customer uploads documents via POST /api/kyc/documents
 *   3. Customer calls POST /api/kyc/submit → status → PENDING (awaiting staff review)
 *   4. Employee/ADMIN reviews via GET /api/kyc/pending, then approves or rejects
 *   5. Approve → VERIFIED; Reject → REJECTED + reason; customer can resubmit
 */
@Service
@ConditionalOnProperty(name = "kyc.provider", havingValue = "MANUAL", matchIfMissing = true)
public class ManualKycVerificationService implements KycVerificationService {

    @Override
    public String providerLabel() {
        return "Manual KYC";
    }

    @Override
    public KycInitiationResult initiate(KycRecord record) {
        record.setMethod(KycMethod.MANUAL);
        record.setStatus(KycStatus.PENDING);
        return new KycInitiationResult(
                null,
                "Please upload your identity document, address proof, and a recent photograph. "
                + "Once submitted, a bank employee will review your documents.",
                null);
    }

    @Override
    public void onSubmit(KycRecord record, String callbackData) {
        // For MANUAL, onSubmit transitions to PENDING (awaiting staff review).
        record.setStatus(KycStatus.PENDING);
    }

    @Override
    public boolean requiresManualReview() {
        return true;
    }
}
