package com.chandru.bankmanagement.service.kyc;

import com.chandru.bankmanagement.entity.KycMethod;
import com.chandru.bankmanagement.entity.KycRecord;
import com.chandru.bankmanagement.entity.KycStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Mock KYC verification — for DEVELOPMENT AND TESTING ONLY.
 *
 * Active when: KYC_PROVIDER=MOCK
 *
 * IMPORTANT:
 *   - This MUST NOT be used in production.
 *   - This MUST NOT be presented to users as real DigiLocker or government verification.
 *   - Records verified by this service are clearly marked KycMethod=MOCK.
 *   - The providerLabel() returns "[DEV] Mock Verification" — never "DigiLocker Verified".
 *
 * Behaviour: auto-verifies on initiation (no documents needed, no staff review).
 * Useful for running integration tests and seeding demo data.
 */
@Service
@ConditionalOnProperty(name = "kyc.provider", havingValue = "MOCK")
public class MockKycVerificationService implements KycVerificationService {

    private static final Logger log = LoggerFactory.getLogger(MockKycVerificationService.class);

    @Override
    public String providerLabel() {
        // Clearly distinguished from real DigiLocker — MUST NOT be changed to "DigiLocker Verified"
        return "[DEV] Mock Verification";
    }

    @Override
    public KycInitiationResult initiate(KycRecord record) {
        log.warn("[MOCK KYC] Auto-verifying customer id={} — DEVELOPMENT ONLY, not for production",
                record.getCustomer().getCustomerId());

        record.setMethod(KycMethod.MOCK);
        record.setStatus(KycStatus.VERIFIED);
        record.setVerifiedAt(LocalDateTime.now());

        return new KycInitiationResult(
                null,
                "[DEV] Mock KYC: automatically verified. This is a development stub only.",
                "MOCK-" + record.getCustomer().getCustomerId());
    }

    @Override
    public void onSubmit(KycRecord record, String callbackData) {
        // Mock: nothing to do — already verified in initiate()
        log.debug("[MOCK KYC] onSubmit called (no-op) for customer id={}",
                record.getCustomer().getCustomerId());
    }

    @Override
    public boolean requiresManualReview() {
        return false;
    }
}
