package com.chandru.bankmanagement.entity;

/**
 * How the KYC verification was performed.
 *
 * MANUAL      — Documents uploaded manually; reviewed by an employee or ADMIN.
 * DIGILOCKER  — Verified through official DigiLocker API (real government integration).
 *               Only set to this value after a successful API callback — never faked.
 * MOCK        — Development/testing stub; clearly labelled.
 *               MUST NOT be presented to users as real DigiLocker or government verification.
 */
public enum KycMethod {
    MANUAL,
    DIGILOCKER,
    MOCK
}
