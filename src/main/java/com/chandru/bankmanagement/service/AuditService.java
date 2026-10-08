package com.chandru.bankmanagement.service;

import com.chandru.bankmanagement.dto.AuditLogResponse;
import com.chandru.bankmanagement.entity.AuditLog;
import com.chandru.bankmanagement.repository.AuditLogRepository;
import com.chandru.bankmanagement.security.Actor;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralised audit logging service.
 *
 * All writes are:
 *   - Async: audit failures MUST NOT break the business operation.
 *   - In a NEW transaction: audit records survive even if the caller rolls back.
 *   - Idempotent: calling multiple times does not corrupt the log.
 *
 * Fields that are NEVER written to the audit log:
 *   passwords, OTPs, access tokens, refresh tokens, CVV,
 *   client secrets, DigiLocker secrets, document file contents.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Async + separate transaction so audit never blocks the caller and
     * audit records survive a business transaction rollback.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action,
                    Actor actor,
                    String resourceType,
                    String resourceId,
                    String status,
                    String remarks) {
        log(action, actor, resourceType, resourceId, status, remarks, null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action,
                    Actor actor,
                    String resourceType,
                    String resourceId,
                    String status,
                    String remarks,
                    String makerCheckerRequestId) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setActorUserId(actor.sub());
            entry.setActorUsername(actor.username());
            entry.setActorRole(resolveHighestRole(actor));
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            entry.setStatus(status);
            entry.setRemarks(truncate(remarks, 500));
            entry.setTimestamp(LocalDateTime.now());
            entry.setRequestId(makerCheckerRequestId);
            entry.setHttpRequestId(MDC.get("requestId")); // from RequestIdFilter
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit must never break the primary flow
            org.slf4j.LoggerFactory.getLogger(AuditService.class)
                    .error("Failed to write audit log for action={}: {}", action, e.getMessage());
        }
    }

    /** Synchronous variant for contexts where @Async cannot be used (e.g. tests). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSync(String action,
                        Actor actor,
                        String resourceType,
                        String resourceId,
                        String status,
                        String remarks) {
        try {
            AuditLog entry = AuditLog.of(action, actor.sub(), actor.username(),
                    resolveHighestRole(actor), resourceType, resourceId, status, truncate(remarks, 500));
            entry.setHttpRequestId(MDC.get("requestId"));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditService.class)
                    .error("Failed to write audit log for action={}: {}", action, e.getMessage());
        }
    }

    /** System-initiated events (no human actor). */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystem(String action,
                          String resourceType,
                          String resourceId,
                          String status,
                          String remarks) {
        try {
            AuditLog entry = AuditLog.of(action, "system", "system", "SYSTEM",
                    resourceType, resourceId, status, truncate(remarks, 500));
            entry.setHttpRequestId(MDC.get("requestId"));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditService.class)
                    .error("Failed to write system audit log for action={}: {}", action, e.getMessage());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAll(int page, int size) {
        return auditLogRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getByActor(String actorUserId) {
        return auditLogRepository.findByActorUserIdOrderByTimestampDesc(actorUserId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getByResource(String resourceType, String resourceId) {
        return auditLogRepository
                .findByResourceTypeAndResourceIdOrderByTimestampDesc(resourceType, resourceId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecent(int limit) {
        return auditLogRepository
                .findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp")))
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveHighestRole(Actor actor) {
        if (actor.admin())   return "ADMIN";
        if (actor.checker()) return "CHECKER";
        if (actor.maker())   return "MAKER";
        if (actor.staff())   return "EMPLOYEE";
        if (actor.tpp())     return "TPP";
        return "CUSTOMER";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(
                a.getId(), a.getAction(), a.getActorUserId(), a.getActorUsername(),
                a.getActorRole(), a.getResourceType(), a.getResourceId(),
                a.getStatus(), a.getRemarks(), a.getTimestamp(),
                a.getRequestId(), a.getHttpRequestId());
    }
}
