package com.chandru.bankmanagement.dto;

import java.time.LocalDateTime;

/**
 * Document metadata returned to staff reviewers.
 * The storageReference is NEVER included — use a separate secure download endpoint.
 */
public record KycDocumentResponse(
        Long id,
        String documentType,
        String fileName,
        String contentType,
        Long fileSize,
        String status,
        LocalDateTime uploadedAt
) {}
