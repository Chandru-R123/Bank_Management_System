package com.chandru.bankmanagement.security;

/**
 * @PreAuthorize expressions shared by the controllers.
 *
 * Realm roles:
 *   ADMIN    — full access
 *   EMPLOYEE — branch staff: view everything, manage customer KYC records
 *   MAKER    — staff who may post transactions on any account
 *   CHECKER  — staff who approve/verify: delete beneficiaries, revoke consents
 *   CUSTOMER — own accounts, beneficiaries and consents only
 *   TPP      — Third-Party Provider (fintech app) using the Open Banking APIs
 */
public final class Roles {

    /** Anyone working at the bank. */
    public static final String STAFF =
            "hasAnyRole('ADMIN', 'EMPLOYEE', 'MAKER', 'CHECKER')";

    /** Create / edit customer KYC records. */
    public static final String CUSTOMER_MANAGERS = "hasAnyRole('ADMIN', 'EMPLOYEE')";

    /** Post money movements — staff makers on any account, customers on their own. */
    public static final String TRANSACTORS = "hasAnyRole('ADMIN', 'MAKER', 'CUSTOMER')";

    /** Verification actions. */
    public static final String CHECKERS = "hasAnyRole('ADMIN', 'CHECKER')";

    public static final String ADMIN    = "hasRole('ADMIN')";
    public static final String CUSTOMER = "hasRole('CUSTOMER')";
    public static final String TPP      = "hasRole('TPP')";

    private Roles() {
    }
}
