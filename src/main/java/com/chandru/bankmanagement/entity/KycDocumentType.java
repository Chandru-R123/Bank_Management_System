package com.chandru.bankmanagement.entity;

/** Types of documents accepted for manual KYC. */
public enum KycDocumentType {
    /** Government photo ID — Aadhaar, PAN, Passport, Voter ID, Driving License. */
    IDENTITY,
    /** Proof of address — utility bill, bank statement, rental agreement. */
    ADDRESS,
    /** Recent photograph of the applicant. */
    PHOTOGRAPH,
    /** Any other supporting document requested by the reviewer. */
    OTHER
}
