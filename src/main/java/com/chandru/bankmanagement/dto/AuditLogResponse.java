package com.chandru.bankmanagement.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String action,
        String actorUserId,
        String actorUsername,
        String actorRole,
        String resourceType,
        String resourceId,
        String status,
        String remarks,
        LocalDateTime timestamp,
        String requestId,
        String httpRequestId
) {}
