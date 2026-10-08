package com.chandru.bankmanagement.dto;

/**
 * Body for CHECKER approve/reject actions.
 * Rejection reason is required when rejecting; ignored on approve.
 */
public record CheckerActionDto(
        String rejectionReason,
        String remarks
) {}
