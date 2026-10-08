package com.chandru.bankmanagement.entity;

/**
 * KYC verification lifecycle for a customer.
 *
 * NOT_STARTED  — customer has not initiated KYC
 * PENDING      — customer has submitted documents; awaiting review
 * UNDER_REVIEW — employee has started reviewing (claimed the submission)
 * VERIFIED     — employee/ADMIN approved; KYC complete
 * REJECTED     — employee/ADMIN rejected with a reason; customer can resubmit
 *
 * Customer can NEVER self-set VERIFIED.
 */
public enum KycStatus {
    NOT_STARTED,
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED
}
