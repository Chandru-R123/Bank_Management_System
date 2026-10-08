package com.chandru.bankmanagement.entity;

/**
 * Canonical action codes used in AuditLog.action.
 * Using constants (not enum) keeps the column extensible without a migration.
 */
public final class AuditActions {

    // ── Customer ──────────────────────────────────────────────────────────────
    public static final String CUSTOMER_CREATED          = "CUSTOMER_CREATED";
    public static final String CUSTOMER_UPDATED          = "CUSTOMER_UPDATED";
    public static final String CUSTOMER_DELETED          = "CUSTOMER_DELETED";
    public static final String CUSTOMER_ONLINE_ENABLED   = "CUSTOMER_ONLINE_BANKING_ENABLED";
    public static final String CUSTOMER_PROFILE_UPDATED  = "CUSTOMER_PROFILE_UPDATED";

    // ── Account ───────────────────────────────────────────────────────────────
    public static final String ACCOUNT_CREATED   = "ACCOUNT_CREATED";
    public static final String ACCOUNT_UPDATED   = "ACCOUNT_UPDATED";
    public static final String ACCOUNT_FROZEN    = "ACCOUNT_FROZEN";
    public static final String ACCOUNT_UNFROZEN  = "ACCOUNT_UNFROZEN";
    public static final String ACCOUNT_CLOSED    = "ACCOUNT_CLOSED";

    // ── Transactions (customer direct) ────────────────────────────────────────
    public static final String DEPOSIT_EXECUTED   = "DEPOSIT_EXECUTED";
    public static final String WITHDRAW_EXECUTED  = "WITHDRAW_EXECUTED";
    public static final String TRANSFER_EXECUTED  = "TRANSFER_EXECUTED";

    // ── Maker–Checker ─────────────────────────────────────────────────────────
    public static final String TXN_REQUEST_CREATED  = "TXN_REQUEST_CREATED";
    public static final String TXN_REQUEST_APPROVED = "TXN_REQUEST_APPROVED";
    public static final String TXN_REQUEST_REJECTED = "TXN_REQUEST_REJECTED";
    public static final String TXN_REQUEST_CANCELLED = "TXN_REQUEST_CANCELLED";
    public static final String TXN_REQUEST_EXECUTED = "TXN_REQUEST_EXECUTED";
    public static final String TXN_REQUEST_FAILED   = "TXN_REQUEST_FAILED";
    public static final String TXN_REQUEST_REVERSED = "TXN_REQUEST_REVERSED";

    // ── Beneficiaries ─────────────────────────────────────────────────────────
    public static final String BENEFICIARY_ADDED   = "BENEFICIARY_ADDED";
    public static final String BENEFICIARY_DELETED = "BENEFICIARY_DELETED";

    // ── Consents ──────────────────────────────────────────────────────────────
    public static final String CONSENT_CREATED   = "CONSENT_CREATED";
    public static final String CONSENT_APPROVED  = "CONSENT_APPROVED";
    public static final String CONSENT_REJECTED  = "CONSENT_REJECTED";
    public static final String CONSENT_REVOKED   = "CONSENT_REVOKED";
    public static final String CONSENT_EXPIRED   = "CONSENT_EXPIRED";

    // ── KYC ───────────────────────────────────────────────────────────────────
    public static final String KYC_STARTED       = "KYC_STARTED";
    public static final String KYC_DOCUMENT_UPLOADED = "KYC_DOCUMENT_UPLOADED";
    public static final String KYC_SUBMITTED     = "KYC_SUBMITTED";
    public static final String KYC_APPROVED      = "KYC_APPROVED";
    public static final String KYC_REJECTED      = "KYC_REJECTED";
    public static final String KYC_RESUBMITTED   = "KYC_RESUBMITTED";

    // ── Security / Admin ──────────────────────────────────────────────────────
    public static final String STAFF_CREATED     = "STAFF_CREATED";
    public static final String STAFF_ROLES_UPDATED = "STAFF_ROLES_UPDATED";
    public static final String STAFF_ENABLED     = "STAFF_ENABLED";
    public static final String STAFF_DISABLED    = "STAFF_DISABLED";
    public static final String PASSWORD_RESET_SENT = "PASSWORD_RESET_SENT";

    private AuditActions() {}
}
