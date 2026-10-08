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
     * Direct money movement (deposit / withdraw / transfer): ADMIN on any
     * account, CUSTOMER on their own. MAKER is intentionally NOT included —
     * makers only initiate requests through /api/transaction-requests, which
     * a CHECKER must approve.
     *
     * A staff role always wins over CUSTOMER, so an EMPLOYEE / MAKER / CHECKER
     * login that also carries CUSTOMER (e.g. from the realm's default roles)
     * still cannot move money directly.
     */
    public static final String CUSTOMER_TRANSACTORS =
            "hasRole('ADMIN') or (hasRole('CUSTOMER') and !hasAnyRole('EMPLOYEE', 'MAKER', 'CHECKER'))";

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
