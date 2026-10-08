package com.chandru.bankmanagement.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Staff payload for POST /api/kyc/{customerId}/approve or /reject.
 */
public record KycReviewRequest(
        /** Required when rejecting; optional when approving. */
        String rejectionReason,
        /** Internal notes, never shown to the customer. */
        String reviewerNotes
) {}
