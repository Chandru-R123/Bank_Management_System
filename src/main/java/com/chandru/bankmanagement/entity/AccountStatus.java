package com.chandru.bankmanagement.entity;

/**
 * Lifecycle of a bank account.
 *
 *   ACTIVE  → normal operation
 *   FROZEN  → no debits or credits allowed (e.g. under investigation)
 *   CLOSED  → permanently closed; balance has been paid out
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
