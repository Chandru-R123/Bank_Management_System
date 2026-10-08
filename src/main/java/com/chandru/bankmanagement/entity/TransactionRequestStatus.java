package com.chandru.bankmanagement.entity;

/**
 * Lifecycle of a Maker–Checker transaction request.
 *
 * PENDING_APPROVAL → APPROVED  → PROCESSING → SUCCESS
 *                              ↘ FAILED
 * PENDING_APPROVAL → REJECTED
 * PENDING_APPROVAL → CANCELLED
 * SUCCESS          → REVERSED
 */
public enum TransactionRequestStatus {

    /** Created by MAKER; awaiting CHECKER action. */
    PENDING_APPROVAL,

    /** CHECKER approved; ready to execute. */
    APPROVED,

    /** Execution is in-flight (concurrency guard). */
    PROCESSING,

    /** Executed successfully; balances changed. */
    SUCCESS,

    /** CHECKER rejected; request is dead. */
    REJECTED,

    /** Execution failed after approval (e.g. insufficient balance at execution time). */
    FAILED,

    /** Cancelled by the MAKER before a CHECKER acted. */
    CANCELLED,

    /** Successfully executed request that was later reversed by an ADMIN. */
    REVERSED;

    /** True when no further state transitions are possible. */
    public boolean isFinal() {
        return this == SUCCESS || this == REJECTED || this == FAILED
                || this == CANCELLED || this == REVERSED;
    }
}
