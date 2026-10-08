package com.chandru.bankmanagement.dto;

import java.time.LocalDateTime;
import java.util.List;

public record KycRecordResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerEmail,
        String status,
        String method,
        LocalDateTime initiatedAt,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime verifiedAt,
        String reviewedBy,
        String rejectionReason,
        /** Only shown to staff, never to the customer. */
        String reviewerNotes,
        List<KycDocumentResponse> documents
) {}
