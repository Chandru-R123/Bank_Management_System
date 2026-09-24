package com.chandru.bankmanagement.entity;

/**
 * Open Banking consent lifecycle.
 *
 *   AWAITING_AUTHORISATION → AUTHORISED → REVOKED
 *                          ↘ REJECTED
 *   Any non-final consent becomes EXPIRED once expiresAt has passed.
 */
public enum ConsentStatus {
    AWAITING_AUTHORISATION,
    AUTHORISED,
    REJECTED,
    REVOKED,
    EXPIRED
}
