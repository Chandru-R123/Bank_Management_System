package com.chandru.bankmanagement.security;

/**
 * @PreAuthorize expressions shared by the controllers.
 *
 * Realm roles:
 *   ADMIN    — full access
 *   EMPLOYEE — branch staff: view everything, manage customer KYC records
 *   MAKER    — staff who submit financial transaction requests (Maker–Checker)
 *   CHECKER  — staff who approve/reject Maker requests; cannot approve own requests
 *   CUSTOMER — own accounts, beneficiaries and consents only
 *   TPP      — Third-Party Provider (fintech app) using the Open Banking APIs
 */
public final class Roles {

    /** Anyone working at the bank. */
    public static final String STAFF =
            "hasAnyRole('ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER')";

    /** Create / edit customer KYC records. */
    public static final String CUSTOMER_MANAGERS = "hasAnyRole('ADMIN', 'EMPLOYEE')";

    /**
     * Transactors — used for CUSTOMER direct operations (deposit/withdraw/transfer).
     * MAKER is intentionally NOT included here: MAKER staff must use the
     * Maker–Checker request flow (/api/transaction-requests).
     */
    public static final String CUSTOMER_TRANSACTORS = "hasAnyRole('ADMIN', 'CUSTOMER')";

    /**
     * Legacy constant — kept for AccountController staff deposit/withdraw
     * which now routes MAKER through Maker–Checker.
     */
    public static final String TRANSACTORS = "hasAnyRole('ADMIN', 'MAKER', 'CUSTOMER')";

    /** Verification actions — approve/reject Maker requests, beneficiaries, consents. */
    public static final String CHECKERS = "hasAnyRole('ADMIN', 'CHECKER')";

    /** Maker–Checker request creation. */
    public static final String MAKERS = "hasAnyRole('ADMIN', 'MAKER')";

    /** KYC review (employee + admin). */
    public static final String KYC_REVIEWERS = "hasAnyRole('ADMIN', 'EMPLOYEE')";

    public static final String ADMIN    = "hasRole('ADMIN')";
    public static final String CUSTOMER = "hasRole('CUSTOMER')";
    public static final String TPP      = "hasRole('TPP')";

    private Roles() {
    }
}
